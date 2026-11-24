package com.crome.forecastpoint.data

import com.crome.forecastpoint.util.HttpUserAgent
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
 * Water height time series for swimmers / open-water athletes:
 *
 * 1. **NOAA CO-OPS tide predictions** (coastal / estuarine) — MLLW feet, harmonic forecast
 * 2. **NOAA CO-OPS water levels** (Great Lakes & some coastal) — LWD via OFS forecast
 * 3. **USGS Water Data gage height** (rivers, lakes, inland) — feet, observed stage + rise/fall
 *
 * Station choice prefers (1) then (2) then (3) when each is within its max radius.
 */
class TideService(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    enum class StationKind {
        /** Ocean / estuary stations with harmonic tide predictions. */
        TidePrediction,
        /** Great Lakes and CO-OPS water-level gauges. */
        WaterLevel,
        /** USGS stream / lake stage gauges (parameter 00065). */
        UsgsGage,
    }

    data class TideStation(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val kind: StationKind,
    )

    data class TideResult(
        val station: TideStation?,
        val distanceMiles: Double?,
        /** epoch-hour (seconds, floored to hour) → height feet */
        val heightByEpochHour: Map<Long, Double>,
        val unavailableReason: String? = null,
        /** e.g. "MLLW", "LWD", or "gage" */
        val datumLabel: String? = null,
    ) {
        companion object {
            val EMPTY = TideResult(null, null, emptyMap(), null, null)
        }
    }

    @Volatile
    private var memoryStations: List<TideStation>? = null

    fun fetchHourlyTides(latitude: Double, longitude: Double): TideResult {
        val station = nearestStation(latitude, longitude)
        if (station != null) {
            return fetchForStation(station, latitude, longitude)
        }

        // USGS river / lake stage (covers most interior US cities)
        val usgs = findNearestUsgsGage(latitude, longitude, TideStationSelector.MAX_USGS_MILES)
        if (usgs != null) {
            val heights = fetchUsgsGageHeights(usgs.seriesId)
            return TideResult(
                station = usgs.station,
                distanceMiles = haversineMiles(latitude, longitude, usgs.station.lat, usgs.station.lon),
                heightByEpochHour = heights,
                unavailableReason = if (heights.isEmpty()) "No USGS stage data" else null,
                datumLabel = "gage",
            )
        }

        // Nothing in range — report closest CO-OPS station for diagnostics
        val fallback = loadCoopsStations()
            .minByOrNull { haversineMiles(latitude, longitude, it.lat, it.lon) }
        return TideResult(
            station = fallback,
            distanceMiles = fallback?.let { haversineMiles(latitude, longitude, it.lat, it.lon) },
            heightByEpochHour = emptyMap(),
            unavailableReason =
                "No tide, lake, or river gauge within ${TideStationSelector.MAX_USGS_MILES.toInt()} mi " +
                    "(USGS) / ${TideStationSelector.MAX_TIDE_PRED_MILES.toInt()} mi (NOAA tides)",
        )
    }

    /**
     * Resolve the gauge to remember for a city using the same priority as the forecast:
     * coastal tide predictions, then CO-OPS water levels, then USGS stage.
     */
    fun nearestStation(latitude: Double, longitude: Double): TideStation? {
        val stations = loadCoopsStations()
        val candidates = stations.map { station ->
            TideStationSelector.Candidate(
                id = station.id,
                name = station.name,
                lat = station.lat,
                lon = station.lon,
                kind = when (station.kind) {
                    StationKind.WaterLevel -> TideStationSelector.Kind.WaterLevel
                    StationKind.UsgsGage -> TideStationSelector.Kind.UsgsGage
                    else -> TideStationSelector.Kind.TidePrediction
                },
            )
        }
        val picked = TideStationSelector.pick(candidates, latitude, longitude) ?: return null
        return stations.firstOrNull { it.id == picked.id }
    }

    /** Hourly heights for a station that was previously picked for a city. */
    fun fetchForStation(
        station: TideStation,
        latitude: Double? = null,
        longitude: Double? = null,
    ): TideResult {
        val heights = when (station.kind) {
            StationKind.TidePrediction -> fetchTidePredictions(station.id)
            StationKind.WaterLevel -> fetchWaterLevels(station.id)
            // USGS needs its series id, which is only known from findNearestUsgsGage.
            StationKind.UsgsGage -> emptyMap()
        }
        return TideResult(
            station = station,
            distanceMiles = if (latitude != null && longitude != null) {
                haversineMiles(latitude, longitude, station.lat, station.lon)
            } else {
                null
            },
            heightByEpochHour = heights,
            unavailableReason = if (heights.isEmpty()) "No data for ${station.name}" else null,
            datumLabel = when (station.kind) {
                StationKind.WaterLevel -> "LWD"
                StationKind.UsgsGage -> "gage"
                else -> "MLLW"
            },
        )
    }

    /**
     * Tide heights for a CO-OPS prediction station.
     *
     * Reference stations expose full hourly series. **Subordinate** stations (e.g. Sacramento,
     * Clarksburg) are NOAA offset sites from a reference gauge — hourly `interval=h` often fails,
     * but high/low (`interval=hilo`) works. We then interpolate a continuous hourly curve
     * (cosine between successive H/L extrema), which is how offset tables are commonly used
     * for swim / small-craft planning (similar to consumer devices that show delta tides).
     */
    private fun fetchTidePredictions(stationId: String): Map<Long, Double> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val begin = fmt.format(cal.time)
        val hourlyUrl =
            "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
                "?begin_date=$begin&range=168&station=$stationId" +
                "&product=predictions&datum=MLLW&time_zone=gmt&interval=h" +
                "&units=english&format=json&application=PointForecast"
        val hourly = parseCoopsTimeValueSeries(httpGet(hourlyUrl), arrayKey = "predictions")
        if (hourly.isNotEmpty()) return hourly

        // Subordinate / offset stations: high-low extrema only
        val hiloUrl =
            "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
                "?begin_date=$begin&range=168&station=$stationId" +
                "&product=predictions&datum=MLLW&time_zone=gmt&interval=hilo" +
                "&units=english&format=json&application=PointForecast"
        val extrema = parseCoopsHilo(httpGet(hiloUrl))
        if (extrema.size >= 2) {
            return interpolateHiloToHourly(extrema)
        }

        // Last resort: apply NOAA tide-prediction offsets to the reference station hourly series
        return fetchSubordinateViaReferenceOffsets(stationId, begin)
    }

    private data class TideExtremum(val epochSec: Long, val heightFt: Double)

    private fun parseCoopsHilo(body: String?): List<TideExtremum> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(body)
            if (root.has("error")) return emptyList()
            val arr = root.optJSONArray("predictions") ?: return emptyList()
            val parse = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val out = ArrayList<TideExtremum>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("t")
                val v = o.optString("v").toDoubleOrNull() ?: continue
                val ms = parse.parse(t)?.time ?: continue
                out += TideExtremum(epochSec = ms / 1000L, heightFt = v)
            }
            out.sortedBy { it.epochSec }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Cosine interpolation between successive high/low extrema → one sample per UTC hour.
     * Produces smooth rising/falling segments for the hourly table.
     */
    private fun interpolateHiloToHourly(extrema: List<TideExtremum>): Map<Long, Double> {
        if (extrema.size < 2) return emptyMap()
        val startHour = (extrema.first().epochSec / 3600L) * 3600L
        val endHour = (extrema.last().epochSec / 3600L) * 3600L
        val out = HashMap<Long, Double>(((endHour - startHour) / 3600L + 4).toInt().coerceAtLeast(8))
        var seg = 0
        var hour = startHour
        while (hour <= endHour) {
            while (seg < extrema.lastIndex - 1 && extrema[seg + 1].epochSec < hour) {
                seg++
            }
            val a = extrema[seg]
            val b = extrema[(seg + 1).coerceAtMost(extrema.lastIndex)]
            val span = (b.epochSec - a.epochSec).coerceAtLeast(1L)
            val t = ((hour - a.epochSec).toDouble() / span).coerceIn(0.0, 1.0)
            // Cosine ease between turning points (tide-like shape)
            val w = (1.0 - kotlin.math.cos(Math.PI * t)) / 2.0
            out[hour] = a.heightFt + (b.heightFt - a.heightFt) * w
            hour += 3600L
        }
        return out
    }

    /**
     * Apply published NOAA subordinate offsets (time minutes + height ratio/add) to the
     * reference station's hourly predictions when direct station products are empty.
     */
    private fun fetchSubordinateViaReferenceOffsets(stationId: String, beginYmd: String): Map<Long, Double> {
        val offsetsUrl =
            "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations/$stationId/tidepredoffsets.json"
        val body = httpGet(offsetsUrl) ?: return emptyMap()
        return try {
            val root = JSONObject(body)
            val refId = root.optString("refStationId").ifBlank { return emptyMap() }
            val type = root.optString("type") // R = reference, S = subordinate
            if (!type.equals("S", ignoreCase = true)) return emptyMap()

            val timeHighMin = root.optDouble("timeOffsetHighTide", Double.NaN)
            val timeLowMin = root.optDouble("timeOffsetLowTide", Double.NaN)
            val heightHigh = root.optDouble("heightOffsetHighTide", Double.NaN)
            val heightLow = root.optDouble("heightOffsetLowTide", Double.NaN)
            val heightAdj = root.optString("heightAdjustedType") // R = ratio, C = constant

            // Average time lag (minutes) for a continuous series approximation
            val timeLagMin = listOf(timeHighMin, timeLowMin)
                .filter { !it.isNaN() }
                .average()
                .takeIf { !it.isNaN() }
                ?: return emptyMap()

            val refUrl =
                "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
                    "?begin_date=$beginYmd&range=168&station=$refId" +
                    "&product=predictions&datum=MLLW&time_zone=gmt&interval=h" +
                    "&units=english&format=json&application=PointForecast"
            val ref = parseCoopsTimeValueSeries(httpGet(refUrl), arrayKey = "predictions")
            if (ref.isEmpty()) return emptyMap()

            val lagSec = (timeLagMin * 60.0).toLong()
            val avgHeightFactor = listOf(heightHigh, heightLow)
                .filter { !it.isNaN() }
                .average()
                .takeIf { !it.isNaN() }
                ?: 1.0
            val isRatio = heightAdj.equals("R", ignoreCase = true) || avgHeightFactor <= 3.0

            // Shift reference series later by lag; scale heights
            val out = HashMap<Long, Double>(ref.size)
            ref.forEach { (epochHour, height) ->
                val shifted = ((epochHour + lagSec) / 3600L) * 3600L
                val h = if (isRatio) height * avgHeightFactor else height + avgHeightFactor
                out[shifted] = h
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun fetchWaterLevels(stationId: String): Map<Long, Double> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val begin = fmt.format(cal.time)

        val ofsUrl =
            "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
                "?begin_date=$begin&range=168&station=$stationId" +
                "&product=ofs_water_level&datum=LWD&time_zone=gmt&interval=h" +
                "&units=english&format=json&application=PointForecast"
        val ofs = parseCoopsTimeValueSeries(httpGet(ofsUrl), arrayKey = "data")
        if (ofs.isNotEmpty()) return ofs

        val obsUrl =
            "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
                "?begin_date=$begin&range=72&station=$stationId" +
                "&product=water_level&datum=LWD&time_zone=gmt&interval=h" +
                "&units=english&format=json&application=PointForecast"
        return parseCoopsTimeValueSeries(httpGet(obsUrl), arrayKey = "data")
    }

    private data class UsgsGagePick(
        val station: TideStation,
        val seriesId: String,
    )

    /**
     * Nearest USGS instantaneous gage height (parameter 00065) within [maxMiles].
     * One search covers the whole radius. A series observed in the last 7 days
     * wins over a closer series whose latest reading is older than that.
     */
    private fun findNearestUsgsGage(latitude: Double, longitude: Double, maxMiles: Double): UsgsGagePick? {
        val candidates = readUsgsPages(
            UsgsWaterData.latestContinuousUrl(latitude, longitude, maxMiles),
            UsgsWaterData.SEARCH_MAX_PAGES,
            UsgsWaterData::parseLatestPage,
        )
        val best = UsgsWaterData.pick(
            candidates,
            latitude,
            longitude,
            maxMiles,
            java.time.Instant.now().epochSecond,
        ) ?: return null
        val named = httpGet(UsgsWaterData.monitoringLocationUrl(best.locationId))
            ?.let { UsgsWaterData.parseMonitoringLocation(it) }
        val siteNo = named?.siteNo?.takeIf { it.isNotBlank() }
            ?: UsgsWaterData.siteNumber(best.locationId)
        if (siteNo.isBlank()) return null
        val name = named?.name?.takeIf { it.isNotBlank() } ?: "USGS $siteNo"
        return UsgsGagePick(
            station = TideStation(
                id = siteNo,
                name = name,
                lat = best.lat,
                lon = best.lon,
                kind = StationKind.UsgsGage,
            ),
            seriesId = best.seriesId,
        )
    }

    /** Instantaneous gage height (ft) for the last 7 days, bucketed by UTC hour. */
    private fun fetchUsgsGageHeights(seriesId: String): Map<Long, Double> {
        if (seriesId.isBlank()) return emptyMap()
        val samples = readUsgsPages(
            UsgsWaterData.continuousUrl(seriesId),
            UsgsWaterData.HISTORY_MAX_PAGES,
            UsgsWaterData::parseContinuousPage,
        )
        return UsgsWaterData.bucketHours(samples)
    }

    private fun <T> readUsgsPages(
        firstUrl: String,
        maxPages: Int,
        parse: (String) -> UsgsWaterData.Page<T>,
    ): List<T> {
        val out = ArrayList<T>()
        var url: String? = firstUrl
        val seen = HashSet<String>()
        var pages = 0
        while (url != null && pages < maxPages) {
            if (!UsgsWaterData.isWaterDataUrl(url) || !seen.add(url)) break
            val body = httpGet(url) ?: break
            val page = parse(body)
            out.addAll(page.items)
            url = page.nextUrl
            pages++
        }
        return out
    }

    private fun parseCoopsTimeValueSeries(body: String?, arrayKey: String): Map<Long, Double> {
        if (body.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(body)
            if (root.has("error")) return emptyMap()
            val arr = root.optJSONArray(arrayKey) ?: return emptyMap()
            val parse = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val out = HashMap<Long, Double>(arr.length() + 4)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("t")
                val v = o.optString("v").toDoubleOrNull() ?: continue
                val ms = parse.parse(t)?.time ?: continue
                val epochHour = (ms / 1000L / 3600L) * 3600L
                out[epochHour] = v
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun loadCoopsStations(): List<TideStation> {
        memoryStations?.let { return it }
        synchronized(this) {
            memoryStations?.let { return it }
            val cached = readCache()
            if (cached != null && cached.isNotEmpty()) {
                memoryStations = cached
                return cached
            }
            val downloaded = downloadAllStations()
            if (downloaded.isNotEmpty()) {
                writeCache(downloaded)
            }
            memoryStations = downloaded
            return downloaded
        }
    }

    private fun cacheFile(): File = File(context.filesDir, "tide_stations_v3.json")

    private fun readCache(): List<TideStation>? {
        val f = cacheFile()
        if (!f.isFile) return null
        val age = System.currentTimeMillis() - f.lastModified()
        if (age > CACHE_TTL_MS) return null
        return try {
            parseStationArray(JSONArray(f.readText()), forceKind = null)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(stations: List<TideStation>) {
        try {
            val arr = JSONArray()
            stations.forEach { s ->
                val k = when (s.kind) {
                    StationKind.WaterLevel -> "w"
                    StationKind.UsgsGage -> "u"
                    StationKind.TidePrediction -> "t"
                }
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("n", s.name)
                        .put("a", s.lat)
                        .put("o", s.lon)
                        .put("k", k),
                )
            }
            cacheFile().writeText(arr.toString())
        } catch (_: Exception) {
            // non-fatal
        }
    }

    private fun downloadAllStations(): List<TideStation> {
        val tide = downloadStationsOfType("tidepredictions", StationKind.TidePrediction)
        val water = downloadStationsOfType("waterlevels", StationKind.WaterLevel)
        val byId = LinkedHashMap<String, TideStation>()
        water.forEach { byId[it.id] = it }
        tide.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    private fun downloadStationsOfType(type: String, kind: StationKind): List<TideStation> {
        val url =
            "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=$type&units=english"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("stations") ?: return emptyList()
            parseStationArray(arr, forceKind = kind)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseStationArray(
        arr: JSONArray,
        forceKind: StationKind?,
    ): List<TideStation> {
        val list = ArrayList<TideStation>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            val name = when {
                o.has("n") -> o.optString("n")
                else -> o.optString("name")
            }
            val lat = when {
                o.has("a") -> o.optDouble("a")
                else -> o.optDouble("lat")
            }
            val lon = when {
                o.has("o") -> o.optDouble("o")
                else -> o.optDouble("lng", o.optDouble("lon", Double.NaN))
            }
            if (lat.isNaN() || lon.isNaN()) continue
            val kind = forceKind ?: when (o.optString("k")) {
                "w" -> StationKind.WaterLevel
                "u" -> StationKind.UsgsGage
                else -> StationKind.TidePrediction
            }
            list.add(TideStation(id = id, name = name, lat = lat, lon = lon, kind = kind))
        }
        return list
    }

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HttpUserAgent.VALUE)
            .header("Accept", "application/json, text/plain, */*")
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
        private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(30)

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
