package com.crome.forecastpoint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.util.TimeZone

class TideGraphDataTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun series(vararg pairs: Pair<Long, Double>): Map<Long, Double> =
        linkedMapOf(*pairs)

    private fun hour(day: Int, hour: Int): Long =
        (day * 24L + hour) * 3600L

    @Test
    fun groupsHourlySamplesIntoLocalCalendarDays() {
        val heights = series(
            hour(0, 22) to 1.0,
            hour(0, 23) to 1.5,
            hour(1, 12) to 2.0,
            hour(1, 13) to 2.5,
            hour(2, 0) to 1.0,
        )

        val days = TideGraphData.groupByLocalDay(
            heights = heights,
            timeZoneId = utc.id,
            dayOffsets = 0..2,
            nowEpochMs = hour(0, 12) * 1000L,
        )

        assertEquals(3, days.size)
        assertEquals(0, days[0].offset)
        assertEquals(1.5, days[0].points.last().heightFt, 0.001)
        assertEquals(1, days[1].offset)
        assertEquals(2.0, days[1].points.first().heightFt, 0.001)
        assertEquals(12, days[1].points.first().hourOfDay)
        assertEquals(2, days[1].points.size)
    }

    @Test
    fun usesForecastLocationZoneNotDeviceZone() {
        val heights = series(
            hour(0, 5) to 0.3,
            hour(0, 6) to 0.5,
        )

        val days = TideGraphData.groupByLocalDay(
            heights = heights,
            timeZoneId = "America/New_York",
            dayOffsets = 0..0,
            nowEpochMs = hour(0, 12) * 1000L,
        )

        // 05:00 UTC = 00:00 EST on the same calendar day.
        assertEquals(0, days.single().points.first().hourOfDay)
    }

    @Test
    fun findsInteriorHighAndLowExtrema() {
        val heights = series(
            hour(0, 0) to 0.0,
            hour(0, 1) to 1.0,
            hour(0, 2) to 2.0,
            hour(0, 3) to 1.0,
            hour(0, 4) to 0.5,
            hour(0, 5) to 1.5,
        )

        val extremes = TideGraphData.extrema(heights)

        assertEquals(2, extremes.size)
        assertEquals("HIGH", extremes[0].type)
        assertEquals(hour(0, 2), extremes[0].epochHour)
        assertEquals("LOW", extremes[1].type)
        assertEquals(hour(0, 4), extremes[1].epochHour)
    }

    @Test
    fun ignoresFlatRunsWhenFindingExtrema() {
        val heights = series(
            hour(0, 0) to 1.0,
            hour(0, 1) to 2.0,
            hour(0, 2) to 2.0,
            hour(0, 3) to 3.0,
            hour(0, 4) to 1.0,
        )

        val extremes = TideGraphData.extrema(heights)

        assertEquals(1, extremes.size)
        assertEquals("HIGH", extremes.single().type)
    }

    @Test
    fun reportsTrendFromNeighbouringHours() {
        val heights = series(
            hour(0, 0) to 0.0,
            hour(0, 1) to 1.0,
            hour(0, 2) to 2.0,
            hour(0, 3) to 2.0,
            hour(0, 4) to 1.0,
        )

        assertEquals("Steady", TideGraphData.trendAt(heights, hour(0, 0)))
        assertEquals("Rising", TideGraphData.trendAt(heights, hour(0, 1)))
        assertEquals("Falling", TideGraphData.trendAt(heights, hour(0, 4)))
        assertEquals("—", TideGraphData.trendAt(heights, hour(0, 9)))
    }

    @Test
    fun dayCarriesMinMaxForChartScale() {
        val heights = series(
            hour(0, 0) to 1.0,
            hour(0, 1) to 3.5,
            hour(0, 2) to -0.4,
        )

        val day = TideGraphData.groupByLocalDay(
            heights = heights,
            timeZoneId = utc.id,
            dayOffsets = 0..0,
            nowEpochMs = hour(0, 12) * 1000L,
        ).single()

        assertEquals(-0.4, day.minFt!!, 0.001)
        assertEquals(3.5, day.maxFt!!, 0.001)
        assertTrue(day.points.map { it.hourOfDay } == listOf(0, 1, 2))
    }

    @Test
    fun localHourFractionUsesForecastZone() {
        val instant = OffsetDateTime.parse("2026-11-24T12:30:00Z").toInstant().toEpochMilli()

        assertEquals(12.5f, TideGraphData.localHourFraction(instant, "UTC"), 0.001f)
        assertEquals(7.5f, TideGraphData.localHourFraction(instant, "America/New_York"), 0.001f)
    }

    @Test
    fun splitExtremesSeparatesByTypeInTimeOrder() {
        val extremes = listOf(
            TideGraphData.Extreme(epochHour = 1L, heightFt = 2.0, type = "HIGH"),
            TideGraphData.Extreme(epochHour = 2L, heightFt = 0.5, type = "LOW"),
            TideGraphData.Extreme(epochHour = 3L, heightFt = 3.0, type = "HIGH"),
            TideGraphData.Extreme(epochHour = 4L, heightFt = 0.2, type = "LOW"),
        )

        val split = TideGraphData.splitExtremes(extremes)

        assertEquals(listOf(2L, 4L), split.lows.map { it.epochHour })
        assertEquals(listOf(1L, 3L), split.highs.map { it.epochHour })
    }

    @Test
    fun todayEventsPicksTwoLowsAndTwoHighsInTimeOrder() {
        val heights = series(
            hour(0, 0) to 1.0,
            hour(0, 1) to 2.0,
            hour(0, 2) to 1.0,
            hour(0, 3) to 0.0,
            hour(0, 4) to 1.0,
            hour(0, 5) to 3.0,
            hour(0, 6) to 1.0,
            hour(0, 7) to 0.5,
            hour(0, 8) to 2.0,
            hour(1, 0) to 2.0,
            hour(1, 1) to 4.0,
            hour(1, 2) to 0.0,
            hour(1, 3) to 2.0,
        )

        val events = TideGraphData.todayEvents(
            heights = heights,
            timeZoneId = utc.id,
            nowEpochMs = hour(0, 12) * 1000L,
        )

        assertEquals(listOf(hour(0, 3), hour(0, 7)), events.lows.map { it.epochHour })
        assertEquals(listOf(hour(0, 1), hour(0, 5)), events.highs.map { it.epochHour })
        assertEquals(listOf("LOW", "LOW"), events.lows.map { it.type })
        assertEquals(listOf("HIGH", "HIGH"), events.highs.map { it.type })
    }

    @Test
    fun todayEventsUsesForecastLocationZone() {
        val base = OffsetDateTime.parse("2026-11-24T00:00:00Z").toEpochSecond()
        val at = { hour: Int -> base + hour * 3600L }
        val heights = series(
            at(1) to 1.0,
            at(2) to 3.0,
            at(3) to 1.0,
            at(4) to 1.0,
            at(5) to 2.0,
            at(6) to 1.0,
            at(7) to 0.0,
            at(8) to 1.0,
        )

        val events = TideGraphData.todayEvents(
            heights = heights,
            timeZoneId = "America/New_York",
            nowEpochMs = at(12) * 1000L,
        )

        // 02:00 UTC is still the previous calendar day in New York.
        assertEquals(listOf(at(5)), events.highs.map { it.epochHour })
        assertEquals(listOf(at(7)), events.lows.map { it.epochHour })
    }

    @Test
    fun todayEventsIsEmptyWithoutSamples() {
        val events = TideGraphData.todayEvents(
            heights = emptyMap(),
            timeZoneId = utc.id,
            nowEpochMs = hour(0, 12) * 1000L,
        )

        assertTrue(events.lows.isEmpty())
        assertTrue(events.highs.isEmpty())
    }
}
