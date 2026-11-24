package com.crome.forecastpoint.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearest usable gauge for a point, with source priority:
 * coastal tide predictions, then CO-OPS water levels, then USGS stage.
 */
object TideStationSelector {
    enum class Kind { TidePrediction, WaterLevel, UsgsGage }

    data class Candidate(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val kind: Kind,
    )

    fun pick(candidates: List<Candidate>, lat: Double, lon: Double): Candidate? =
        listOf(
            Kind.TidePrediction to MAX_TIDE_PRED_MILES,
            Kind.WaterLevel to MAX_COOPS_WATER_MILES,
            Kind.UsgsGage to MAX_USGS_MILES,
        ).firstNotNullOfOrNull { (kind, maxMiles) ->
            candidates
                .asSequence()
                .filter { it.kind == kind }
                .filter { distanceMiles(lat, lon, it.lat, it.lon) <= maxMiles }
                .minByOrNull { distanceMiles(lat, lon, it.lat, it.lon) }
        }

    fun distanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).let { it * it }
        val c = 2 * asin(min(1.0, sqrt(a)))
        return r * c
    }

    /** Ocean/estuary harmonic tide predictions. */
    const val MAX_TIDE_PRED_MILES = 120.0
    /** Great Lakes / CO-OPS water-level gauges. */
    const val MAX_COOPS_WATER_MILES = 100.0
    /** USGS stream/lake stage for interior rivers and lakes. */
    const val MAX_USGS_MILES = 50.0
}
