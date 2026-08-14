package com.crome.forecastpoint.data

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
 * NOAA CO-OPS tide predictions (hourly) for the nearest tide-prediction station.
 * Station catalog is downloaded once and cached on disk for 30 days.
 */
class TideService(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    data class TideStation(val id: String, val name: String, val lat: Double, val lon: Double)

    data class TideResult(
        val station: TideStation?,
        val distanceMiles: Double?,
        /** epoch-hour (seconds, floored to hour) → height feet MLLW */
        val heightByEpochHour: Map<Long, Double>,
        val unavailableReason: String? = null,
    ) {
        companion object {
            val EMPTY = TideResult(null, null, emptyMap(), null)
        }
    }

    @Volatile
    private var memoryStations: List<TideStation>? = null

    fun fetchHourlyTides(latitude: Double, longitude: Double): TideResult {
        val stations = loadStations()
        if (stations.isEmpty()) {
            return TideResult.EMPTY.copy(unavailableReason = "Tide stations unavailable")
        }
        val nearest = stations.minByOrNull { haversineMiles(latitude, longitude, it.lat, it.lon) }
            ?: return TideResult.EMPTY
        val dist = haversineMiles(latitude, longitude, nearest.lat, nearest.lon)
        if (dist > MAX_STATION_MILES) {
            return TideResult(
                station = nearest,
                distanceMiles = dist,
                heightByEpochHour = emptyMap(),
                unavailableReason = "No tide station within ${MAX_STATION_MILES.toInt()} mi",
            )
        }
        val heights = fetchPredictions(nearest.id)
        return TideResult(
            station = nearest,
            distanceMiles = dist,
            heightByEpochHour = heights,
            unavailableReason = if (heights.isEmpty()) "No tide predictions" else null,
        )
    }

    private fun fetchPredictions(stationId: String): Map<Long, Double> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val begin = fmt.format(cal.time)
        // 7 days of hourly predictions
        val url =
            "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter" +
                "?begin_date=$begin&range=168&station=$stationId" +
                "&product=predictions&datum=MLLW&time_zone=gmt&interval=h" +
                "&units=english&format=json&application=PointForecast"

        val body = httpGet(url) ?: return emptyMap()
        val root = JSONObject(body)
        if (root.has("error")) return emptyMap()
        val preds = root.optJSONArray("predictions") ?: return emptyMap()
        val parse = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val out = HashMap<Long, Double>(preds.length() + 4)
        for (i in 0 until preds.length()) {
            val o = preds.optJSONObject(i) ?: continue
            val t = o.optString("t")
            val v = o.optString("v").toDoubleOrNull() ?: continue
            val ms = parse.parse(t)?.time ?: continue
            val epochHour = (ms / 1000L / 3600L) * 3600L
            out[epochHour] = v
        }
        return out
    }

    private fun loadStations(): List<TideStation> {
        memoryStations?.let { return it }
        synchronized(this) {
            memoryStations?.let { return it }
            val cached = readCache()
            if (cached != null) {
                memoryStations = cached
                return cached
            }
            val downloaded = downloadStations()
            if (downloaded.isNotEmpty()) {
                writeCache(downloaded)
            }
            memoryStations = downloaded
            return downloaded
        }
    }

    private fun cacheFile(): File = File(context.filesDir, "tide_stations_v1.json")

    private fun readCache(): List<TideStation>? {
        val f = cacheFile()
        if (!f.isFile) return null
        val age = System.currentTimeMillis() - f.lastModified()
        if (age > CACHE_TTL_MS) return null
        return try {
            parseStationArray(JSONArray(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(stations: List<TideStation>) {
        try {
            val arr = JSONArray()
            stations.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("n", s.name)
                        .put("a", s.lat)
                        .put("o", s.lon),
                )
            }
            cacheFile().writeText(arr.toString())
        } catch (_: Exception) {
            // non-fatal
        }
    }

    private fun downloadStations(): List<TideStation> {
        val url =
            "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions&units=english"
        val body = httpGet(url) ?: return emptyList()
        return try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("stations") ?: return emptyList()
            parseStationArray(arr, compact = false)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseStationArray(arr: JSONArray, compact: Boolean = true): List<TideStation> {
        val list = ArrayList<TideStation>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString(if (compact && o.has("id")) "id" else "id")
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
            list.add(TideStation(id = id, name = name, lat = lat, lon = lon))
        }
        return list
    }

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PointForecast/1.0 (Android; personal)")
            .header("Accept", "application/json")
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
        private const val MAX_STATION_MILES = 80.0
        private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(30)

        fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 3958.8 // Earth radius miles
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
