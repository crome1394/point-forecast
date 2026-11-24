package com.crome.forecastpoint.util

import java.util.Locale

/** Caption under the tide station name: location and NOAA station code. */
object TideStationLabel {
    fun detailLine(id: String?, lat: Double?, lon: Double?): String? {
        if (lat == null || lon == null) return null
        val coords = String.format(Locale.US, "%.4f, %.4f", lat, lon)
        val code = id?.takeIf { it.isNotBlank() } ?: return coords
        return "$coords · Code $code"
    }
}
