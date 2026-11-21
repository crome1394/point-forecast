package com.crome.forecastpoint.data

import com.crome.forecastpoint.util.LocationTimeZone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures trimmed from live api.weather.gov responses for Columbus OH (2026-11-20). */
class NwsApiFallbackTest {
    private val points = JSONObject(
        """{"properties":{"timeZone":"America/New_York",
            "relativeLocation":{"properties":{"city":"Columbus","state":"OH"}}}}""",
    )
    private val forecast = JSONObject(
        """{"properties":{"elevation":{"unitCode":"wmoUnit:m","value":235.0008},"periods":[
            {"name":"Tonight","startTime":"2026-11-20T18:00:00-05:00","isDaytime":false,
             "temperature":39,"probabilityOfPrecipitation":{"value":0},
             "icon":"https://api.weather.gov/icons/land/night/skc?size=medium",
             "shortForecast":"Clear","detailedForecast":"Clear, with a low around 39."},
            {"name":"Saturday","startTime":"2026-11-21T06:00:00-05:00","isDaytime":true,
             "temperature":64,"probabilityOfPrecipitation":{"value":null},
             "icon":"https://api.weather.gov/icons/land/day/few?size=medium",
             "shortForecast":"Sunny","detailedForecast":"Sunny, with a high near 64."}]}}""",
    )
    private val observation = JSONObject(
        """{"properties":{"timestamp":"2026-11-21T04:05:00+00:00","textDescription":"Clear",
            "icon":"https://api.weather.gov/icons/land/night/skc?size=medium",
            "temperature":{"value":9},"dewpoint":{"value":4},"windDirection":{"value":120},
            "windSpeed":{"value":9.252},"barometricPressure":{"value":102505.98},
            "seaLevelPressure":{"value":null},"visibility":{"value":16093.44},
            "relativeHumidity":{"value":70.89},"elevation":{"value":249},
            "stationName":"John Glenn Columbus International Airport","stationId":"KCMH"}}""",
    )

    @Test
    fun forecastBecomesMapClickShape() {
        val root = NwsApiFallback.mapClickRoot(points, forecast, observation)
        val loc = root.getJSONObject("location")
        assertEquals("Columbus OH", loc.getString("areaDescription"))
        assertEquals("771", loc.getString("elevation"))
        assertEquals("America/New_York", loc.getString("timezone"))

        val time = root.getJSONObject("time")
        assertEquals("Tonight", time.getJSONArray("startPeriodName").getString(0))
        assertEquals("Low", time.getJSONArray("tempLabel").getString(0))
        assertEquals("High", time.getJSONArray("tempLabel").getString(1))

        val data = root.getJSONObject("data")
        assertEquals("39", data.getJSONArray("temperature").getString(0))
        assertEquals("0", data.getJSONArray("pop").getString(0))
        assertEquals(true, data.getJSONArray("pop").isNull(1))
        assertEquals("Sunny, with a high near 64.", data.getJSONArray("text").getString(1))
        assertEquals(0, data.getJSONArray("hazard").length())
    }

    @Test
    fun observationConvertsToUsUnits() {
        val co = NwsApiFallback.mapClickRoot(points, forecast, observation)
            .getJSONObject("currentobservation")
        assertEquals("48", co.getString("Temp"))
        assertEquals("39", co.getString("Dewp"))
        assertEquals("71", co.getString("Relh"))
        assertEquals("6", co.getString("Winds"))
        assertEquals("120", co.getString("Windd"))
        assertEquals("10.00", co.getString("Visibility"))
        assertEquals("30.27", co.getString("SLP"))
        assertEquals("John Glenn Columbus International Airport", co.getString("name"))
        assertEquals("817", co.getString("elev"))
        assertEquals("20 Nov 11:05 pm EST", co.getString("Date"))
    }

    @Test
    fun missingObservationIsOmitted() {
        val root = NwsApiFallback.mapClickRoot(points, forecast, null)
        assertEquals(false, root.has("currentobservation"))
    }

    @Test
    fun hourlyBecomesDigitalWithRealTimestamps() {
        val hourly = JSONObject(
            """{"properties":{"periods":[
                {"startTime":"2026-11-20T23:00:00-05:00","temperature":44,"temperatureUnit":"F",
                 "probabilityOfPrecipitation":{"value":0},"relativeHumidity":{"value":90},
                 "windSpeed":"5 to 10 mph","windDirection":"E",
                 "icon":"https://api.weather.gov/icons/land/night/skc?size=small","shortForecast":"Clear"}]}}""",
        )
        val us = NwsApiFallback.digitalFromHourly(hourly, metric = false).getJSONObject("Hourly")
        assertEquals("1795233600", us.getJSONArray("unixtime").getString(0))
        assertEquals("44", us.getJSONArray("temperature").getString(0))
        assertEquals("10", us.getJSONArray("windSpeed").getString(0))
        assertEquals("90", us.getJSONArray("relativeHumidity").getString(0))

        val metric = NwsApiFallback.digitalFromHourly(hourly, metric = true).getJSONObject("Hourly")
        assertEquals("7", metric.getJSONArray("temperature").getString(0))
        // digitalJSON semantics: wind stays mph; NwsApi converts.
        assertEquals("10", metric.getJSONArray("windSpeed").getString(0))
    }

    @Test
    fun windStringParsing() {
        assertEquals(3, NwsApiFallback.windMph("3 mph"))
        assertEquals(15, NwsApiFallback.windMph("10 to 15 mph"))
        assertEquals(9, NwsApiFallback.windMph("15 km/h"))
        assertNull(NwsApiFallback.windMph(""))
    }

    @Test
    fun ianaTimeZoneAccepted() {
        assertEquals("America/Chicago", LocationTimeZone.fromNwsCode("America/Chicago")?.id)
        assertEquals("America/New_York", LocationTimeZone.fromNwsCode("E|Y|5")?.id)
        assertNull(LocationTimeZone.fromNwsCode("Not/AZone"))
    }
}
