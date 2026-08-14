package com.crome.forecastpoint.util

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object WeatherMath {
    fun feelsLikeF(tempF: Int?, humidityPct: Int?, windMph: Int?): Int? {
        val t = tempF ?: return null
        val rh = humidityPct
        val w = windMph ?: 0
        // Wind chill when cold & breezy
        if (t <= 50 && w >= 3) {
            val wc = 35.74 + 0.6215 * t - 35.75 * w.toDouble().pow(0.16) +
                0.4275 * t * w.toDouble().pow(0.16)
            return wc.roundToInt()
        }
        // Heat index when warm & humid
        if (t >= 80 && rh != null && rh >= 40) {
            val hi = -42.379 + 2.04901523 * t + 10.14333127 * rh -
                0.22475541 * t * rh - 6.83783e-3 * t * t -
                5.481717e-2 * rh * rh + 1.22874e-3 * t * t * rh +
                8.5282e-4 * t * rh * rh - 1.99e-6 * t * t * rh * rh
            return hi.roundToInt()
        }
        return t
    }

    /** Dew point °F from air temperature °F and relative humidity %. */
    fun dewPointF(tempF: Int?, humidityPct: Int?): Int? {
        val t = tempF ?: return null
        val rh = humidityPct ?: return null
        if (rh !in 1..100) return null
        val tC = (t - 32.0) * 5.0 / 9.0
        val a = 17.27
        val b = 237.7
        val alpha = ((a * tC) / (b + tC)) + ln(rh / 100.0)
        val dpC = (b * alpha) / (a - alpha)
        return (dpC * 9.0 / 5.0 + 32.0).roundToInt()
    }

    fun celsiusToF(c: Double): Int = (c * 9.0 / 5.0 + 32.0).roundToInt()

    fun kmhToMph(kmh: Double): Int = (kmh * 0.621371).roundToInt()

    fun mmToInches(mm: Double): Double = mm / 25.4

    fun degreesToCardinal(degrees: Int?): String? {
        if (degrees == null) return null
        val dirs = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        val idx = ((degrees % 360) / 22.5).roundToInt() % 16
        return dirs[idx]
    }

    fun inHgToMb(inHg: Double): Double = inHg * 33.8639

    fun metersToMiles(m: Double): Double = m / 1609.344

    fun hPaToInHg(hPa: Double): Double = hPa / 33.8639
}
