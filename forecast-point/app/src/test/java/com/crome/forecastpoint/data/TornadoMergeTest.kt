package com.crome.forecastpoint.data

import com.crome.forecastpoint.data.SevereWeatherService.TornadoReport
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Rows from the real 2025-10-18 SPC daily file and 2025_torn.csv. */
class TornadoMergeTest {
    private fun report(
        id: String,
        utc: String,
        lat: Double,
        lon: Double,
        scale: String,
        location: String,
    ) = TornadoReport(
        id = id,
        timeLabel = "",
        epochMs = Instant.parse(utc).toEpochMilli(),
        fScale = scale,
        location = location,
        county = "",
        state = "AR",
        latitude = lat,
        longitude = lon,
        comments = "",
        distanceMiles = 0.0,
    )

    // Daily: 2056 UTC, "3 ENE Degray Lake State", UNK
    private val dailyDegray = report("d1", "2025-10-18T20:56:00Z", 34.28, -93.10, "UNK", "3 ENE Degray Lake State")
    // WCM: 14:50 CST = 20:50 UTC, EF1 (mag 1), start 34.2756,-93.1016
    private val wcmDegray = report("wcm1", "2025-10-18T20:50:00Z", 34.2756, -93.1016, "EF1", "34.28°N, 93.10°W")

    @Test
    fun sameTornadoInBothSourcesIsListedOnce() {
        val merged = SevereWeatherService.mergeDailyAndWcm(listOf(dailyDegray), listOf(wcmDegray))
        assertEquals(1, merged.size)
        // Daily place name kept, official WCM rating filled in
        assertEquals("3 ENE Degray Lake State", merged[0].location)
        assertEquals("EF1", merged[0].fScale)
    }

    @Test
    fun dailyRatingIsKeptWhenAlreadyKnown() {
        val rated = dailyDegray.copy(fScale = "EF2")
        val merged = SevereWeatherService.mergeDailyAndWcm(listOf(rated), listOf(wcmDegray))
        assertEquals("EF2", merged.single().fScale)
    }

    @Test
    fun differentTimeOrPlaceIsNotMerged() {
        val hourLater = wcmDegray.copy(id = "wcm2", epochMs = wcmDegray.epochMs + 60 * 60_000L)
        val farAway = wcmDegray.copy(id = "wcm3", latitude = 34.60) // ~22 mi north
        val merged = SevereWeatherService.mergeDailyAndWcm(listOf(dailyDegray), listOf(hourLater, farAway))
        assertEquals(3, merged.size)
    }

    @Test
    fun pairingIsOneToOne() {
        // Two WCM tornadoes near one daily report: the nearer one pairs even when
        // the farther one comes first in the list.
        val second = wcmDegray.copy(id = "wcm4", latitude = 34.30, longitude = -93.06)
        val merged = SevereWeatherService.mergeDailyAndWcm(listOf(dailyDegray), listOf(second, wcmDegray))
        assertEquals(2, merged.size)
        assertEquals(setOf("d1", "wcm4"), merged.map { it.id }.toSet())
    }

    @Test
    fun wcmOnlyAndDailyOnlyRecordsAreKept() {
        assertEquals(1, SevereWeatherService.mergeDailyAndWcm(emptyList(), listOf(wcmDegray)).size)
        assertEquals(1, SevereWeatherService.mergeDailyAndWcm(listOf(dailyDegray), emptyList()).size)
    }
}
