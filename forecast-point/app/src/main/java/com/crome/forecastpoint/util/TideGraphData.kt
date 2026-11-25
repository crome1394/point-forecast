package com.crome.forecastpoint.util

import java.util.Calendar
import java.util.TimeZone

/** Pure chart data for the tide diagram (heights are feet above the station datum). */
object TideGraphData {
    data class Point(
        val hourOfDay: Int,
        val epochHour: Long,
        val heightFt: Double,
    )

    data class Extreme(
        val epochHour: Long,
        val heightFt: Double,
        val type: String,
    )

    data class Day(
        val offset: Int,
        val points: List<Point>,
        val extremes: List<Extreme>,
        val minFt: Double?,
        val maxFt: Double?,
    )

    /** Today's turning points, split by type for a compact two-column table. */
    data class TodayTideEvents(
        val lows: List<Extreme>,
        val highs: List<Extreme>,
    ) {
        fun rows(): List<TideTableRow> = tideTableRows(lows + highs)
    }

    data class TideTableRow(
        val low: Extreme?,
        val high: Extreme?,
    )

    fun tideTableRows(extremes: List<Extreme>): List<TideTableRow> {
        val rows = ArrayList<TideTableRow>()
        extremes.sortedBy { it.epochHour }.forEach { extreme ->
            if (extreme.type == "LOW") {
                rows += TideTableRow(low = extreme, high = null)
            } else {
                val last = rows.lastOrNull()
                if (last != null && last.low != null && last.high == null) {
                    rows[rows.lastIndex] = last.copy(high = extreme)
                } else {
                    rows += TideTableRow(low = null, high = extreme)
                }
            }
        }
        return rows
    }

    fun splitExtremes(extremes: List<Extreme>): TodayTideEvents = TodayTideEvents(
        lows = extremes.filter { it.type == "LOW" },
        highs = extremes.filter { it.type == "HIGH" },
    )

    fun todayEvents(
        heights: Map<Long, Double>,
        timeZoneId: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TodayTideEvents {
        val today = groupByLocalDay(
            heights = heights,
            timeZoneId = timeZoneId,
            dayOffsets = listOf(0),
            nowEpochMs = nowEpochMs,
        ).first()
        val split = splitExtremes(today.extremes)
        return TodayTideEvents(
            lows = split.lows.take(2),
            highs = split.highs.take(2),
        )
    }

    /** Next turning point of each type at or after [nowEpochSec]. */
    data class NextTideEvents(
        val low: Extreme?,
        val high: Extreme?,
    ) {
        /** Left-to-right widget order, regardless of which type comes first. */
        fun ordered(): List<Extreme> = listOfNotNull(low, high).sortedBy { it.epochHour }
    }

    fun nextEvents(heights: Map<Long, Double>, nowEpochSec: Long): NextTideEvents {
        val upcoming = extrema(heights).filter { it.epochHour >= nowEpochSec }
        return NextTideEvents(
            low = upcoming.firstOrNull { it.type == "LOW" },
            high = upcoming.firstOrNull { it.type == "HIGH" },
        )
    }

    /** Fractional local hour for drawing a "now" marker on a 24-hour chart. */
    fun localHourFraction(epochMs: Long, timeZoneId: String?): Float {
        val tz = timeZoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        val cal = Calendar.getInstance(tz).apply { timeInMillis = epochMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60f +
            cal.get(Calendar.SECOND) / 3600f
        return hour.coerceIn(0f, 23f)
    }

    /**
     * Split an epoch-hour → height map into forecast-location calendar days.
     * Uses the location's zone, not the device zone, like the Sun/Moon screen.
     */
    fun groupByLocalDay(
        heights: Map<Long, Double>,
        timeZoneId: String?,
        dayOffsets: Iterable<Int>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<Day> {
        val tz = timeZoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        val anchor = Calendar.getInstance(tz).apply {
            timeInMillis = nowEpochMs
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sorted = heights.toSortedMap()
        val local = Calendar.getInstance(tz)

        return dayOffsets.map { offset ->
            val day = (anchor.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val year = day.get(Calendar.YEAR)
            val month = day.get(Calendar.MONTH)
            val date = day.get(Calendar.DAY_OF_MONTH)
            val points = sorted.entries.mapNotNull { (epochHour, heightFt) ->
                local.timeInMillis = epochHour * 1000L
                if (local.get(Calendar.YEAR) == year &&
                    local.get(Calendar.MONTH) == month &&
                    local.get(Calendar.DAY_OF_MONTH) == date
                ) {
                    Point(
                        hourOfDay = local.get(Calendar.HOUR_OF_DAY),
                        epochHour = epochHour,
                        heightFt = heightFt,
                    )
                } else {
                    null
                }
            }
            val hours = points.map { it.epochHour }.toSet()
            Day(
                offset = offset,
                points = points,
                extremes = TideGraphData.extrema(sorted).filter { it.epochHour in hours },
                minFt = points.minOfOrNull { it.heightFt },
                maxFt = points.maxOfOrNull { it.heightFt },
            )
        }
    }

    /** Interior turning points of the hourly curve (no endpoints, no flat runs). */
    fun extrema(heights: Map<Long, Double>): List<Extreme> {
        val sorted = heights.toSortedMap()
        if (sorted.size < 3) return emptyList()
        val entries = sorted.entries.toList()
        val out = ArrayList<Extreme>()
        for (i in 1 until entries.lastIndex) {
            val prev = entries[i - 1].value
            val cur = entries[i].value
            val next = entries[i + 1].value
            val type = when {
                cur > prev && cur > next -> "HIGH"
                cur < prev && cur < next -> "LOW"
                else -> continue
            }
            out += Extreme(
                epochHour = entries[i].key,
                heightFt = cur,
                type = type,
            )
        }
        return out
    }

    /** Rising / Falling / Steady vs the adjacent hourly samples. */
    fun trendAt(heights: Map<Long, Double>, epochHour: Long): String {
        val cur = heights[epochHour] ?: return "—"
        val prev = heights[epochHour - 3600L] ?: return "Steady"
        val delta = cur - prev
        return when {
            delta > 0.01 -> "Rising"
            delta < -0.01 -> "Falling"
            else -> "Steady"
        }
    }
}
