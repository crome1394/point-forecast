package com.crome.forecastpoint.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * NOAA Space Weather Prediction Center (SWPC) products — planetary, not location-specific.
 * https://services.swpc.noaa.gov/
 */
class SpaceWeatherService(
    private val client: OkHttpClient = defaultClient(),
) {
    data class Period(
        val epochSec: Long,
        /** UTC start of the 3-hour bin */
        val timeLabelUtc: String,
        val kp: Double?,
        /** G0–G5 derived from Kp when SWPC scale is absent */
        val gScale: String,
        val status: String,
    )

    data class Snapshot(
        val updatedAtEpochMs: Long,
        val currentGScale: String?,
        val currentGText: String?,
        val periods: List<Period>,
    )

    suspend fun fetch(): Snapshot = withContext(Dispatchers.IO) {
        val periods = runCatching { fetchKpForecast() }.getOrDefault(emptyList())
        val scales = runCatching { fetchCurrentScales() }.getOrNull()
        Snapshot(
            updatedAtEpochMs = System.currentTimeMillis(),
            currentGScale = scales?.first,
            currentGText = scales?.second,
            periods = periods,
        )
    }

    private fun fetchKpForecast(): List<Period> {
        val body = getString(
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json",
        )
        val arr = JSONArray(body)
        val out = ArrayList<Period>(arr.length())
        val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val labelFmt = SimpleDateFormat("EEE M/d\nHH'z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        // Keep recent observed + upcoming predicted (last ~5 days of file is enough UI)
        val start = (arr.length() - 48).coerceAtLeast(0)
        for (i in start until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tag = o.optString("time_tag")
            if (tag.isBlank()) continue
            val ms = runCatching { parse.parse(tag)?.time }.getOrNull() ?: continue
            val kp = when {
                !o.isNull("kp") -> o.optDouble("kp", Double.NaN).takeIf { !it.isNaN() }
                else -> null
            }
            val status = o.optString("observed").ifBlank { "unknown" }
            val scaleRaw = o.optString("noaa_scale").takeIf { it.isNotBlank() && it != "null" }
            val gScale = scaleRaw?.let { if (it.startsWith("G")) it else "G$it" }
                ?: kpToGScale(kp)
            out += Period(
                epochSec = ms / 1000L,
                timeLabelUtc = labelFmt.format(ms),
                kp = kp,
                gScale = gScale,
                status = status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
            )
        }
        return out
    }

    /** Current NOAA G (geomagnetic) scale from noaa-scales.json */
    private fun fetchCurrentScales(): Pair<String, String>? {
        val body = getString("https://services.swpc.noaa.gov/products/noaa-scales.json")
        val root = JSONObject(body)
        val now = root.optJSONObject("0") ?: return null
        val g = now.optJSONObject("G") ?: return null
        val scale = g.optString("Scale").takeIf { it.isNotBlank() } ?: return null
        val text = g.optString("Text").takeIf { it.isNotBlank() } ?: "—"
        val label = if (scale.startsWith("G")) scale else "G$scale"
        return label to text
    }

    private fun kpToGScale(kp: Double?): String {
        if (kp == null) return "—"
        val k = kp.roundToInt().coerceIn(0, 9)
        return when {
            k < 5 -> "G0"
            k == 5 -> "G1"
            k == 6 -> "G2"
            k == 7 -> "G3"
            k == 8 -> "G4"
            else -> "G5"
        }
    }

    private fun getString(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: throw IllegalStateException("Empty body")
        }
    }

    companion object {
        private const val USER_AGENT =
            "ForecastPoint/1.0 (Android; open-source; https://github.com/crome1394/forecast-point)"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()
    }
}
