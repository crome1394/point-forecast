package com.crome.forecastpoint.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.util.Locale
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos

/**
 * USGS Water Data OGC API (api.waterdata.usgs.gov) for instantaneous gage height.
 *
 * Parameter 00065 is gage height. Statistic 00011 is the instantaneous series,
 * the same measurement the old NWIS `iv` service returned. No API key: anonymous
 * calls are limited per IP, and a key shipped in the app would be shared by
 * every install.
 *
 * The new locations collection has no active/inactive flag. A retired gage can
 * be closer than a live one and still carry an old reading, which would leave
 * the hourly chart empty. [pick] prefers a series observed in the last 7 days.
 */
internal object UsgsWaterData {
    const val HOST = "api.waterdata.usgs.gov"
    const val STAGE_PARAMETER = "00065"
    const val INSTANTANEOUS = "00011"

    /** Same window as the hourly stage chart and the old `period=P7D` request. */
    const val RECENT_WINDOW_SEC = 7L * 24L * 3600L

    const val SEARCH_LIMIT = 1000
    const val SEARCH_MAX_PAGES = 4
    const val HISTORY_LIMIT = 5000
    const val HISTORY_MAX_PAGES = 4

    /**
     * Old NWIS `siteType=ST` was a prefix match, so canal (ST-CA) and ditch
     * (ST-DCH) sites were included. Exact codes drop those. LIKE keeps them.
     */
    const val STAGE_FILTER =
        "parameter_code='$STAGE_PARAMETER' AND statistic_id='$INSTANTANEOUS' AND " +
            "(site_type_code LIKE 'ST%' OR site_type_code LIKE 'LK%' OR " +
            "site_type_code LIKE 'ES%' OR site_type_code LIKE 'OC%')"

    private const val METERS_TO_FEET = 3.280839895

    data class Candidate(
        val seriesId: String,
        val locationId: String,
        val lat: Double,
        val lon: Double,
        val observedEpochSec: Long,
    )

    data class Sample(val epochSec: Long, val feet: Double)

    data class Page<T>(val items: List<T>, val nextUrl: String?)

    data class LocationName(val siteNo: String, val name: String)

    fun latestContinuousUrl(latitude: Double, longitude: Double, radiusMiles: Double): String {
        return itemsBuilder("latest-continuous")
            .addQueryParameter("f", "json")
            .addQueryParameter("bbox", bbox(latitude, longitude, radiusMiles))
            .addQueryParameter("limit", SEARCH_LIMIT.toString())
            .addQueryParameter("filter", STAGE_FILTER)
            .build()
            .toString()
    }

    fun continuousUrl(seriesId: String, end: Instant = Instant.now()): String {
        val start = end.minusSeconds(RECENT_WINDOW_SEC)
        val interval = "${formatInstant(start)}/${formatInstant(end)}"
        return itemsBuilder("continuous")
            .addQueryParameter("f", "json")
            .addQueryParameter("time_series_id", seriesId)
            .addQueryParameter("parameter_code", STAGE_PARAMETER)
            .addQueryParameter("statistic_id", INSTANTANEOUS)
            .addQueryParameter("datetime", interval)
            .addQueryParameter("limit", HISTORY_LIMIT.toString())
            .build()
            .toString()
    }

    fun monitoringLocationUrl(locationId: String): String {
        return itemsBuilder("monitoring-locations")
            .addQueryParameter("f", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("id", locationId)
            .build()
            .toString()
    }

    /** West, south, east, north. Same degree step the old site search used. */
    fun bbox(latitude: Double, longitude: Double, radiusMiles: Double): String {
        val degLat = radiusMiles / 69.0
        val degLon = radiusMiles / (69.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.2))
        return "${longitude - degLon},${latitude - degLat},${longitude + degLon},${latitude + degLat}"
    }

    fun isWaterDataUrl(url: String): Boolean = allowedNextUrl(url) != null

    /** Follow pagination only back to this API. Relative links resolve on [HOST]. */
    fun allowedNextUrl(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val absolute = when {
            href.startsWith("https://") || href.startsWith("http://") -> href
            href.startsWith("/") -> "https://$HOST$href"
            else -> return null
        }
        val parsed = absolute.toHttpUrlOrNull() ?: return null
        if (parsed.username.isNotEmpty()) return null
        if (parsed.scheme != "https" || parsed.host != HOST) return null
        return parsed.toString()
    }

    fun parseLatestPage(body: String): Page<Candidate> {
        val root = parseRoot(body) ?: return Page(emptyList(), null)
        val features = root.arrayOrEmpty("features")
        val items = ArrayList<Candidate>(features.size)
        for (el in features) {
            val feature = el as? JsonObject ?: continue
            parseCandidate(feature)?.let { items += it }
        }
        return Page(items, nextUrl(root))
    }

    fun parseContinuousPage(body: String): Page<Sample> {
        val root = parseRoot(body) ?: return Page(emptyList(), null)
        val features = root.arrayOrEmpty("features")
        val items = ArrayList<Sample>(features.size)
        for (el in features) {
            val feature = el as? JsonObject ?: continue
            parseSample(feature)?.let { items += it }
        }
        return Page(items, nextUrl(root))
    }

    fun parseMonitoringLocation(body: String): LocationName? {
        val root = parseRoot(body) ?: return null
        val feature = root.arrayOrEmpty("features").firstOrNull() as? JsonObject ?: return null
        val props = feature["properties"] as? JsonObject ?: return null
        val name = props.text("monitoring_location_name")?.trim().orEmpty()
        val siteNo = props.text("monitoring_location_number")?.trim().orEmpty()
        if (name.isEmpty() && siteNo.isEmpty()) return null
        return LocationName(siteNo = siteNo, name = name)
    }

    /**
     * Nearest series inside [maxMiles]. A series observed since
     * [nowEpochSec] minus [RECENT_WINDOW_SEC] beats a closer stale series.
     * Stale series are used only when every series in range is older than that.
     * Equal distance prefers the later observation, then the series id.
     */
    fun pick(
        candidates: List<Candidate>,
        latitude: Double,
        longitude: Double,
        maxMiles: Double,
        nowEpochSec: Long,
    ): Candidate? {
        val inRange = ArrayList<Ranked>(candidates.size)
        for (candidate in candidates) {
            if (candidate.seriesId.isBlank() || candidate.locationId.isBlank()) continue
            if (!candidate.lat.isFinite() || !candidate.lon.isFinite()) continue
            val miles = TideService.haversineMiles(
                latitude,
                longitude,
                candidate.lat,
                candidate.lon,
            )
            if (!miles.isFinite() || miles > maxMiles) continue
            inRange += Ranked(candidate, miles)
        }
        if (inRange.isEmpty()) return null
        val cutoff = nowEpochSec - RECENT_WINDOW_SEC
        val recent = inRange.filter { it.candidate.observedEpochSec >= cutoff }
        val pool = if (recent.isNotEmpty()) recent else inRange
        return pool.minWithOrNull(
            compareBy<Ranked> { it.miles }
                .thenByDescending { it.candidate.observedEpochSec }
                .thenBy { it.candidate.seriesId },
        )?.candidate
    }

    /**
     * Gage height stored as feet. 00065 is defined in feet; a blank unit stays
     * in feet. Meters are converted. Anything else is skipped so a discharge
     * unit cannot be drawn as a stage.
     */
    fun toFeet(valueText: String?, unitText: String?): Double? {
        val value = valueText?.trim()?.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        val unit = unitText?.trim()?.lowercase(Locale.US)?.removeSuffix(".").orEmpty()
        return when (unit) {
            "", "ft", "feet", "foot" -> value
            "m", "meter", "meters", "metre", "metres" -> value * METERS_TO_FEET
            else -> null
        }
    }

    /** Later sample in the same UTC hour wins, even if it appears earlier in the page. */
    fun bucketHours(samples: List<Sample>): Map<Long, Double> {
        val bestEpoch = HashMap<Long, Long>(samples.size)
        val out = HashMap<Long, Double>(samples.size)
        for (sample in samples) {
            val hour = Math.floorDiv(sample.epochSec, 3600L) * 3600L
            val previous = bestEpoch[hour]
            if (previous == null || sample.epochSec >= previous) {
                bestEpoch[hour] = sample.epochSec
                out[hour] = sample.feet
            }
        }
        return out
    }

    /** "USGS-03116000" → "03116000". Leading zeros stay. */
    fun siteNumber(locationId: String): String {
        val trimmed = locationId.trim()
        return if (trimmed.startsWith("USGS-")) trimmed.removePrefix("USGS-") else trimmed
    }

    private data class Ranked(val candidate: Candidate, val miles: Double)

    private fun itemsBuilder(collection: String): HttpUrl.Builder {
        return HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments("ogcapi/v1/collections")
            .addPathSegment(collection)
            .addPathSegment("items")
    }

    private fun formatInstant(instant: Instant): String {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))
    }

    private fun parseRoot(body: String): JsonObject? {
        if (body.isBlank()) return null
        return runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    }

    private fun parseCandidate(feature: JsonObject): Candidate? {
        val props = feature["properties"] as? JsonObject ?: return null
        if (!acceptsStageSeries(props)) return null
        val seriesId = props.text("time_series_id")?.trim().orEmpty()
        val locationId = props.text("monitoring_location_id")?.trim().orEmpty()
        if (seriesId.isEmpty() || locationId.isEmpty()) return null
        val observed = parseEpoch(props.text("time")) ?: return null
        val (lat, lon) = pointLatLon(feature) ?: return null
        return Candidate(
            seriesId = seriesId,
            locationId = locationId,
            lat = lat,
            lon = lon,
            observedEpochSec = observed,
        )
    }

    private fun parseSample(feature: JsonObject): Sample? {
        val props = feature["properties"] as? JsonObject ?: return null
        if (!acceptsStageSeries(props)) return null
        val epoch = parseEpoch(props.text("time")) ?: return null
        val feet = toFeet(props.text("value"), props.text("unit_of_measure")) ?: return null
        return Sample(epochSec = epoch, feet = feet)
    }

    /** Missing parameter or statistic is allowed; a different code is not. */
    private fun acceptsStageSeries(props: JsonObject): Boolean {
        val parameter = props.text("parameter_code")
        if (parameter != null && parameter != STAGE_PARAMETER) return false
        val statistic = props.text("statistic_id")
        if (statistic != null && statistic != INSTANTANEOUS) return false
        return true
    }

    private fun pointLatLon(feature: JsonObject): Pair<Double, Double>? {
        val geometry = feature["geometry"] as? JsonObject ?: return null
        val type = geometry.text("type") ?: return null
        if (!type.equals("Point", ignoreCase = true)) return null
        val coords = geometry["coordinates"] as? JsonArray ?: return null
        val lon = coords.doubleAt(0) ?: return null
        val lat = coords.doubleAt(1) ?: return null
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    private fun nextUrl(root: JsonObject): String? {
        val links = root["links"] as? JsonArray ?: return null
        for (el in links) {
            val link = el as? JsonObject ?: continue
            val rel = link.text("rel") ?: continue
            if (!rel.equals("next", ignoreCase = true)) continue
            return allowedNextUrl(link.text("href"))
        }
        return null
    }

    private fun parseEpoch(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(text).toEpochSecond() }.getOrNull()
    }

    private fun JsonObject.arrayOrEmpty(key: String): JsonArray {
        return this[key] as? JsonArray ?: JsonArray(emptyList())
    }

    private fun JsonObject.text(key: String): String? {
        val prim = this[key] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.contentOrNull
    }

    private fun JsonArray.doubleAt(index: Int): Double? {
        val prim = getOrNull(index) as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.contentOrNull?.toDoubleOrNull()
    }
}
