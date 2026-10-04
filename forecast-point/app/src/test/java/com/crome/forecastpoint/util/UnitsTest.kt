package com.crome.forecastpoint.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun nwsPeriodTempsMatchPublishedMetricValues() {
        // Chicago MapClick sample: US 52/69, metric 11/21.
        assertEquals(11, WeatherMath.fahrenheitToC(52.0))
        assertEquals(21, WeatherMath.fahrenheitToC(69.0))
        assertEquals("11° C", Units.temperature(52, storedMetric = false, displayMetric = true))
        assertEquals("21° C", Units.temperature(69, storedMetric = false, displayMetric = true))
        assertEquals("11° C", Units.temperature(11, storedMetric = true, displayMetric = true))
        assertEquals("52° F", Units.temperature(11, storedMetric = true, displayMetric = false))
    }

    @Test
    fun currentObservationConvertsFromFahrenheit() {
        // MapClick currentobservation stays in °F even when unit=1.
        assertEquals(18, WeatherMath.fahrenheitToC(64.0))
        assertEquals("18° C", Units.temperature(64, storedMetric = false, displayMetric = true))
        assertEquals("64° F", Units.temperature(64, storedMetric = false, displayMetric = false))
    }

    @Test
    fun windStaysPutWhenTheSnapshotAlreadyMatches() {
        assertEquals(13, WeatherMath.mphToKmh(8.0))
        assertEquals("13 km/h", Units.wind(8, storedMetric = false, displayMetric = true))
        assertEquals("8 mph", Units.wind(8, storedMetric = false, displayMetric = false))
        assertEquals("8 km/h", Units.wind(8, storedMetric = true, displayMetric = true))
    }

    @Test
    fun precipitationVisibilityTideElevationAndPressure() {
        assertEquals("3.0 mm", Units.precipitation("0.12 in", storedMetric = false, displayMetric = true))
        assertEquals("0.12 in", Units.precipitation("3.0 mm", storedMetric = true, displayMetric = false))
        assertEquals("0.12 in", Units.precipitation("0.12 in", storedMetric = false, displayMetric = false))
        assertEquals("16.1 km", Units.visibility("10.00", storedMetric = false, displayMetric = true))
        assertEquals("10.00 mi", Units.visibility("10.00", storedMetric = false, displayMetric = false))
        assertEquals("1.52 m", Units.tideHeight(5.0, storedMetric = false, displayMetric = true))
        assertEquals("5.00 ft", Units.tideHeight(5.0, storedMetric = false, displayMetric = false))
        assertEquals(" - 180 m", Units.elevationSuffix(591, displayMetric = true))
        assertEquals(" - 591 ft", Units.elevationSuffix(591, displayMetric = false))
        assertEquals(
            "1013.25 hPa (30.17 in Hg)",
            Units.barometer("30.17", "1013.25", displayMetric = true),
        )
        assertEquals(
            "30.17 in Hg (1013.25 mb)",
            Units.barometer("30.17", "1013.25", displayMetric = false),
        )
    }

    @Test
    fun hazardDistancesFollowTheDisplayUnit() {
        assertEquals("Mi", Units.hazardDistanceHeader(false))
        assertEquals("km", Units.hazardDistanceHeader(true))
        assertEquals("250", Units.hazardDistanceAmount(250.0, false))
        assertEquals("402", Units.hazardDistanceAmount(250.0, true))
        assertEquals("250 mi", Units.hazardRadiusLabel(250, false))
        assertEquals("402 km", Units.hazardRadiusLabel(250, true))
        assertEquals("250m", Units.hazardRadiusLabel(250, false, compact = true))
        assertEquals("402 km", Units.hazardRadiusLabel(250, true, compact = true))
        assertEquals("1.0k mi", Units.hazardRadiusLabel(1000, false))
        assertEquals("1.6k km", Units.hazardRadiusLabel(1000, true))
        assertEquals("250 miles (default)", Units.hazardRadiusSettingsLabel(250, false, true))
        assertEquals("402 km (default)", Units.hazardRadiusSettingsLabel(250, true, true))
        assertEquals(40..6437, Units.hazardRadiusEntryBounds(true))
        assertEquals(250, Units.hazardRadiusEntryToMiles(402, true))
        assertEquals(25, Units.hazardRadiusEntryToMiles(40, true))
        assertEquals(4000, Units.hazardRadiusEntryToMiles(6437, true))
        assertEquals(
            "USGS FDSN · focus 402 km · ≤402 km",
            Units.relabelMiles("USGS FDSN · focus 250 mi · ≤250 mi", true),
        )
    }

    @Test
    fun integerCelsiusRoundTripsThroughFahrenheit() {
        for (c in -40..45) {
            val f = WeatherMath.celsiusToF(c.toDouble())
            assertEquals(c, WeatherMath.fahrenheitToC(f.toDouble()))
        }
    }
}
