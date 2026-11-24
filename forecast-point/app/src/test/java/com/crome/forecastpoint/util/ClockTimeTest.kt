package com.crome.forecastpoint.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTimeTest {
    @Test
    fun convertsAmPmToDevice24HourSetting() {
        assertEquals("07:02", ClockTime.format("7:02 AM", use24Hour = true))
        assertEquals("17:48", ClockTime.format("5:48 PM", use24Hour = true))
        assertEquals("00:00", ClockTime.format("12:00 AM", use24Hour = true))
        assertEquals("12:00", ClockTime.format("12:00 PM", use24Hour = true))
    }

    @Test
    fun keepsTwelveHourSetting() {
        assertEquals("7:02 AM", ClockTime.format("7:02 AM", use24Hour = false))
        assertEquals("5:48 PM", ClockTime.format("5:48 PM", use24Hour = false))
    }

    @Test
    fun acceptsAlreadyFormattedOrMissingTimes() {
        assertEquals("18:42", ClockTime.format("18:42", use24Hour = true))
        assertEquals("6:42 PM", ClockTime.format("18:42", use24Hour = false))
        assertEquals("—", ClockTime.format("—", use24Hour = true))
        assertEquals(null, ClockTime.format(null, use24Hour = true))
    }
}
