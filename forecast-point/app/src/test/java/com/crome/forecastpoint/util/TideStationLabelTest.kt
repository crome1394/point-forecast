package com.crome.forecastpoint.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TideStationLabelTest {
    @Test
    fun formatsCoordinatesAndStationCode() {
        assertEquals(
            "26.6128, -80.0342 · Code 8722670",
            TideStationLabel.detailLine("8722670", 26.6127778, -80.0341667),
        )
    }

    @Test
    fun omitsCodeWhenBlank() {
        assertEquals(
            "26.6128, -80.0342",
            TideStationLabel.detailLine(" ", 26.6127778, -80.0341667),
        )
    }

    @Test
    fun returnsNullWithoutCoordinates() {
        assertNull(TideStationLabel.detailLine("8722670", null, -80.0341667))
        assertNull(TideStationLabel.detailLine(null, 26.6127778, null))
    }
}
