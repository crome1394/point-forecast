package com.crome.forecastpoint.util

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Format weather numbers for the user's selected unit system.
 *
 * Values on a snapshot are already in that snapshot's unit system (see
 * [com.crome.forecastpoint.data.WeatherSnapshot.unitSystem]). Pass
 * [storedMetric] for the snapshot and [displayMetric] for the setting.
 * When they differ (toggle before the reload finishes, or an older cache),
 * values are converted.
 *
 * Elevation and tide-station distance are always stored in feet / miles.
 * Pressure on hourly rows is always hPa / mb.
 */
object Units {
    fun temperature(value: Int?, storedMetric: Boolean, displayMetric: Boolean): String {
        val shown = convertTemp(value, storedMetric, displayMetric) ?: return "—"
        return if (displayMetric) "$shown° C" else "$shown° F"
    }

    /** Compact widget / cell form without the unit letter: "18°". */
    fun temperatureDegrees(value: Int?, storedMetric: Boolean, displayMetric: Boolean): String {
        val shown = convertTemp(value, storedMetric, displayMetric) ?: return "—"
        return "$shown°"
    }

    fun toFahrenheit(value: Int?, storedMetric: Boolean): Int? =
        convertTemp(value, storedMetric, displayMetric = false)

    fun wind(value: Int?, storedMetric: Boolean, displayMetric: Boolean): String? {
        val shown = convertSpeed(value, storedMetric, displayMetric) ?: return null
        return if (displayMetric) "$shown km/h" else "$shown mph"
    }

    fun precipitation(stored: String?, storedMetric: Boolean, displayMetric: Boolean): String {
        if (stored.isNullOrBlank()) return ""
        if (storedMetric == displayMetric) return stored
        val amount = stored.substringBefore(' ').toDoubleOrNull() ?: return stored
        return if (displayMetric) {
            String.format(Locale.US, "%.1f mm", amount * 25.4)
        } else {
            String.format(Locale.US, "%.2f in", amount / 25.4)
        }
    }

    /** Current-conditions visibility. [stored] is a unitless number in the snapshot system. */
    fun visibility(stored: String?, storedMetric: Boolean, displayMetric: Boolean): String? {
        if (stored.isNullOrBlank()) return null
        val amount = stored.toDoubleOrNull()
            ?: return "$stored ${if (displayMetric) "km" else "mi"}"
        val shown = convertDistance(amount, storedMetric, displayMetric)
        return if (displayMetric) {
            String.format(Locale.US, "%.1f km", shown)
        } else {
            String.format(Locale.US, "%.2f mi", shown)
        }
    }

    fun visibilityAmount(value: Double?, storedMetric: Boolean, displayMetric: Boolean): String {
        if (value == null) return ""
        val shown = convertDistance(value, storedMetric, displayMetric)
        return if (displayMetric) {
            String.format(Locale.US, "%.1f km", shown)
        } else {
            String.format(Locale.US, "%.1f mi", shown)
        }
    }

    /** Qualitative visibility bands use miles. */
    fun toMiles(value: Double?, storedMetric: Boolean): Double? =
        if (value == null) null else convertDistance(value, storedMetric, displayMetric = false)

    fun tideHeight(value: Double?, storedMetric: Boolean, displayMetric: Boolean): String {
        if (value == null) return ""
        val shown = convertHeight(value, storedMetric, displayMetric)
        val unit = if (displayMetric) "m" else "ft"
        return String.format(Locale.US, "%.2f %s", shown, unit)
    }

    /** Tide-station distance. [miles] is always statute miles. */
    fun distanceFromMiles(miles: Double, displayMetric: Boolean): String =
        if (displayMetric) {
            String.format(Locale.US, "%.0f km", WeatherMath.milesToKm(miles))
        } else {
            String.format(Locale.US, "%.0f mi", miles)
        }

    /** Column title for a hazard distance that is stored in statute miles. */
    fun hazardDistanceHeader(displayMetric: Boolean): String =
        if (displayMetric) "km" else "Mi"

    /** Number only. The column header carries the unit. */
    fun hazardDistanceAmount(miles: Double, displayMetric: Boolean): String {
        val shown = if (displayMetric) WeatherMath.milesToKm(miles) else miles
        return String.format(Locale.US, "%.0f", shown)
    }

    /**
     * Explore-radius badge. [miles] is always statute miles.
     * [compact] is the chip form ("250m" in US, "402 km" in metric).
     */
    fun hazardRadiusLabel(miles: Int, displayMetric: Boolean, compact: Boolean = false): String {
        if (!displayMetric) {
            return when {
                compact -> "${miles}m"
                miles >= 1000 -> String.format(Locale.US, "%.1fk mi", miles / 1000.0)
                else -> "$miles mi"
            }
        }
        val km = WeatherMath.milesToKm(miles.toDouble()).roundToInt()
        return when {
            compact -> "$km km"
            km >= 1000 -> String.format(Locale.US, "%.1fk km", km / 1000.0)
            else -> "$km km"
        }
    }

    /** Settings → Map focus radius row. US keeps the word "miles". */
    fun hazardRadiusSettingsLabel(miles: Int, displayMetric: Boolean, isDefault: Boolean): String {
        val amount = if (displayMetric) {
            WeatherMath.milesToKm(miles.toDouble()).roundToInt()
        } else {
            miles
        }
        val unit = if (displayMetric) "km" else "miles"
        return if (isDefault) "$amount $unit (default)" else "$amount $unit"
    }

    /** Inclusive min/max for the custom radius field, in the unit the user types. */
    fun hazardRadiusEntryBounds(displayMetric: Boolean): IntRange =
        if (displayMetric) {
            WeatherMath.milesToKm(25.0).roundToInt()..WeatherMath.milesToKm(4000.0).roundToInt()
        } else {
            25..4000
        }

    /** Custom radius entry back into the miles value the maps and APIs use. */
    fun hazardRadiusEntryToMiles(entered: Int, displayMetric: Boolean): Int {
        val miles = if (displayMetric) {
            WeatherMath.kmToMiles(entered.toDouble()).roundToInt()
        } else {
            entered
        }
        return miles.coerceIn(25, 4000)
    }

    /** Rewrite "250 mi" phrases baked into hazard query summaries. */
    fun relabelMiles(text: String, displayMetric: Boolean): String {
        if (!displayMetric) return text
        return Regex("""(\d+(?:\.\d+)?) mi\b""").replace(text) { match ->
            val miles = match.groupValues[1].toDouble()
            "${WeatherMath.milesToKm(miles).roundToInt()} km"
        }
    }

    /** Location header suffix. [feet] is always feet. */
    fun elevationSuffix(feet: Int?, displayMetric: Boolean): String {
        if (feet == null) return ""
        return if (!displayMetric) {
            " - $feet ft"
        } else {
            " - ${WeatherMath.feetToMeters(feet.toDouble()).roundToInt()} m"
        }
    }

    fun barometer(inHg: String?, mb: String?, displayMetric: Boolean): String? {
        val inches = inHg?.takeIf { it.isNotBlank() }?.let { "$it in Hg" }
        val metric = mb?.takeIf { it.isNotBlank() }?.let { value ->
            if (displayMetric) "$value hPa" else "$value mb"
        }
        val primary = if (displayMetric) metric else inches
        val secondary = if (displayMetric) inches else metric
        return when {
            primary == null -> secondary
            secondary == null -> primary
            else -> "$primary ($secondary)"
        }
    }

    private fun convertTemp(value: Int?, storedMetric: Boolean, displayMetric: Boolean): Int? {
        if (value == null) return null
        if (storedMetric == displayMetric) return value
        return if (displayMetric) {
            WeatherMath.fahrenheitToC(value.toDouble())
        } else {
            WeatherMath.celsiusToF(value.toDouble())
        }
    }

    private fun convertSpeed(value: Int?, storedMetric: Boolean, displayMetric: Boolean): Int? {
        if (value == null) return null
        if (storedMetric == displayMetric) return value
        return if (displayMetric) {
            WeatherMath.mphToKmh(value.toDouble())
        } else {
            WeatherMath.kmhToMph(value.toDouble())
        }
    }

    private fun convertDistance(value: Double, storedMetric: Boolean, displayMetric: Boolean): Double {
        if (storedMetric == displayMetric) return value
        return if (displayMetric) WeatherMath.milesToKm(value) else WeatherMath.kmToMiles(value)
    }

    private fun convertHeight(value: Double, storedMetric: Boolean, displayMetric: Boolean): Double {
        if (storedMetric == displayMetric) return value
        return if (displayMetric) WeatherMath.feetToMeters(value) else value / 0.3048
    }
}
