package com.crome.forecastpoint.util

import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Resolve a [TimeZone] for a forecast location (not the device default).
 *
 * Prefer NWS MapClick `location.timezone` codes, then an ISO-8601 offset from
 * period start times, then a longitude-based fixed offset as a last resort.
 */
object LocationTimeZone {
    private val isoOffset = Pattern.compile("([+-])(\\d{2}):(\\d{2})$")

    /**
     * @param nwsCode NWS style e.g. `E|Y|5` (zone|DST flag|standard UTC offset hours)
     * @param isoDateTime e.g. `2026-08-15T19:00:00-04:00` from startValidTime
     */
    fun resolve(
        latitude: Double,
        longitude: Double,
        nwsCode: String? = null,
        isoDateTime: String? = null,
    ): TimeZone {
        fromNwsCode(nwsCode)?.let { return it }
        fromIsoOffset(isoDateTime)?.let { return it }
        return fromLongitude(longitude)
    }

    /** Map NWS `location.timezone` codes to IANA zones (handles DST). */
    fun fromNwsCode(code: String?): TimeZone? {
        if (code.isNullOrBlank()) return null
        val letter = code.trim().substringBefore('|').uppercase(Locale.US)
        val id = when (letter) {
            "E", "V" -> "America/New_York" // Eastern; V sometimes used in NE
            "C" -> "America/Chicago"
            "M" -> "America/Denver"
            "P" -> "America/Los_Angeles"
            "A" -> "America/Anchorage"
            "H" -> "Pacific/Honolulu"
            "G" -> "Pacific/Guam"
            "J" -> "America/Puerto_Rico" // Atlantic / Caribbean
            "S" -> "Pacific/Pago_Pago" // Samoa
            "F" -> "Pacific/Chuuk" // rarely used
            else -> return null
        }
        return TimeZone.getTimeZone(id)
    }

    fun fromIsoOffset(isoDateTime: String?): TimeZone? {
        if (isoDateTime.isNullOrBlank()) return null
        val m = isoOffset.matcher(isoDateTime.trim())
        if (!m.find()) return null
        val sign = m.group(1)
        val hh = m.group(2)
        val mm = m.group(3)
        // java.util.TimeZone accepts "GMT-04:00"
        return TimeZone.getTimeZone("GMT$sign$hh:$mm")
    }

    /** Fixed offset from longitude (no DST) — last resort only. */
    fun fromLongitude(longitude: Double): TimeZone {
        val hours = ((longitude + 7.5) / 15.0).toInt().coerceIn(-12, 14)
        val id = if (hours >= 0) "GMT+$hours" else "GMT$hours"
        return TimeZone.getTimeZone(id)
    }
}
