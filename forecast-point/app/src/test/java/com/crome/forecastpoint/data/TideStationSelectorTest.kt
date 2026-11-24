package com.crome.forecastpoint.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TideStationSelectorTest {
    private val lantanaLat = 26.5848450388814
    private val lantanaLon = -80.03649735753257

    private fun candidate(
        id: String,
        lat: Double,
        lon: Double,
        kind: TideStationSelector.Kind = TideStationSelector.Kind.TidePrediction,
    ) = TideStationSelector.Candidate(
        id = id,
        name = id,
        lat = lat,
        lon = lon,
        kind = kind,
    )

    @Test
    fun picksNearestTidePredictionStation() {
        val northPalmBeach = candidate("8722557", 26.8267, -80.055)
        val lakeWorthPier = candidate("8722670", 26.6127778, -80.0341667)
        val boyntonBeach = candidate("8722706", 26.5483, -80.0533)

        val picked = TideStationSelector.pick(
            listOf(northPalmBeach, boyntonBeach, lakeWorthPier),
            lantanaLat,
            lantanaLon,
        )

        assertEquals("8722670", picked?.id)
    }

    @Test
    fun tidePredictionWinsOverCloserWaterLevelGauge() {
        val tide = candidate("tide-8mi", 26.6127778, -80.0341667)
        val water = candidate(
            "water-2mi",
            26.5848 + 0.02,
            -80.0365,
            TideStationSelector.Kind.WaterLevel,
        )

        val picked = TideStationSelector.pick(listOf(water, tide), lantanaLat, lantanaLon)

        assertEquals("tide-8mi", picked?.id)
    }

    @Test
    fun fallsBackToWaterLevelWhenNoTideStationInRange() {
        val water = candidate(
            "water",
            26.6,
            -80.04,
            TideStationSelector.Kind.WaterLevel,
        )
        val usgs = candidate(
            "usgs",
            26.585,
            -80.037,
            TideStationSelector.Kind.UsgsGage,
        )

        val picked = TideStationSelector.pick(listOf(water, usgs), lantanaLat, lantanaLon)

        assertEquals("water", picked?.id)
    }

    @Test
    fun returnsNullWhenNothingIsWithinRange() {
        val far = candidate("far", 39.0, -81.7)

        assertNull(TideStationSelector.pick(listOf(far), lantanaLat, lantanaLon))
    }

    @Test
    fun distanceIsHaversineMiles() {
        // Lake Worth Pier is about 2.1 miles north of Lantana Public Beach.
        val distance = TideStationSelector.distanceMiles(
            lantanaLat,
            lantanaLon,
            26.6127778,
            -80.0341667,
        )

        assertTrue(distance > 1.9 && distance < 2.3)
        assertEquals(0.0, TideStationSelector.distanceMiles(10.0, 20.0, 10.0, 20.0), 0.0001)
    }
}
