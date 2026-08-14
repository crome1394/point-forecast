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

    data class ScaleLevel(
        val scale: String,
        val text: String,
    )

    data class Snapshot(
        val updatedAtEpochMs: Long,
        val currentGScale: String?,
        val currentGText: String?,
        val currentRScale: String? = null,
        val currentRText: String? = null,
        val currentSScale: String? = null,
        val currentSText: String? = null,
        val periods: List<Period>,
    ) {
        /**
         * Title-bar cue level for elevated space weather.
         * Not a system notification — only changes the sun/moon icon appearance.
         *
         * @param watchMin minimum NOAA scale (1–5) for Watch (default 1 = G1/R1/S1)
         * @param activeMin minimum NOAA scale (1–5) for Active (default 2 = G2/R2/S2)
         * @param forecastHorizonHours how far ahead predicted G counts
         */
        fun alertLevel(
            watchMin: Int = 1,
            activeMin: Int = 2,
            forecastHorizonHours: Int = 48,
            nowEpochMs: Long = System.currentTimeMillis(),
        ): SpaceWeatherAlert {
            val watch = watchMin.coerceIn(1, 5)
            val active = activeMin.coerceIn(watch, 5)

            val gNow = scaleNumber(currentGScale)
            val rNow = scaleNumber(currentRScale)
            val sNow = scaleNumber(currentSScale)
            val maxNow = maxOf(gNow, rNow, sNow)

            val horizonSec = nowEpochMs / 1000L + forecastHorizonHours.coerceIn(1, 168) * 3600L
            val maxPredictedG = periods
                .asSequence()
                .filter { it.status.equals("Predicted", ignoreCase = true) }
                .filter { it.epochSec <= horizonSec }
                .maxOfOrNull { scaleNumber(it.gScale) }
                ?: 0

            val peak = maxOf(maxNow, maxPredictedG)
            return when {
                peak >= active -> SpaceWeatherAlert.Active
                peak >= watch -> SpaceWeatherAlert.Watch
                else -> SpaceWeatherAlert.Quiet
            }
        }
    }

    /** Visual / title-bar alert for elevated SWPC conditions. */
    enum class SpaceWeatherAlert {
        /** G0/R0/S0 and no G1+ predicted in the next ~48h. */
        Quiet,
        /** Minor activity: G1, R1, S1 now or G1+ forecast soon. */
        Watch,
        /** Moderate+ : G2+/R2+/S2+ now or G2+ forecast soon. */
        Active,
    }

    suspend fun fetch(): Snapshot = withContext(Dispatchers.IO) {
        val periods = runCatching { fetchKpForecast() }.getOrDefault(emptyList())
        val scales = runCatching { fetchAllScales() }.getOrNull()
        Snapshot(
            updatedAtEpochMs = System.currentTimeMillis(),
            currentGScale = scales?.g?.scale,
            currentGText = scales?.g?.text,
            currentRScale = scales?.r?.scale,
            currentRText = scales?.r?.text,
            currentSScale = scales?.s?.scale,
            currentSText = scales?.s?.text,
            periods = periods,
        )
    }

    private data class AllScales(
        val r: ScaleLevel?,
        val s: ScaleLevel?,
        val g: ScaleLevel?,
    )

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

    /** Current NOAA R / S / G scales from noaa-scales.json */
    private fun fetchAllScales(): AllScales {
        val body = getString("https://services.swpc.noaa.gov/products/noaa-scales.json")
        val root = JSONObject(body)
        val now = root.optJSONObject("0") ?: return AllScales(null, null, null)
        fun read(key: String, prefix: String): ScaleLevel? {
            val o = now.optJSONObject(key) ?: return null
            val scale = o.optString("Scale").takeIf { it.isNotBlank() } ?: return null
            val text = o.optString("Text").takeIf { it.isNotBlank() } ?: "—"
            val label = if (scale.startsWith(prefix)) scale else "$prefix$scale"
            return ScaleLevel(label, text)
        }
        return AllScales(
            r = read("R", "R"),
            s = read("S", "S"),
            g = read("G", "G"),
        )
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

    companion object {
        private const val USER_AGENT =
            "PointForecast/1.0 (Android; open-source; https://github.com/crome1394/forecast-point)"

        /** Parse "G2", "R1", "S0" → 0–5 (unknown → 0). */
        fun scaleNumber(scale: String?): Int {
            if (scale.isNullOrBlank() || scale == "—") return 0
            val digits = scale.trim().uppercase(Locale.US).dropWhile { !it.isDigit() }
            return digits.takeWhile { it.isDigit() }.toIntOrNull()?.coerceIn(0, 5) ?: 0
        }

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()
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

}
