package com.crome.forecastpoint.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reformat stored AM/PM clock strings to the device's 12/24-hour setting. */
object ClockTime {
    fun format(value: String?, use24Hour: Boolean): String? {
        if (value.isNullOrBlank() || value == "—") return value
        val parsed = parse(value.trim()) ?: return value
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(parsed)
    }

    private fun parse(value: String): Date? {
        val parsers = listOf(
            SimpleDateFormat("h:mm a", Locale.US),
            SimpleDateFormat("HH:mm", Locale.getDefault()),
        )
        parsers.forEach { it.isLenient = false }
        return parsers.firstNotNullOfOrNull { parser ->
            runCatching { parser.parse(value) }.getOrNull()
        }
    }
}
