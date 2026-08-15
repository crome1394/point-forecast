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
        val updatedAtEpochMs: Long,
        val error: String? = null,
        val querySummary: String = "",
    )

    /**
     * @param focusRadiusMiles user map-focus setting (Settings → Map); limits
     *   active tropical cyclones, SPC tornado reports, and map context to that
     *   distance from the selected city.
     */
    suspend fun fetchAround(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int = 250,
    ): Snapshot = withContext(Dispatchers.IO) {
        val focusMiles = focusRadiusMiles.toDouble().coerceIn(25.0, 4000.0)
        coroutineScope {
            val stormsDef = async {
                runCatching { fetchTropicalStorms(latitude, longitude) }
                    .getOrElse { emptyList() }
            }
            val reportsDef = async {
                runCatching {
                    fetchTornadoReports(
                        latitude,
                        longitude,
                        maxMiles = focusMiles,
                        daysBack = 7,
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
            Snapshot(
                latitude = latitude,
                longitude = longitude,
                tropicalStorms = storms,
                tornadoReports = reports,
                localAlerts = alerts,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = null,
                querySummary =
                    "NHC tropical within ${focusRadiusMiles} mi: ${storms.size}" +
                        (if (allStorms.size != storms.size) {
                            " (${allStorms.size} active worldwide)"
                        } else {
                            ""
                        }) +
                        " · SPC tornado reports (7d, ≤${focusRadiusMiles} mi): ${reports.size} · " +
                        "NWS local tornado/tropical alerts: ${alerts.size}",
            )
        }
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

    private fun fetchTornadoReports(
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        daysBack: Int,
    ): List<TornadoReport> {
        val out = ArrayList<TornadoReport>()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val ymdFmt = SimpleDateFormat("yyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (d in 0 until daysBack) {
            val day = cal.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, -d)
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
            // "today.csv" style alternate
            if (body.isNullOrBlank() && d == 0) {
                body = httpGet("https://www.spc.noaa.gov/climo/reports/today.csv")
                // today.csv mixes types — parse only tornado-ish lines if f_scale present
            }
            if (body.isNullOrBlank()) continue
            parseTornCsv(body, day, refLat, refLon, maxMiles, out)
        }
        return out.sortedBy { it.distanceMiles }.take(40)
    }

    private fun parseTornCsv(
        body: String,
        dayUtc: Calendar,
        refLat: Double,
        refLon: Double,
        maxMiles: Double,
        out: MutableList<TornadoReport>,
    ) {
        val lines = body.lineSequence().toList()
        if (lines.isEmpty()) return
        val header = lines.first().lowercase(Locale.US)
        // SPC tornado CSV: Time,F_Scale,Location,County,State,Lat,Lon,Comments
        // today.csv may start the same for tornado section after "Tornado Reports" markers
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            if (line.startsWith("Time", ignoreCase = true)) continue
            if (line.contains("Hail Reports", ignoreCase = true)) break
            if (line.contains("Wind Reports", ignoreCase = true)) break
            val p = parseCsvLine(line)
            if (p.size < 7) continue
            // Detect columns
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
            )
        }
    }

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
        private const val USER_AGENT =
            "PointForecast/1.0 (Android; open-source; https://github.com/crome1394/forecast-point)"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
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
