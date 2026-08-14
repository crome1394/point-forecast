package com.crome.forecastpoint.data

import com.crome.forecastpoint.util.WeatherMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo free APIs (no key) for fields NWS digitalJSON often lacks hourly:
 * UV index, surface pressure, visibility fallback, and US AQI.
 *
 * Docs: https://open-meteo.com/ — non-commercial free tier.
 */
class OpenMeteoService(
    private val client: OkHttpClient = defaultClient(),
) {
    data class HourlyExtras(
        val visibilityMi: Map<Long, Double> = emptyMap(),
        val pressureMb: Map<Long, Double> = emptyMap(),
        val uvIndex: Map<Long, Double> = emptyMap(),
        val usAqi: Map<Long, Int> = emptyMap(),
        val pm25: Map<Long, Double> = emptyMap(),
    ) {
        companion object {
            val EMPTY = HourlyExtras()
        }
    }

    suspend fun fetchExtras(latitude: Double, longitude: Double): HourlyExtras =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val weather = async {
                    runCatching { fetchWeatherHourly(latitude, longitude) }.getOrDefault(HourlyExtras.EMPTY)
                }
                val air = async {
                    runCatching { fetchAirQuality(latitude, longitude) }.getOrDefault(HourlyExtras.EMPTY)
                }
                val w = weather.await()
                val a = air.await()
                HourlyExtras(
                    visibilityMi = w.visibilityMi,
                    pressureMb = w.pressureMb,
                    uvIndex = w.uvIndex,
                    usAqi = a.usAqi,
                    pm25 = a.pm25,
                )
            }
        }

    private fun fetchWeatherHourly(latitude: Double, longitude: Double): HourlyExtras {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=visibility,surface_pressure,uv_index" +
                "&timezone=UTC&forecast_days=7"
        val root = JSONObject(getString(url))
        val hourly = root.optJSONObject("hourly") ?: return HourlyExtras.EMPTY
        val times = hourly.optJSONArray("time") ?: return HourlyExtras.EMPTY
        val vis = hourly.optJSONArray("visibility")
        val pres = hourly.optJSONArray("surface_pressure")
        val uv = hourly.optJSONArray("uv_index")
        val visMap = mutableMapOf<Long, Double>()
        val presMap = mutableMapOf<Long, Double>()
        val uvMap = mutableMapOf<Long, Double>()
        for (i in 0 until times.length()) {
            val epoch = parseUtcHour(times.optString(i)) ?: continue
            vis?.optDouble(i, Double.NaN)?.takeIf { !it.isNaN() }?.let {
                visMap[epoch] = WeatherMath.metersToMiles(it)
            }
            pres?.optDouble(i, Double.NaN)?.takeIf { !it.isNaN() }?.let {
                presMap[epoch] = it
            }
            uv?.optDouble(i, Double.NaN)?.takeIf { !it.isNaN() }?.let {
                uvMap[epoch] = it
            }
        }
        return HourlyExtras(visibilityMi = visMap, pressureMb = presMap, uvIndex = uvMap)
    }

    private fun fetchAirQuality(latitude: Double, longitude: Double): HourlyExtras {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        val url =
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                "&hourly=us_aqi,pm2_5&timezone=UTC&forecast_days=5"
        val root = JSONObject(getString(url))
        val hourly = root.optJSONObject("hourly") ?: return HourlyExtras.EMPTY
        val times = hourly.optJSONArray("time") ?: return HourlyExtras.EMPTY
        val aqi = hourly.optJSONArray("us_aqi")
        val pm = hourly.optJSONArray("pm2_5")
        val aqiMap = mutableMapOf<Long, Int>()
        val pmMap = mutableMapOf<Long, Double>()
        for (i in 0 until times.length()) {
            val epoch = parseUtcHour(times.optString(i)) ?: continue
            if (aqi != null && !aqi.isNull(i)) {
                val v = aqi.optDouble(i, Double.NaN)
                if (!v.isNaN()) aqiMap[epoch] = v.toInt()
            }
            if (pm != null && !pm.isNull(i)) {
                val v = pm.optDouble(i, Double.NaN)
                if (!v.isNaN()) pmMap[epoch] = v
            }
        }
        return HourlyExtras(usAqi = aqiMap, pm25 = pmMap)
    }

    private fun parseUtcHour(isoLocal: String): Long? {
        if (isoLocal.isBlank()) return null
        // "2026-08-14T15:00" as UTC (we requested timezone=UTC)
        val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val ms = runCatching { parse.parse(isoLocal)?.time }.getOrNull() ?: return null
        return (ms / 1000L / 3600L) * 3600L
    }

    private fun getString(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
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
