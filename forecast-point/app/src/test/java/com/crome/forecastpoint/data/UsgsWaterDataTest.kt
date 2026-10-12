package com.crome.forecastpoint.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime

class UsgsWaterDataTest {
    private val kidronLat = 40.7458
    private val kidronLon = -81.7453
    private val now = OffsetDateTime.parse("2026-10-12T00:30:00Z").toEpochSecond()

    @Test
    fun nearerStaleGageLosesToALiveGage() {
        val staleFlume = candidate(
            seriesId = "flume",
            locationId = "USGS-404755081531900",
            lat = 40.7986111111111,
            lon = -81.8886111111111,
            time = "2020-11-03T13:02:00+00:00",
        )
        val clinton = candidate(
            seriesId = "clinton",
            locationId = "USGS-03116000",
            lat = 40.9278334087981,
            lon = -81.6326245092029,
            time = "2026-10-12T00:15:00+00:00",
        )
        val sugar = candidate(
            seriesId = "sugar",
            locationId = "USGS-03124000",
            lat = 40.6356164966275,
            lon = -81.5528992726336,
            time = "2026-10-12T00:15:00+00:00",
        )
        val picked = UsgsWaterData.pick(
            listOf(staleFlume, clinton, sugar),
            kidronLat,
            kidronLon,
            maxMiles = 50.0,
            nowEpochSec = now,
        )
        val nearestLive = listOf(clinton, sugar).minBy {
            TideService.haversineMiles(kidronLat, kidronLon, it.lat, it.lon)
        }
        assertEquals(nearestLive.locationId, picked!!.locationId)
        assertTrue(picked.locationId != staleFlume.locationId)
    }

    @Test
    fun staleGageIsUsedWhenNothingRecentIsInRange() {
        val stale = candidate(
            seriesId = "only-stale",
            locationId = "USGS-404755081531900",
            lat = 40.7986111111111,
            lon = -81.8886111111111,
            time = "2020-11-03T13:02:00Z",
        )
        val farLive = candidate(
            seriesId = "far-live",
            locationId = "USGS-99999999",
            lat = 39.0,
            lon = -80.0,
            time = "2026-10-12T00:15:00Z",
        )
        val picked = UsgsWaterData.pick(
            listOf(stale, farLive),
            kidronLat,
            kidronLon,
            maxMiles = 50.0,
            nowEpochSec = now,
        )
        assertEquals("USGS-404755081531900", picked!!.locationId)
    }

    @Test
    fun equalDistancePrefersTheLaterObservation() {
        val older = candidate(
            seriesId = "b-older",
            locationId = "USGS-1",
            lat = 40.7458,
            lon = -81.7453,
            time = "2026-10-11T00:00:00Z",
        )
        val newer = candidate(
            seriesId = "a-newer",
            locationId = "USGS-2",
            lat = 40.7458,
            lon = -81.7453,
            time = "2026-10-12T00:00:00Z",
        )
        val picked = UsgsWaterData.pick(listOf(older, newer), kidronLat, kidronLon, 50.0, now)
        assertEquals("a-newer", picked!!.seriesId)
    }

    @Test
    fun latestPageSkipsBadGeometryAndOtherParameters() {
        val page = UsgsWaterData.parseLatestPage(
            """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [-81.8886111111111, 40.7986111111111]},
                  "properties": {
                    "time_series_id": "stale",
                    "monitoring_location_id": "USGS-404755081531900",
                    "parameter_code": "00065",
                    "statistic_id": "00011",
                    "time": "2020-11-03T13:02:00+00:00",
                    "value": "0.00",
                    "unit_of_measure": "ft"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [-81.6326245092029, 40.9278334087981]},
                  "properties": {
                    "time_series_id": "live",
                    "monitoring_location_id": "USGS-03116000",
                    "parameter_code": "00065",
                    "statistic_id": "00011",
                    "time": "2026-10-12T00:15:00+00:00",
                    "value": "5.08",
                    "unit_of_measure": "ft"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [-81.7453, 40.7458]},
                  "properties": {
                    "time_series_id": "discharge",
                    "monitoring_location_id": "USGS-00000001",
                    "parameter_code": "00060",
                    "statistic_id": "00011",
                    "time": "2026-10-12T00:15:00Z",
                    "value": "10",
                    "unit_of_measure": "ft^3/s"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": null,
                  "properties": {
                    "time_series_id": "no-geometry",
                    "monitoring_location_id": "USGS-00000002",
                    "time": "2026-10-12T00:15:00Z",
                    "value": "1",
                    "unit_of_measure": "ft"
                  }
                }
              ],
              "links": [
                {"rel": "self", "href": "https://api.waterdata.usgs.gov/ogcapi/v1/collections/latest-continuous/items"},
                {"rel": "next", "href": "https://evil.example/steal"},
                {"rel": "next", "href": "https://api.waterdata.usgs.gov/ogcapi/v1/collections/latest-continuous/items?offset=2"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf("stale", "live"), page.items.map { it.seriesId })
        assertEquals(40.9278334087981, page.items[1].lat, 0.0000001)
        assertEquals(-81.6326245092029, page.items[1].lon, 0.0000001)
        assertNull(page.nextUrl)
        val picked = UsgsWaterData.pick(page.items, kidronLat, kidronLon, 50.0, now)
        assertEquals("live", picked!!.seriesId)
    }

    @Test
    fun relativeNextLinkStaysOnTheWaterDataHost() {
        val page = UsgsWaterData.parseLatestPage(
            """
            {"type":"FeatureCollection","features":[],"links":[
              {"rel":"next","href":"/ogcapi/v1/collections/latest-continuous/items?offset=1000"}
            ]}
            """.trimIndent(),
        )
        val next = page.nextUrl!!.toHttpUrl()
        assertEquals(UsgsWaterData.HOST, next.host)
        assertEquals("https", next.scheme)
        assertEquals("1000", next.queryParameter("offset"))
    }

    @Test
    fun hourBucketKeepsTheLaterSampleAndConvertsMeters() {
        val page = UsgsWaterData.parseContinuousPage(
            """
            {"type":"FeatureCollection","features":[
              {"properties":{"time":"2026-10-12T00:15:00Z","value":"5.08","unit_of_measure":"ft","parameter_code":"00065","statistic_id":"00011"}},
              {"properties":{"time":"2026-10-12T00:45:00Z","value":"5.20","unit_of_measure":"FT"}},
              {"properties":{"time":"2026-10-12T00:10:00Z","value":"1.00","unit_of_measure":"feet"}},
              {"properties":{"time":"2026-10-12T01:00:00Z","value":"1.5","unit_of_measure":"m"}},
              {"properties":{"time":"2026-10-12T02:00:00Z","value":"9","unit_of_measure":"ft^3/s"}},
              {"properties":{"time":"2026-10-12T03:00:00Z","value":"nope","unit_of_measure":"ft"}},
              {"properties":{"time":"2026-10-12T04:00:00Z","value":null,"unit_of_measure":"ft"}},
              {"properties":{"time":"2026-10-12T05:00:00Z","value":"4.0","unit_of_measure":null}},
              {"properties":{"time":"2026-10-12T06:00:00Z","value":"3","unit_of_measure":"ft","parameter_code":"00060","statistic_id":"00011"}}
            ]}
            """.trimIndent(),
        )
        val hours = UsgsWaterData.bucketHours(page.items)
        val h0 = OffsetDateTime.parse("2026-10-12T00:00:00Z").toEpochSecond()
        assertEquals(5.20, hours[h0]!!, 0.0001)
        assertEquals(1.5 * 3.280839895, hours[h0 + 3600L]!!, 0.0001)
        assertEquals(4.0, hours[h0 + 5 * 3600L]!!, 0.0001)
        assertNull(hours[h0 + 2 * 3600L])
        assertNull(hours[h0 + 3 * 3600L])
        assertNull(hours[h0 + 4 * 3600L])
        assertNull(hours[h0 + 6 * 3600L])
    }

    @Test
    fun unknownUnitsAreSkippedAndBlankUnitsStayFeet() {
        assertEquals(5.08, UsgsWaterData.toFeet("5.08", "ft")!!, 0.0001)
        assertEquals(5.0, UsgsWaterData.toFeet("5", "foot")!!, 0.0001)
        assertEquals(2.0, UsgsWaterData.toFeet("2", "")!!, 0.0001)
        assertEquals(2.0, UsgsWaterData.toFeet("2", null)!!, 0.0001)
        assertEquals(3.280839895, UsgsWaterData.toFeet("1", "metres")!!, 0.0000001)
        assertNull(UsgsWaterData.toFeet("1", "cfs"))
        assertNull(UsgsWaterData.toFeet("nope", "ft"))
        assertNull(UsgsWaterData.toFeet(null, "ft"))
    }

    @Test
    fun siteNumberKeepsLeadingZeros() {
        assertEquals("03116000", UsgsWaterData.siteNumber("USGS-03116000"))
        assertEquals("410014081362600", UsgsWaterData.siteNumber("USGS-410014081362600"))
        assertEquals("03116000", UsgsWaterData.siteNumber("03116000"))
    }

    @Test
    fun locationNameComesFromTheMonitoringLocationFeature() {
        val named = UsgsWaterData.parseMonitoringLocation(
            """
            {"features":[{"properties":{
              "monitoring_location_number":"03116000",
              "monitoring_location_name":"Tuscarawas River at Clinton OH"
            }}]}
            """.trimIndent(),
        )
        assertEquals("03116000", named!!.siteNo)
        assertEquals("Tuscarawas River at Clinton OH", named.name)
        assertNull(UsgsWaterData.parseMonitoringLocation("""{"features":[]}"""))
    }

    @Test
    fun requestsUseTheWaterDataHostAndNoApiKey() {
        val search = UsgsWaterData.latestContinuousUrl(40.0, -80.0, 50.0).toHttpUrl()
        assertEquals("https", search.scheme)
        assertEquals(UsgsWaterData.HOST, search.host)
        assertEquals(
            listOf("ogcapi", "v1", "collections", "latest-continuous", "items"),
            search.pathSegments,
        )
        assertEquals(UsgsWaterData.STAGE_FILTER, search.queryParameter("filter"))
        assertEquals("1000", search.queryParameter("limit"))
        assertNull(search.queryParameter("api_key"))
        val bbox = search.queryParameter("bbox")!!.split(",")
        assertEquals(4, bbox.size)
        assertTrue(bbox[0].toDouble() < -80.0)
        assertTrue(bbox[1].toDouble() < 40.0)
        assertTrue(bbox[2].toDouble() > -80.0)
        assertTrue(bbox[3].toDouble() > 40.0)

        val end = Instant.parse("2026-10-12T00:30:00Z")
        val history = UsgsWaterData.continuousUrl("abc123", end).toHttpUrl()
        assertEquals("continuous", history.pathSegments[3])
        assertEquals("abc123", history.queryParameter("time_series_id"))
        assertEquals("00065", history.queryParameter("parameter_code"))
        assertEquals("00011", history.queryParameter("statistic_id"))
        assertEquals("5000", history.queryParameter("limit"))
        assertEquals(
            "2026-10-05T00:30:00Z/2026-10-12T00:30:00Z",
            history.queryParameter("datetime"),
        )
        assertNull(history.queryParameter("api_key"))

        val location = UsgsWaterData.monitoringLocationUrl("USGS-03116000").toHttpUrl()
        assertEquals("monitoring-locations", location.pathSegments[3])
        assertEquals("USGS-03116000", location.queryParameter("id"))
        assertNull(location.queryParameter("api_key"))

        assertTrue(UsgsWaterData.isWaterDataUrl(search.toString()))
        assertEquals(false, UsgsWaterData.isWaterDataUrl("https://waterservices.usgs.gov/nwis/iv/"))
        assertEquals(false, UsgsWaterData.isWaterDataUrl("http://api.waterdata.usgs.gov/ogcapi/v1/x"))
        assertEquals(
            false,
            UsgsWaterData.isWaterDataUrl("https://api.waterdata.usgs.gov.evil.example/ogcapi"),
        )
        assertNull(UsgsWaterData.allowedNextUrl("https://user:pass@api.waterdata.usgs.gov/ogcapi/v1/x"))
    }

    private fun candidate(
        seriesId: String,
        locationId: String,
        lat: Double,
        lon: Double,
        time: String,
    ): UsgsWaterData.Candidate {
        return UsgsWaterData.Candidate(
            seriesId = seriesId,
            locationId = locationId,
            lat = lat,
            lon = lon,
            observedEpochSec = OffsetDateTime.parse(time).toEpochSecond(),
        )
    }
}
