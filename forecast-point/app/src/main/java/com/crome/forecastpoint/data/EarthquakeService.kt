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
 * Catalog depth: modern FDSN coverage is multi-decade (often to ~1900 for larger events).
 * For long history windows we raise the minimum magnitude so a limited result set still
 * spans the full look-back (M1+ in California can exceed 20k events/year).
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
        /** Search radius used (km). */
        val radiusKm: Double,
        /** Look-back window (days) for preset mode; custom uses start/end. */
        val historyDays: Int = 7,
        val historyStartMs: Long = 0L,
        val historyEndMs: Long = 0L,
        /** Catalog min magnitude used for the query (by window length). */
        val catalogMinMagnitude: Double = 1.0,
        /** Events in the history window within focus radius. */
        val recentAll: List<Quake>,
        /** @deprecated kept empty for callers; use [recentAll]. */
        val recentNotable: List<Quake> = emptyList(),
        /** @deprecated kept empty for callers; use [recentAll]. */
        val historical: List<Quake> = emptyList(),
        val updatedAtEpochMs: Long,
        val error: String? = null,
        /** Human-readable query summary for transparency / validation. */
        val querySummary: String = "",
    ) {
        /** Back-compat alias used by older UI call sites. */
        val recent: List<Quake> get() = recentAll
    }

    /**
     * @param focusRadiusMiles map focus / explore radius.
     * @param historyDays look-back when [historyStartMs]/[historyEndMs] are null.
     * @param historyStartMs optional custom range start (inclusive, UTC day).
     * @param historyEndMs optional custom range end (inclusive, UTC day end).
     */
    suspend fun fetchAround(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int = 250,
        historyDays: Int = 7,
        historyStartMs: Long? = null,
        historyEndMs: Long? = null,
    ): Snapshot = withContext(Dispatchers.IO) {
        val endMs = historyEndMs ?: System.currentTimeMillis()
        val startMs = historyStartMs
            ?: (endMs - historyDays.coerceIn(1, MAX_HISTORY_DAYS).toLong() * 24L * 3600L * 1000L)
        val days = (
            ((endMs - startMs).coerceAtLeast(0L) / (24L * 3600L * 1000L)).toInt()
            ).coerceAtLeast(1)
        val focusKm = (focusRadiusMiles.coerceIn(25, 4000) * KM_PER_MILE)
        val focusMiles = focusRadiusMiles.toDouble().coerceIn(25.0, 4000.0)
        val startIso = isoFromEpoch(startMs)
        val endIso = isoFromEpoch(endMs)

        // Raise min mag for longer custom ranges so query size stays manageable.
        var minMag = catalogMinMagnitudeForHistory(days)
        var lastError: String? = null
        var reports = emptyList<Quake>()

        // Prefer user focus radius; expand only if quiet (empty) at that distance.
        val radii = buildList {
            add(focusKm)
            listOf(100.0, 200.0, 350.0, 500.0, 800.0).forEach { r ->
                if (r > focusKm + 1) add(r)
            }
        }.distinct()

        for (attempt in 0 until 4) {
            var gotAny = false
            for (r in radii) {
                val result = runCatching {
                    query(
                        latitude = latitude,
                        longitude = longitude,
                        maxRadiusKm = r,
                        startTimeIso = startIso,
                        endTimeIso = endIso,
                        minMagnitude = minMag,
                        orderBy = "time",
                        limit = QUERY_LIMIT,
                    )
                }
                if (result.isFailure) {
                    lastError = result.exceptionOrNull()?.message
                    continue
                }
                val raw = result.getOrDefault(emptyList())
                if (raw.isNotEmpty()) gotAny = true
                val inFocus = raw.filter { it.distanceMiles <= focusMiles }
                reports = if (inFocus.isNotEmpty()) inFocus else raw
                if (reports.isNotEmpty()) break
            }
            if (!gotAny) break

            // If we hit the API limit and the oldest event is still much newer than the
            // history start, raise min magnitude so the window can span further back.
            val oldest = reports.minOfOrNull { it.timeEpochMs } ?: break
            val hitLimit = reports.size >= QUERY_LIMIT * 0.9
            val windowCovered = oldest <= startMs + ((endMs - startMs) * 0.15).toLong()
            if (!hitLimit || windowCovered || minMag >= 5.0) break
            minMag = (minMag + 0.5).coerceAtMost(5.0)
        }

        // Time-span the list so a busy region still shows older events in a long window.
        val forUi = selectTimeSpanning(reports, UI_MAX_EVENTS)
            .sortedByDescending { it.timeEpochMs }

        val oldestLabel = forUi.minOfOrNull { it.timeEpochMs }?.let { formatEpochDate(it) } ?: "—"
        val newestLabel = forUi.maxOfOrNull { it.timeEpochMs }?.let { formatEpochDate(it) } ?: "—"

        val summary =
            "USGS FDSN · focus ${focusRadiusMiles} mi · $startIso → $endIso · catalog M≥" +
                String.format(java.util.Locale.US, "%.1f", minMag) +
                " · ${forUi.size} reports · span $oldestLabel → $newestLabel"

        Snapshot(
            latitude = latitude,
            longitude = longitude,
            radiusKm = focusKm,
            historyDays = days,
            historyStartMs = startMs,
            historyEndMs = endMs,
            catalogMinMagnitude = minMag,
            recentAll = forUi,
            recentNotable = emptyList(),
            historical = emptyList(),
            updatedAtEpochMs = System.currentTimeMillis(),
            error = if (forUi.isEmpty()) lastError else null,
            querySummary = summary,
        )
    }

    private fun query(
        latitude: Double,
        longitude: Double,
        maxRadiusKm: Double,
        startTimeIso: String,
        endTimeIso: String,
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
                "&endtime=$endTimeIso" +
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

    private fun isoFromEpoch(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMs
        return String.format(
            java.util.Locale.US,
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    private fun formatEpochDate(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMs
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
        /** USGS allows large limits; keep reasonable for mobile. */
        private const val QUERY_LIMIT = 1500
        private const val UI_MAX_EVENTS = 120
        /** Practical max look-back we expose in Settings (USGS FDSN supports multi-decade). */
        const val MAX_HISTORY_DAYS = 7300 // 20 years

        /**
         * Catalog min magnitude by history length so limited result sets still cover
         * the full window in active regions.
         */
        fun catalogMinMagnitudeForHistory(historyDays: Int): Double = when {
            historyDays <= 7 -> 1.0
            historyDays <= 30 -> 1.5
            historyDays <= 90 -> 2.0
            historyDays <= 365 -> 2.5
            historyDays <= 1825 -> 3.0 // ~5 years
            historyDays <= 2555 -> 3.5 // ~7 years
            historyDays <= 3650 -> 4.0 // ~10 years
            else -> 4.5 // ~20 years
        }

        /**
         * Pick up to [maxCount] events evenly across the time range (plus a few strongest)
         * so long history is not dominated by the last few months.
         */
        fun selectTimeSpanning(events: List<Quake>, maxCount: Int): List<Quake> {
            if (events.size <= maxCount) return events
            val byTime = events.sortedBy { it.timeEpochMs }
            val selected = LinkedHashMap<String, Quake>()
            // Strongest events first (ensure large quakes always appear)
            events.sortedByDescending { it.magnitude ?: 0.0 }
                .take((maxCount * 0.25).toInt().coerceAtLeast(8))
                .forEach { selected[it.id] = it }
            // Evenly sample across time
            val slots = (maxCount - selected.size).coerceAtLeast(1)
            if (byTime.size >= 2) {
                for (i in 0 until slots) {
                    val idx = (i.toDouble() / (slots - 1).coerceAtLeast(1) * (byTime.lastIndex)).toInt()
                    val q = byTime[idx.coerceIn(0, byTime.lastIndex)]
                    selected[q.id] = q
                    if (selected.size >= maxCount) break
                }
            }
            // Fill remainder with nearest
            if (selected.size < maxCount) {
                events.sortedBy { it.distanceMiles }.forEach { q ->
                    if (selected.size >= maxCount) return@forEach
                    selected.putIfAbsent(q.id, q)
                }
            }
            return selected.values.toList()
        }

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
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
