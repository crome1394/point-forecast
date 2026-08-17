package com.crome.forecastpoint.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tornado + hurricane / tropical cyclone summary from official NOAA sources:
 *
 * 1. **NHC** [CurrentStorms.json](https://www.nhc.noaa.gov/CurrentStorms.json) — active tropical cyclones
 * 2. **NWS API** `/alerts/active?point=` — tornado/tropical watches & warnings for the city
 * 3. **SPC** climo storm reports CSV — recent tornado reports (last ~7 days)
 *
 * Validated against live NHC (active storm list), api.weather.gov alerts, and SPC daily CSVs.
 */
class SevereWeatherService(
    private val client: OkHttpClient = defaultClient(),
) {
    data class TropicalStorm(
        val id: String,
        val name: String,
        val classification: String,
        val intensityKt: Int?,
        val pressureMb: Int?,
        val latitude: Double,
        val longitude: Double,
        val movementDir: Int?,
        val movementSpeedKt: Int?,
        val lastUpdate: String?,
        val distanceMiles: Double,
        val advisoryUrl: String?,
    )

    data class TornadoReport(
        val id: String,
        val timeLabel: String,
        val epochMs: Long,
        val fScale: String,
        val location: String,
        val county: String,
        val state: String,
        val latitude: Double,
        val longitude: Double,
        val comments: String,
        val distanceMiles: Double,
        /** SPC daily storm-reports page for this date (HTML details). */
        val detailUrl: String? = null,
        /**
         * True when [location] is start coordinates from WCM (no city in the source file).
         * UI may reverse-geocode these to a nearby place name.
         */
        val locationIsCoordinate: Boolean = false,
    )

    data class ActiveAlert(
        val id: String,
        val event: String,
        val headline: String,
        val severity: String,
        val areaDesc: String,
        val onset: String?,
        val ends: String?,
        val instruction: String?,
    )

    data class Snapshot(
        val latitude: Double,
        val longitude: Double,
        val tropicalStorms: List<TropicalStorm>,
        val tornadoReports: List<TornadoReport>,
        val localAlerts: List<ActiveAlert>,
        /** Look-back window (days) for preset mode; custom uses start/end. */
        val historyDays: Int = 7,
        val historyStartMs: Long = 0L,
        val historyEndMs: Long = 0L,
        val updatedAtEpochMs: Long,
        val error: String? = null,
        val querySummary: String = "",
    )

    /**
     * @param focusRadiusMiles map focus / explore radius; limits active tropical
     *   cyclones, SPC tornado reports, and map context from the selected city.
     * @param historyDays look-back when [historyStartMs]/[historyEndMs] are null.
     * @param historyStartMs optional custom range start.
     * @param historyEndMs optional custom range end.
     */
    suspend fun fetchAround(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int = 250,
        historyDays: Int = 7,
        historyStartMs: Long? = null,
        historyEndMs: Long? = null,
    ): Snapshot = withContext(Dispatchers.IO) {
        val focusMiles = focusRadiusMiles.toDouble().coerceIn(25.0, 4000.0)
        val endMs = historyEndMs ?: System.currentTimeMillis()
        val startMs = historyStartMs
            ?: (endMs - historyDays.coerceIn(1, MAX_HISTORY_DAYS).toLong() * 24L * 3600L * 1000L)
        val days = (
            ((endMs - startMs).coerceAtLeast(0L) / (24L * 3600L * 1000L)).toInt()
            ).coerceAtLeast(1)
        coroutineScope {
            val stormsDef = async {
                runCatching { fetchTropicalStorms(latitude, longitude) }
                    .getOrElse { emptyList() }
            }
            val reportsDef = async {
                runCatching {
                    fetchSevereWeatherReports(
                        latitude,
                        longitude,
                        maxMiles = focusMiles,
                        startMs = startMs,
                        endMs = endMs,
                    )
                }.getOrElse { emptyList() }
            }
            val alertsDef = async {
                runCatching { fetchLocalSevereAlerts(latitude, longitude) }
                    .getOrElse { emptyList() }
            }
            val allStorms = stormsDef.await()
            // Same radius as map zoom / tornado history
            val storms = allStorms.filter { it.distanceMiles <= focusMiles }
            val reports = reportsDef.await()
            val alerts = alertsDef.await()
            val oldest = reports.minOfOrNull { it.epochMs }
            val newest = reports.maxOfOrNull { it.epochMs }
            val span = if (oldest != null && newest != null) {
                " · span ${formatEpochDateUtc(oldest)} → ${formatEpochDateUtc(newest)}"
            } else {
                ""
            }
            Snapshot(
                latitude = latitude,
                longitude = longitude,
                tropicalStorms = storms,
                tornadoReports = reports,
                localAlerts = alerts,
                historyDays = days,
                historyStartMs = startMs,
                historyEndMs = endMs,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = null,
                querySummary =
                    "NHC tropical within ${focusRadiusMiles} mi: ${storms.size}" +
                        (if (allStorms.size != storms.size) {
                            " (${allStorms.size} active worldwide)"
                        } else {
                            ""
                        }) +
                        " · SPC reports (${formatEpochDateUtc(startMs)}→${formatEpochDateUtc(endMs)}, " +
                        "≤${focusRadiusMiles} mi): ${reports.size}$span · " +
                        "NWS local tornado/tropical alerts: ${alerts.size}",
            )
        }
    }

    private fun formatEpochDateUtc(epochMs: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMs
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun fetchTropicalStorms(refLat: Double, refLon: Double): List<TropicalStorm> {
        val body = httpGet("https://www.nhc.noaa.gov/CurrentStorms.json")
            ?: return emptyList()
        val root = JSONObject(body)
        val arr = root.optJSONArray("activeStorms") ?: return emptyList()
        val out = ArrayList<TropicalStorm>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val lat = s.optDouble("latitudeNumeric", Double.NaN)
            val lon = s.optDouble("longitudeNumeric", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val id = s.optString("id").ifBlank { "storm_$i" }
            val name = s.optString("name").ifBlank { id }
            val classification = s.optString("classification").ifBlank { "—" }
            val intensity = s.optString("intensity").toIntOrNull()
                ?: s.optInt("intensity", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val pressure = s.optString("pressure").toIntOrNull()
                ?: s.optInt("pressure", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val movDir = s.optString("movementDir").toIntOrNull()
                ?: s.optInt("movementDir", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val movSpd = s.optString("movementSpeed").toIntOrNull()
                ?: s.optInt("movementSpeed", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val advisory = s.optJSONObject("publicAdvisory")?.optString("url")
                ?.takeIf { it.startsWith("http") }
                ?: "https://www.nhc.noaa.gov/text/refresh/MIATCPEP${s.optString("binNumber").filter { it.isDigit() }}.shtml"
            // Prefer graphics page as stable entry
            val graphics = s.optJSONObject("forecastGraphics")?.optString("url")
                ?.takeIf { it.startsWith("http") }
            out += TropicalStorm(
                id = id,
                name = name,
                classification = classification,
                intensityKt = intensity,
                pressureMb = pressure,
                latitude = lat,
                longitude = lon,
                movementDir = movDir,
                movementSpeedKt = movSpd,
                lastUpdate = s.optString("lastUpdate").takeIf { it.isNotBlank() },
                distanceMiles = haversineMiles(refLat, refLon, lat, lon),
                advisoryUrl = graphics ?: advisory.takeIf { it.startsWith("http") },
            )
        }
        return out.sortedBy { it.distanceMiles }
    }

    /**
     * Severe-weather (tornado) reports near the city for [startMs]..[endMs].
     *
     * - **Recent window only** (entire range within the last ~[DAILY_REPORT_MAX_DAYS] days):
     *   SPC daily preliminary storm-report CSVs (those files do not exist for historical years).
     * - **Anything older** (custom ranges like July 1992, or multi-year presets):
     *   SPC WCM official yearly files + 1950–present archive.
     *
     * Previously we chose daily CSVs whenever the *span* was ≤ 30 days, which broke short
     * custom ranges far in the past (e.g. a single day in 1992 returned zero reports).
     */
    private fun fetchSevereWeatherReports(
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        startMs: Long,
        endMs: Long,
    ): List<TornadoReport> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 3600L * 1000L
        val oldestDailyStart = now - DAILY_REPORT_MAX_DAYS.toLong() * dayMs
        // Daily CSVs only cover roughly the last month — not historical custom ranges.
        val entireRangeIsRecent = startMs >= oldestDailyStart && endMs >= oldestDailyStart
        return if (entireRangeIsRecent) {
            fetchDailyTornadoReports(refLat, refLon, maxMiles, startMs, endMs)
        } else {
            fetchWcmTornadoReports(refLat, refLon, maxMiles, startMs, endMs)
        }
    }

    private fun fetchDailyTornadoReports(
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        startMs: Long,
        endMs: Long,
    ): List<TornadoReport> {
        val out = ArrayList<TornadoReport>()
        val ymdFmt = SimpleDateFormat("yyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = endMs
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while (!day.before(startDay) && guard < DAILY_REPORT_MAX_DAYS + 2) {
            guard++
            val ymd = ymdFmt.format(day.time)
            // Prefer filtered reports; fall back to unfiltered
            val urls = listOf(
                "https://www.spc.noaa.gov/climo/reports/${ymd}_rpts_filtered_torn.csv",
                "https://www.spc.noaa.gov/climo/reports/${ymd}_rpts_torn.csv",
            )
            var body: String? = null
            for (u in urls) {
                body = httpGet(u)
                if (!body.isNullOrBlank() && !body.contains("404", ignoreCase = true)) break
            }
            // "today.csv" style alternate for the end day
            if (body.isNullOrBlank() && guard == 1) {
                body = httpGet("https://www.spc.noaa.gov/climo/reports/today.csv")
            }
            if (!body.isNullOrBlank()) {
                parseTornCsv(body, day.clone() as Calendar, ymd, refLat, refLon, maxMiles, out)
            }
            day.add(Calendar.DAY_OF_YEAR, -1)
        }
        return selectTimeSpanningReports(
            out.filter { it.epochMs in startMs..endMs },
            UI_MAX_REPORTS,
        ).sortedByDescending { it.epochMs }
    }

    /**
     * Official SPC WCM tornado data for multi-year look-backs.
     *
     * - Yearly files (`YYYY_torn.csv`) exist from **2008** onward.
     * - Pre-2008 (and any missing yearly file) uses the multi-decade
     *   `1950-YYYY_actual_tornadoes.csv` archive so long custom ranges are not
     *   truncated at 2008.
     */
    private fun fetchWcmTornadoReports(
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        startMs: Long,
        endMs: Long,
    ): List<TornadoReport> {
        val start = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = startMs
        }
        val end = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = endMs
        }
        val startYear = start.get(Calendar.YEAR)
        val endYear = end.get(Calendar.YEAR)
        val out = ArrayList<TornadoReport>()
        val ymdFmt = SimpleDateFormat("yyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Years covered by per-year files (2008+)
        val yearlyFrom = maxOf(startYear, WCM_YEARLY_START)
        for (year in yearlyFrom..endYear) {
            val body = httpGet("https://www.spc.noaa.gov/wcm/data/${year}_torn.csv")
                ?: continue
            parseWcmTornCsv(body, startMs, endMs, refLat, refLon, maxMiles, ymdFmt, out)
        }

        // Pre-2008 (or any gap before yearly files): multi-decade archive
        if (startYear < WCM_YEARLY_START) {
            val archive = fetchWcmHistoricalArchive()
            if (archive != null) {
                // Prefer yearly for 2008+; archive only fills older years when we have yearly data
                val archiveEndMs = if (out.isNotEmpty()) {
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        set(Calendar.YEAR, WCM_YEARLY_START)
                        set(Calendar.MONTH, Calendar.JANUARY)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis - 1L
                } else {
                    endMs
                }
                parseWcmTornCsv(
                    archive,
                    startMs,
                    minOf(endMs, archiveEndMs),
                    refLat,
                    refLon,
                    maxMiles,
                    ymdFmt,
                    out,
                )
            }
        }

        return selectTimeSpanningReports(out, UI_MAX_REPORTS)
            .sortedByDescending { it.epochMs }
    }

    /**
     * Download SPC WCM multi-decade tornado archive (1950–present).
     * Tries current and previous calendar-year filenames.
     */
    private fun fetchWcmHistoricalArchive(): String? {
        val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR)
        for (y in year downTo year - 2) {
            val body = httpGet("https://www.spc.noaa.gov/wcm/data/1950-${y}_actual_tornadoes.csv")
            if (!body.isNullOrBlank() && body.lineSequence().take(2).count() >= 2) {
                return body
            }
        }
        // Fallback naming used in some years
        for (y in year downTo year - 2) {
            val body = httpGet("https://www.spc.noaa.gov/wcm/data/1950-${y}_all_tornadoes.csv")
            if (!body.isNullOrBlank()) return body
        }
        return null
    }

    /**
     * Keep strong events + evenly sample across the time range so long history
     * is not dominated by the last few months of weak reports.
     */
    private fun selectTimeSpanningReports(
        events: List<TornadoReport>,
        maxCount: Int,
    ): List<TornadoReport> {
        if (events.size <= maxCount) return events
        val byTime = events.sortedBy { it.epochMs }
        val selected = LinkedHashMap<String, TornadoReport>()
        // Strongest EF ratings first
        events.sortedWith(
            compareByDescending<TornadoReport> { parseEfForSort(it.fScale) }
                .thenByDescending { it.epochMs },
        ).take((maxCount * 0.3).toInt().coerceAtLeast(12))
            .forEach { selected[it.id] = it }
        // Even time samples across the full window
        val slots = (maxCount - selected.size).coerceAtLeast(1)
        if (byTime.size >= 2) {
            for (i in 0 until slots) {
                val idx = (
                    i.toDouble() / (slots - 1).coerceAtLeast(1) * (byTime.lastIndex)
                    ).toInt()
                val r = byTime[idx.coerceIn(0, byTime.lastIndex)]
                selected[r.id] = r
                if (selected.size >= maxCount) break
            }
        }
        // Nearest fill
        if (selected.size < maxCount) {
            events.sortedBy { it.distanceMiles }.forEach { r ->
                if (selected.size >= maxCount) return@forEach
                selected.putIfAbsent(r.id, r)
            }
        }
        return selected.values.toList()
    }

    private fun parseEfForSort(raw: String): Int {
        val u = raw.uppercase(Locale.US)
        if (u.contains("UNK") || u.isBlank()) return -1
        return Regex("([0-5])").find(u)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun parseTornCsv(
        body: String,
        dayUtc: Calendar,
        ymd: String,
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        out: MutableList<TornadoReport>,
    ) {
        val lines = body.lineSequence().toList()
        if (lines.isEmpty()) return
        val header = lines.first().lowercase(Locale.US)
        // SPC tornado CSV: Time,F_Scale,Location,County,State,Lat,Lon,Comments
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            if (line.startsWith("Time", ignoreCase = true)) continue
            if (line.contains("Hail Reports", ignoreCase = true)) break
            if (line.contains("Wind Reports", ignoreCase = true)) break
            val p = parseCsvLine(line)
            if (p.size < 7) continue
            val timeStr: String
            val fScale: String
            val location: String
            val county: String
            val state: String
            val lat: Double
            val lon: Double
            val comments: String
            if (header.contains("f_scale") || header.contains("f-scale") || p.size >= 8) {
                timeStr = p[0].trim()
                fScale = p[1].trim().ifBlank { "—" }
                location = p[2].trim()
                county = p.getOrNull(3)?.trim().orEmpty()
                state = p.getOrNull(4)?.trim().orEmpty()
                lat = p.getOrNull(5)?.toDoubleOrNull() ?: continue
                lon = p.getOrNull(6)?.toDoubleOrNull() ?: continue
                comments = p.getOrNull(7)?.trim().orEmpty()
            } else {
                continue
            }
            val dist = haversineMiles(refLat, refLon, lat, lon)
            if (dist > maxMiles) continue
            val epoch = parseSpcTime(dayUtc, timeStr)
            out += TornadoReport(
                id = "${dayUtc.timeInMillis}_${timeStr}_${lat}_$lon",
                timeLabel = timeStr,
                epochMs = epoch,
                fScale = fScale,
                location = location,
                county = county,
                state = state,
                latitude = lat,
                longitude = lon,
                comments = comments,
                distanceMiles = dist,
                detailUrl = spcDailyReportUrl(ymd),
            )
        }
    }

    /**
     * WCM yearly tornado CSV columns:
     * om,yr,mo,dy,date,time,tz,st,stf,stn,mag,inj,fat,loss,closs,slat,slon,...
     */
    private fun parseWcmTornCsv(
        body: String,
        startMs: Long,
        endMs: Long,
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        ymdFmt: SimpleDateFormat,
        out: MutableList<TornadoReport>,
    ) {
        for (line in body.lineSequence().drop(1)) {
            if (line.isBlank()) continue
            val p = parseCsvLine(line)
            if (p.size < 17) continue
            val yr = p.getOrNull(1)?.toIntOrNull() ?: continue
            val mo = p.getOrNull(2)?.toIntOrNull() ?: continue
            val dy = p.getOrNull(3)?.toIntOrNull() ?: continue
            val dateStr = p.getOrNull(4)?.trim().orEmpty()
            val timeStr = p.getOrNull(5)?.trim().orEmpty()
            val state = p.getOrNull(7)?.trim().orEmpty()
            val mag = p.getOrNull(10)?.toIntOrNull()
            val lat = p.getOrNull(15)?.toDoubleOrNull() ?: continue
            val lon = p.getOrNull(16)?.toDoubleOrNull() ?: continue
            val dist = haversineMiles(refLat, refLon, lat, lon)
            if (dist > maxMiles) continue
            val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.YEAR, yr)
                set(Calendar.MONTH, (mo - 1).coerceIn(0, 11))
                set(Calendar.DAY_OF_MONTH, dy.coerceIn(1, 31))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // time is HH:MM:SS
            val parts = timeStr.split(':')
            if (parts.size >= 2) {
                day.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull()?.coerceIn(0, 23) ?: 0)
                day.set(Calendar.MINUTE, parts[1].toIntOrNull()?.coerceIn(0, 59) ?: 0)
            }
            val epoch = day.timeInMillis
            if (epoch < startMs || epoch > endMs) continue
            val fScale = when {
                mag == null || mag < 0 -> "UNK"
                else -> "EF$mag"
            }
            val ymd = ymdFmt.format(day.time)
            val hhmm = timeStr.take(5).replace(":", "")
            // WCM rows have start lat/lon, not city names — show coordinates + state.
            val place = buildString {
                append(String.format(Locale.US, "%.2f°N", lat))
                append(", ")
                // Western hemisphere longitudes are negative in the file
                val lonAbs = kotlin.math.abs(lon)
                append(String.format(Locale.US, "%.2f°%s", lonAbs, if (lon <= 0) "W" else "E"))
                if (state.isNotBlank()) append(" · $state")
            }
            val fips = p.getOrNull(24)?.trim().orEmpty() // f1 county FIPS (often present)
            out += TornadoReport(
                id = "wcm_${yr}_${mo}_${dy}_${lat}_$lon",
                timeLabel = if (hhmm.length >= 3) hhmm else timeStr,
                epochMs = epoch,
                fScale = fScale,
                location = place,
                county = if (fips.isNotBlank() && fips != "0") "County FIPS $fips" else "",
                state = state,
                latitude = lat,
                longitude = lon,
                comments = buildString {
                    append("Official SPC WCM tornado record")
                    if (dateStr.isNotBlank()) append(" · $dateStr")
                    append(" · tap for daily storm reports page")
                },
                distanceMiles = dist,
                detailUrl = spcDailyReportUrl(ymd),
                locationIsCoordinate = true,
            )
        }
    }

    private fun spcDailyReportUrl(yyMMdd: String): String =
        "https://www.spc.noaa.gov/climo/reports/${yyMMdd}_rpts.html"

    private fun parseSpcTime(dayUtc: Calendar, hhmm: String): Long {
        val digits = hhmm.filter { it.isDigit() }
        if (digits.length < 3) return dayUtc.timeInMillis
        val padded = digits.padStart(4, '0')
        val h = padded.substring(0, 2).toIntOrNull() ?: 0
        val m = padded.substring(2, 4).toIntOrNull() ?: 0
        val c = dayUtc.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, h.coerceIn(0, 23))
        c.set(Calendar.MINUTE, m.coerceIn(0, 59))
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }

    private fun fetchLocalSevereAlerts(latitude: Double, longitude: Double): List<ActiveAlert> {
        val url =
            "https://api.weather.gov/alerts/active?point=$latitude,$longitude&status=actual"
        val body = httpGet(url) ?: return emptyList()
        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: return emptyList()
        val keywords = listOf(
            "tornado", "hurricane", "tropical storm", "tropical depression",
            "storm surge", "typhoon", "cyclone",
        )
        val out = ArrayList<ActiveAlert>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val props = f.optJSONObject("properties") ?: continue
            val event = props.optString("event")
            val evLow = event.lowercase(Locale.US)
            if (keywords.none { evLow.contains(it) }) continue
            out += ActiveAlert(
                id = f.optString("id").ifBlank { props.optString("id") },
                event = event,
                headline = props.optString("headline").ifBlank { event },
                severity = props.optString("severity").ifBlank { "—" },
                areaDesc = props.optString("areaDesc"),
                onset = props.optString("onset").takeIf { it.isNotBlank() },
                ends = props.optString("ends").ifBlank { props.optString("expires") }
                    .takeIf { it.isNotBlank() },
                instruction = props.optString("instruction").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/csv,text/plain,*/*")
            .get()
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Daily SPC CSVs are practical up to this many days; longer uses WCM yearly files. */
        private const val DAILY_REPORT_MAX_DAYS = 30
        /**
         * First year of SPC per-year `YYYY_torn.csv` files on the WCM site.
         * Earlier years require the multi-decade archive.
         */
        private const val WCM_YEARLY_START = 2008
        /** Cap UI/list size after time-spanning selection (WCM can return 1000+ near OKC). */
        private const val UI_MAX_REPORTS = 120
        /** Practical max look-back (SPC WCM tornado archive: 1950–present; 20y is well covered). */
        const val MAX_HISTORY_DAYS = 7300 // 20 years
        private const val USER_AGENT =
            "PointForecast/1.1.6 (Android; open-source; https://github.com/crome1394/point-forecast)"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                // Multi-decade WCM archive is ~9 MB; allow a longer read
                .readTimeout(90, TimeUnit.SECONDS)
                .build()

        fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 3958.8
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).let { it * it }
            val c = 2 * asin(min(1.0, sqrt(a)))
            return r * c
        }
    }
}
