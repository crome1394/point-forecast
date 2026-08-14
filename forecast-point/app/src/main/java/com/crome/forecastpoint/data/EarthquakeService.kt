package com.crome.forecastpoint.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * USGS Earthquake Hazards Program — FDSN event API (GeoJSON).
 * https://earthquake.usgs.gov/fdsnws/event/1/
 *
 * **Validation notes (checked against live USGS FDSN):**
 * - Sacramento: hundreds of M1+ events/month within 200 km (esp. Geysers geothermal) — correct.
 * - Hemet / SoCal: dense M1+ activity — correct.
 * - New York: often **zero** M1+ within 200 km in a quiet month; expand to ~350–500 km
 *   to surface New England / mid-Atlantic events — correct USGS behavior, not a bug.
 * - Consumer apps often default to **M2.5+** or “significant only”, so counts look lower.
 *   We expose both “all M1+” and “M2.5+” lists sorted by **distance**.
 */
class EarthquakeService(
    private val client: OkHttpClient = defaultClient(),
) {
    data class Quake(
        val id: String,
        val magnitude: Double?,
        val place: String,
        val timeEpochMs: Long,
        val latitude: Double,
        val longitude: Double,
        val depthKm: Double?,
        val url: String?,
        val distanceMiles: Double,
    )

    data class Snapshot(
        val latitude: Double,
        val longitude: Double,
        /** Search radius used for recent M1+ (km). */
        val radiusKm: Double,
        /** Last 30 days, M ≥ 1.0, nearest first. */
        val recentAll: List<Quake>,
        /** Last 30 days, M ≥ 2.5, nearest first (closer to many third-party maps). */
        val recentNotable: List<Quake>,
        /** Last 10 years, M ≥ 4.0 within a wide radius, strongest first. */
        val historical: List<Quake>,
        val updatedAtEpochMs: Long,
        val error: String? = null,
        /** Human-readable query summary for transparency / validation. */
        val querySummary: String = "",
    ) {
        /** Back-compat alias used by older UI call sites. */
        val recent: List<Quake> get() = recentAll
    }

    /**
     * @param focusRadiusMiles user setting (Settings → Map focus radius); applied to
     *   **historical** queries and used as the preferred window for recent lists.
     */
    suspend fun fetchAround(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int = 250,
    ): Snapshot = withContext(Dispatchers.IO) {
        val focusKm = (focusRadiusMiles.coerceIn(25, 1500) * KM_PER_MILE)
        // Prefer the user focus radius first; expand only if that window is empty (quiet regions)
        val radii = buildList {
            add(focusKm)
            listOf(100.0, 200.0, 350.0, 500.0, 800.0).forEach { r ->
                if (r > focusKm + 1) add(r)
            }
        }.distinct()
        var recentAll = emptyList<Quake>()
        var usedRadius = radii.first()
        var lastError: String? = null
        for (r in radii) {
            usedRadius = r
            val result = runCatching {
                query(
                    latitude = latitude,
                    longitude = longitude,
                    maxRadiusKm = r,
                    startTimeIso = isoDaysAgo(30),
                    minMagnitude = 1.0,
                    orderBy = "time",
                    limit = 200,
                )
            }
            if (result.isFailure) {
                lastError = result.exceptionOrNull()?.message
                continue
            }
            recentAll = result.getOrDefault(emptyList())
                .sortedBy { it.distanceMiles }
            if (recentAll.isNotEmpty()) break
        }

        // Keep table/map markers within the focus radius when we had to expand for “any activity”
        val focusMiles = focusRadiusMiles.toDouble().coerceIn(25.0, 1500.0)
        val recentInFocus = recentAll.filter { it.distanceMiles <= focusMiles }
        val recentForUi = if (recentInFocus.isNotEmpty()) recentInFocus else recentAll

        val recentNotable = recentForUi
            .filter { (it.magnitude ?: 0.0) >= 2.5 }
            .sortedBy { it.distanceMiles }
            .take(40)

        // Historical always uses the user map-focus distance (same as map zoom)
        val historical = runCatching {
            query(
                latitude = latitude,
                longitude = longitude,
                maxRadiusKm = focusKm,
                startTimeIso = isoDaysAgo(365 * 10),
                minMagnitude = 4.0,
                orderBy = "magnitude",
                limit = 40,
            ).filter { it.distanceMiles <= focusMiles }
                .sortedByDescending { it.magnitude ?: 0.0 }
        }.getOrDefault(emptyList())

        val summary =
            "USGS FDSN · focus ${focusRadiusMiles} mi · last 30d M≥1.0 " +
                "(${recentForUi.size} events) · M≥2.5 subset ${recentNotable.size} · " +
                "historical 10y M≥4.0 within ${focusRadiusMiles} mi (${historical.size})"

        Snapshot(
            latitude = latitude,
            longitude = longitude,
            radiusKm = focusKm,
            recentAll = recentForUi.take(50),
            recentNotable = recentNotable,
            historical = historical,
            updatedAtEpochMs = System.currentTimeMillis(),
            error = if (recentForUi.isEmpty() && historical.isEmpty()) lastError else null,
            querySummary = summary,
        )
    }

    private fun query(
        latitude: Double,
        longitude: Double,
        maxRadiusKm: Double,
        startTimeIso: String,
        minMagnitude: Double,
        orderBy: String,
        limit: Int,
    ): List<Quake> {
        val url =
            "https://earthquake.usgs.gov/fdsnws/event/1/query" +
                "?format=geojson" +
                "&latitude=$latitude" +
                "&longitude=$longitude" +
                "&maxradiuskm=$maxRadiusKm" +
                "&starttime=$startTimeIso" +
                "&minmagnitude=$minMagnitude" +
                "&orderby=$orderBy" +
                "&limit=$limit"
        val body = httpGet(url) ?: throw IllegalStateException("No response from USGS")
        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Quake>(features.length())
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val props = f.optJSONObject("properties") ?: continue
            val geom = f.optJSONObject("geometry") ?: continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            // GeoJSON: [lon, lat, depth_km]
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            val depth = coords.optDouble(2).takeIf { !it.isNaN() }
            if (lat.isNaN() || lon.isNaN()) continue
            val mag = if (props.isNull("mag")) null else props.optDouble("mag")
            val place = props.optString("place").ifBlank { "Unknown location" }
            val time = props.optLong("time", 0L)
            val id = f.optString("id").ifBlank { "${time}_$lat" }
            val dist = haversineMiles(latitude, longitude, lat, lon)
            out += Quake(
                id = id,
                magnitude = mag,
                place = place,
                timeEpochMs = time,
                latitude = lat,
                longitude = lon,
                depthKm = depth,
                url = props.optString("url").takeIf { it.startsWith("http") },
                distanceMiles = dist,
            )
        }
        return out
    }

    private fun isoDaysAgo(days: Int): String {
        val ms = System.currentTimeMillis() - days.toLong() * 24L * 3600L * 1000L
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
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
        private const val USER_AGENT =
            "PointForecast/1.0 (Android; open-source; https://github.com/crome1394/forecast-point)"
        private const val KM_PER_MILE = 1.609344

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
