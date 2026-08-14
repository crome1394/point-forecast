package com.crome.forecastpoint.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Local sun & moon times / phase for a geographic point.
 * Approximations suitable for UI (not navigation-grade ephemeris).
 */
object CelestialCalculator {

    data class SunDay(
        val sunriseHours: Double,
        val sunsetHours: Double,
        val solarNoonHours: Double,
        val daylightHours: Double,
        val civilDawnHours: Double,
        val civilDuskHours: Double,
        val sunrise: String,
        val sunset: String,
        val solarNoon: String,
        val civilDawn: String,
        val civilDusk: String,
        val daylightLabel: String,
        /** Solar altitude degrees for hours 0..24 (25 samples). */
        val altitudeByHour: List<Float>,
    )

    data class MoonDay(
        val moonriseHours: Double?,
        val moonsetHours: Double?,
        val moonrise: String,
        val moonset: String,
        val phaseName: String,
        val illuminationPct: Int,
        val ageDays: Double,
        val isWaxing: Boolean,
        /** Approximate moon altitude degrees for hours 0..24. */
        val altitudeByHour: List<Float>,
    )

    fun sunDay(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
        day: Calendar = Calendar.getInstance(timeZone),
    ): SunDay {
        val rise = sunEventHours(latitude, longitude, timeZone, day, rise = true, zenith = 90.833)
        val set = sunEventHours(latitude, longitude, timeZone, day, rise = false, zenith = 90.833)
        val dawn = sunEventHours(latitude, longitude, timeZone, day, rise = true, zenith = 96.0)
        val dusk = sunEventHours(latitude, longitude, timeZone, day, rise = false, zenith = 96.0)
        val noon = if (!rise.isNaN() && !set.isNaN()) (rise + set) / 2.0
        else solarNoonHours(longitude, timeZone, day)
        val daylight = if (!rise.isNaN() && !set.isNaN()) {
            var d = set - rise
            if (d < 0) d += 24.0
            d
        } else {
            Double.NaN
        }
        val alts = (0..24).map { h ->
            solarAltitudeDeg(latitude, longitude, timeZone, day, h.toDouble()).toFloat()
        }
        return SunDay(
            sunriseHours = rise,
            sunsetHours = set,
            solarNoonHours = noon,
            daylightHours = daylight,
            civilDawnHours = dawn,
            civilDuskHours = dusk,
            sunrise = formatTime(rise),
            sunset = formatTime(set),
            solarNoon = formatTime(noon),
            civilDawn = formatTime(dawn),
            civilDusk = formatTime(dusk),
            daylightLabel = formatDuration(daylight),
            altitudeByHour = alts,
        )
    }

    fun moonDay(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
        day: Calendar = Calendar.getInstance(timeZone),
        /**
         * When true, phase/illumination use the current instant (best for “today”).
         * When false, use local noon on [day] (for past/future day pages).
         */
        useCurrentInstantForPhase: Boolean = false,
    ): MoonDay {
        val phaseJd = if (useCurrentInstantForPhase) {
            System.currentTimeMillis() / 86400000.0 + 2440587.5
        } else {
            julianDayAtLocalMidnight(day, timeZone) + 0.5
        }
        val phase = moonPhase(phaseJd)
        val (rise, set) = moonRiseSet(latitude, longitude, timeZone, day)
        val alts = (0..24).map { h ->
            moonAltitudeDeg(latitude, longitude, timeZone, day, h.toDouble()).toFloat()
        }
        // Nearest percent (not truncate) — near new moon 2.6% was showing as “2%”
        val pct = kotlin.math.round(phase.illumination * 100.0).toInt().coerceIn(0, 100)
        return MoonDay(
            moonriseHours = rise,
            moonsetHours = set,
            moonrise = formatTime(rise ?: Double.NaN),
            moonset = formatTime(set ?: Double.NaN),
            phaseName = phase.name,
            illuminationPct = pct,
            ageDays = phase.ageDays,
            isWaxing = phase.isWaxing,
            altitudeByHour = alts,
        )
    }

    /** True if local wall time is between sunrise and sunset (daytime icon). */
    fun isDaytime(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
        now: Calendar = Calendar.getInstance(timeZone),
    ): Boolean {
        val sun = sunDay(latitude, longitude, timeZone, now)
        if (sun.sunriseHours.isNaN() || sun.sunsetHours.isNaN()) {
            val h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0
            return h in 6.0..18.0
        }
        val h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0
        return if (sun.sunriseHours < sun.sunsetHours) {
            h >= sun.sunriseHours && h < sun.sunsetHours
        } else {
            // polar edge cases / wrap
            h >= sun.sunriseHours || h < sun.sunsetHours
        }
    }

    // —— Sun (NOAA-style) ——

    private fun sunEventHours(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        day: Calendar,
        rise: Boolean,
        zenith: Double,
    ): Double {
        val year = day.get(Calendar.YEAR)
        val month = day.get(Calendar.MONTH) + 1
        val dayOfMonth = day.get(Calendar.DAY_OF_MONTH)
        val n = dayOfYear(year, month, dayOfMonth)
        val lngHour = longitude / 15.0
        val t = n + ((if (rise) 6.0 else 18.0) - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(rad(m))) + (0.020 * sin(rad(2 * m))) + 282.634
        l = normalize360(l)
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = normalize360(ra)
        val lQuad = floor(l / 90.0) * 90.0
        val raQuad = floor(ra / 90.0) * 90.0
        ra = (ra + (lQuad - raQuad)) / 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(zenith)) - (sinDec * sin(rad(latitude)))) /
            (cosDec * cos(rad(latitude)))
        if (cosH > 1) return Double.NaN
        if (cosH < -1) return Double.NaN
        var h = if (rise) 360.0 - deg(acos(cosH)) else deg(acos(cosH))
        h /= 15.0
        val tLocal = h + ra - (0.06571 * t) - 6.622
        var ut = normalize24(tLocal - lngHour)
        return utToLocalHours(ut, year, month, dayOfMonth, timeZone)
    }

    private fun solarNoonHours(longitude: Double, timeZone: TimeZone, day: Calendar): Double {
        val year = day.get(Calendar.YEAR)
        val month = day.get(Calendar.MONTH) + 1
        val dayOfMonth = day.get(Calendar.DAY_OF_MONTH)
        val n = dayOfYear(year, month, dayOfMonth).toDouble()
        val t = n + (12.0 - longitude / 15.0) / 24.0
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(rad(m))) + (0.020 * sin(rad(2 * m))) + 282.634
        l = normalize360(l)
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = normalize360(ra)
        val lQuad = floor(l / 90.0) * 90.0
        val raQuad = floor(ra / 90.0) * 90.0
        ra = (ra + (lQuad - raQuad)) / 15.0
        val tLocal = ra - (0.06571 * t) - 6.622 + 12.0
        var ut = normalize24(tLocal - longitude / 15.0)
        return utToLocalHours(ut, year, month, dayOfMonth, timeZone)
    }

    private fun solarAltitudeDeg(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        day: Calendar,
        localHour: Double,
    ): Double {
        val jd = julianDayAtLocal(day, timeZone, localHour)
        val t = (jd - 2451545.0) / 36525.0
        val l0 = normalize360(280.46646 + 36000.76983 * t)
        val m = rad(normalize360(357.52911 + 35999.05029 * t))
        val c = (1.914602 - 0.004817 * t) * sin(m) + 0.019993 * sin(2 * m)
        val tl = rad(normalize360(l0 + c))
        val eps = rad(23.439 - 0.0000004 * t)
        val dec = asin(sin(eps) * sin(tl))
        val ra = atan2(cos(eps) * sin(tl), cos(tl))
        val gmst = normalize360(280.46061837 + 360.98564736629 * (jd - 2451545.0))
        val lst = rad(normalize360(gmst + longitude))
        val ha = lst - ra
        val lat = rad(latitude)
        val alt = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha))
        return deg(alt)
    }

    // —— Moon ——

    private data class PhaseInfo(
        val name: String,
        val illumination: Double,
        val ageDays: Double,
        val isWaxing: Boolean,
    )

    /**
     * Illuminated fraction from Meeus-style phase angle (Astronomical Algorithms).
     * Matches common sky apps within a couple of percent; near new moon small
     * differences (e.g. 3% vs 4–7%) are normal across sources and definitions.
     */
    private fun moonPhase(jd: Double): PhaseInfo {
        val t = (jd - 2451545.0) / 36525.0
        val d = normalize360(297.8501921 + 445267.1114034 * t) // mean elongation °
        val m = normalize360(357.5291092 + 35999.0502909 * t) // sun mean anomaly °
        val mp = normalize360(134.9633964 + 477198.8675055 * t) // moon mean anomaly °

        // Phase angle i (°) — ~180° at new moon, ~0° at full
        var i = 180.0 - d -
            6.289 * sin(rad(mp)) +
            2.100 * sin(rad(m)) -
            1.274 * sin(rad(2 * d - mp)) -
            0.658 * sin(rad(2 * d)) -
            0.214 * sin(rad(2 * mp)) -
            0.110 * sin(rad(d))
        i = abs(i % 360.0)
        if (i > 180.0) i = 360.0 - i

        // Illuminated fraction of the disc (0 = new, 1 = full)
        val illum = ((1.0 + cos(rad(i))) / 2.0).coerceIn(0.0, 1.0)

        val synodic = 29.530588853
        // Age from mean elongation (0 at mean new moon)
        val age = synodic * d / 360.0
        val isWaxing = d < 180.0
        val name = when {
            age < 1.84566 || age >= 27.68493 -> "New moon"
            age < 5.53699 -> "Waxing crescent"
            age < 9.22831 -> "First quarter"
            age < 12.91963 -> "Waxing gibbous"
            age < 16.61096 -> "Full moon"
            age < 20.30228 -> "Waning gibbous"
            age < 23.99361 -> "Last quarter"
            else -> "Waning crescent"
        }
        return PhaseInfo(name, illum, age, isWaxing)
    }

    /**
     * Approximate moonrise/moonset by scanning altitudes for the local day.
     */
    private fun moonRiseSet(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        day: Calendar,
    ): Pair<Double?, Double?> {
        var rise: Double? = null
        var set: Double? = null
        var prev = moonAltitudeDeg(latitude, longitude, timeZone, day, 0.0)
        var h = 0.25
        while (h <= 24.0) {
            val alt = moonAltitudeDeg(latitude, longitude, timeZone, day, h)
            // horizon ~ 0° (ignore refraction for simplicity)
            if (prev < 0 && alt >= 0 && rise == null) {
                rise = h - 0.25 * (alt / (alt - prev + 1e-9)).coerceIn(0.0, 1.0)
            }
            if (prev >= 0 && alt < 0 && set == null) {
                set = h - 0.25 * ((-alt) / (prev - alt + 1e-9)).coerceIn(0.0, 1.0)
            }
            prev = alt
            h += 0.25
        }
        return rise to set
    }

    private fun moonAltitudeDeg(
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone,
        day: Calendar,
        localHour: Double,
    ): Double {
        val jd = julianDayAtLocal(day, timeZone, localHour)
        val t = (jd - 2451545.0) / 36525.0
        // Mean longitude, anomaly, etc. (simplified)
        val lp = rad(normalize360(218.3164477 + 481267.88123421 * t))
        val d = rad(normalize360(297.8501921 + 445267.1114034 * t))
        val m = rad(normalize360(357.5291092 + 35999.0502909 * t))
        val mp = rad(normalize360(134.9633964 + 477198.8675055 * t))
        val f = rad(normalize360(93.2720950 + 483202.0175233 * t))
        val lon = lp + rad(
            6.289 * sin(mp) +
                1.274 * sin(2 * d - mp) +
                0.658 * sin(2 * d) +
                0.214 * sin(2 * mp) -
                0.186 * sin(m) -
                0.114 * sin(2 * f),
        )
        val latM = rad(
            5.128 * sin(f) +
                0.281 * sin(mp + f) +
                0.278 * sin(mp - f) +
                0.173 * sin(2 * d - f),
        )
        val eps = rad(23.439 - 0.0000004 * t)
        val x = cos(latM) * cos(lon)
        val y = cos(eps) * cos(latM) * sin(lon) - sin(eps) * sin(latM)
        val z = sin(eps) * cos(latM) * sin(lon) + cos(eps) * sin(latM)
        val ra = atan2(y, x)
        val dec = asin(z)
        val gmst = normalize360(280.46061837 + 360.98564736629 * (jd - 2451545.0))
        val lst = rad(normalize360(gmst + longitude))
        val ha = lst - ra
        val lat = rad(latitude)
        val alt = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha))
        return deg(alt)
    }

    // —— Time helpers ——

    private fun julianDayAtLocalMidnight(day: Calendar, timeZone: TimeZone): Double {
        return julianDayAtLocal(day, timeZone, 0.0)
    }

    private fun julianDayAtLocal(day: Calendar, timeZone: TimeZone, localHour: Double): Double {
        val cal = day.clone() as Calendar
        cal.timeZone = timeZone
        val h = floor(localHour).toInt().coerceIn(0, 23)
        val m = ((localHour - h) * 60).toInt().coerceIn(0, 59)
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val ms = cal.timeInMillis
        return ms / 86400000.0 + 2440587.5
    }

    private fun utToLocalHours(
        ut: Double,
        year: Int,
        month: Int,
        dayOfMonth: Int,
        timeZone: TimeZone,
    ): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, floor(ut).toInt())
            set(Calendar.MINUTE, ((ut - floor(ut)) * 60).toInt())
            set(Calendar.SECOND, 0)
        }
        val local = Calendar.getInstance(timeZone).apply { timeInMillis = cal.timeInMillis }
        return local.get(Calendar.HOUR_OF_DAY) + local.get(Calendar.MINUTE) / 60.0
    }

    fun formatTime(hours: Double): String {
        if (hours.isNaN()) return "—"
        val h24 = normalize24(hours)
        val h = floor(h24).toInt()
        val m = ((h24 - h) * 60).toInt().coerceIn(0, 59)
        val ampm = if (h < 12) "AM" else "PM"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format(Locale.US, "%d:%02d %s", h12, m, ampm)
    }

    private fun formatDuration(hours: Double): String {
        if (hours.isNaN()) return "—"
        val h = floor(hours).toInt()
        val m = ((hours - h) * 60).toInt().coerceIn(0, 59)
        return String.format(Locale.US, "%d hr %02d min", h, m)
    }

    private fun dayOfYear(year: Int, month: Int, day: Int): Int {
        val n1 = floor(275.0 * month / 9.0).toInt()
        val n2 = floor((month + 9.0) / 12.0).toInt()
        val n3 = 1 + floor((year - 4.0 * floor(year / 4.0) + 2.0) / 3.0).toInt()
        return n1 - (n2 * n3) + day - 30
    }

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun normalize360(v: Double): Double {
        var x = v % 360.0
        if (x < 0) x += 360.0
        return x
    }

    private fun normalize24(v: Double): Double {
        var x = v % 24.0
        if (x < 0) x += 24.0
        return x
    }
}
