package com.crome.forecastpoint.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

class SpcReportTimeTest {
    private fun fileDay(y: Int, m: Int, d: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m - 1, d, 12, 0, 0)
        }

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    @Test
    fun afternoonTimeStaysOnFileDate() {
        // 260610 file, Lomira WI at 2001 UTC
        assertEquals("2026-06-10T20:01:00Z", iso(SevereWeatherService.spcDailyReportEpochMs(fileDay(2026, 6, 10), "2001")))
    }

    @Test
    fun earlyUtcTimeRollsToNextDate() {
        // 260610 file, Capital Airport IL at 0404 UTC = June 10, 11:04 pm CDT
        assertEquals("2026-06-11T04:04:00Z", iso(SevereWeatherService.spcDailyReportEpochMs(fileDay(2026, 6, 10), "0404")))
        assertEquals("2026-06-11T11:59:00Z", iso(SevereWeatherService.spcDailyReportEpochMs(fileDay(2026, 6, 10), "1159")))
        assertEquals("2026-06-10T12:00:00Z", iso(SevereWeatherService.spcDailyReportEpochMs(fileDay(2026, 6, 10), "1200")))
    }

    @Test
    fun rolloverCrossesMonthAndYear() {
        assertEquals("2027-01-01T00:30:00Z", iso(SevereWeatherService.spcDailyReportEpochMs(fileDay(2026, 12, 31), "0030")))
    }

    @Test
    fun wcmCstIsSixHoursBehindUtc() {
        // 2025 WCM row: 2025-06-02 17:14:00, tz=3 (CST)
        assertEquals("2025-06-02T23:14:00Z", iso(SevereWeatherService.wcmEpochMs(2025, 6, 2, 17, 14, "3")))
        // Late-evening CST crosses into the next UTC day
        assertEquals("2025-06-03T04:30:00Z", iso(SevereWeatherService.wcmEpochMs(2025, 6, 2, 22, 30, "3")))
    }

    @Test
    fun wcmGmtRowIsUtc() {
        assertEquals("2025-06-02T17:14:00Z", iso(SevereWeatherService.wcmEpochMs(2025, 6, 2, 17, 14, "9")))
    }
}
