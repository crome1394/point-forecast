package com.crome.forecastpoint.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpcReportCsvTest {
    @Test
    fun reportAtFourOhFourIsKept() {
        // Real row from 260610_rpts_filtered_torn.csv; the 0404 time once made
        // the whole day's file look like an HTTP 404 page.
        val body = "Time,F_Scale,Location,County,State,Lat,Lon,Comments\n" +
            "0404,UNK,1 E Capital Airport,Sangamon,IL,39.84,-89.67,EF-1 (ILX)\n"
        assertTrue(SevereWeatherService.isSpcReportCsv(body))
    }

    @Test
    fun headerOnlyFileIsValid() {
        assertTrue(SevereWeatherService.isSpcReportCsv("Time,F_Scale,Location,County,State,Lat,Lon,Comments\n"))
    }

    @Test
    fun htmlErrorPageAndMissingBodyAreRejected() {
        assertFalse(SevereWeatherService.isSpcReportCsv("<!DOCTYPE html><title>404 Not Found</title>"))
        assertFalse(SevereWeatherService.isSpcReportCsv(""))
        assertFalse(SevereWeatherService.isSpcReportCsv(null))
    }
}
