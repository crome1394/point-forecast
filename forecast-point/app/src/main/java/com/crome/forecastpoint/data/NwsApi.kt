package com.crome.forecastpoint.data

import com.crome.forecastpoint.util.IconMapper
import com.crome.forecastpoint.util.SunCalculator
import com.crome.forecastpoint.util.WeatherMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * NOAA NWS MapClick + digitalJSON + gridpoints + CO-OPS tides client.
 * Uses a browser-like User-Agent so Akamai does not 403 the request (Calyx-safe).
 */
class NwsApi(
    private val client: OkHttpClient = defaultClient(),
    private val tideService: TideService? = null,
    private val openMeteoService: OpenMeteoService? = OpenMeteoService(),
) {
    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        preferredName: String? = null,
    ): WeatherSnapshot = withContext(Dispatchers.IO) {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)

        coroutineScope {
            // Critical path first: forecast JSON (drives main screen)
            val forecastDeferred = async {
                getJson(
                    "https://forecast.weather.gov/MapClick.php?lat=$lat&lon=$lon&unit=0&lg=english&FcstType=json",
                )
            }
            val digitalDeferred = async {
                runCatching {
                    getJson(
                        "https://forecast.weather.gov/MapClick.php?lat=$lat&lon=$lon&unit=0&lg=english&FcstType=digitalJSON",
                    )
                }.getOrNull()
            }
            // Secondary data in parallel (failures are non-fatal)
            val gridDeferred = async {
                runCatching { fetchGridSeries(latitude, longitude) }.getOrDefault(GridSeries.EMPTY)
            }
            // null = request failed; empty list = no active alerts (authoritative)
            val alertsDeferred = async {
                runCatching { fetchActiveAlerts(latitude, longitude) }.getOrNull()
            }
            val tidesDeferred = async {
                runCatching {
                    tideService?.fetchHourlyTides(latitude, longitude) ?: TideService.TideResult.EMPTY
                }.getOrDefault(TideService.TideResult.EMPTY)
            }
            val extrasDeferred = async {
                runCatching {
                    openMeteoService?.fetchExtras(latitude, longitude)
                        ?: OpenMeteoService.HourlyExtras.EMPTY
                }.getOrDefault(OpenMeteoService.HourlyExtras.EMPTY)
            }

            parseSnapshot(
                root = forecastDeferred.await(),
                digital = digitalDeferred.await(),
                grid = gridDeferred.await(),
                apiAlerts = alertsDeferred.await(),
                tides = tidesDeferred.await(),
                extras = extrasDeferred.await(),
                latitude = latitude,
                longitude = longitude,
                preferredName = preferredName,
            )
        }
    }

    /**
     * City / town search only — filters out streets, businesses, and other non-settlements.
     * Handles "City, ST" queries (e.g. "de kalb, IL" → DeKalb IL).
     */
    suspend fun geocode(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val seen = linkedSetOf<String>()
        val out = mutableListOf<GeocodeResult>()

        fun addFrom(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (!isSettlementHit(o)) continue
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val display = o.optString("display_name")
                val addr = o.optJSONObject("address")
                val name = formatPlaceName(
                    placeName = o.optString("name").takeIf { it.isNotBlank() },
                    address = addr,
                    displayName = display,
                    osmClass = o.optString("class"),
                    osmType = o.optString("type"),
                )
                if (name.isBlank() || isStreetLikeName(name)) continue
                val key = "${name.lowercase(Locale.US)}|${"%.3f".format(Locale.US, lat)}|${"%.3f".format(Locale.US, lon)}"
                if (!seen.add(key)) continue
                out += GeocodeResult(
                    name = name,
                    displayName = display.ifBlank { name },
                    latitude = lat,
                    longitude = lon,
                )
            }
        }

        // 1) Free-form search
        addFrom(nominatimSearchFreeform(trimmed))

        // 2) Structured "City, ST" — better for multi-word cities (De Kalb → DeKalb)
        val cityState = parseCityStateQuery(trimmed)
        if (cityState != null) {
            val (city, state) = cityState
            addFrom(nominatimSearchStructured(city, state))
            // Nominatim is picky about spaces: "de kalb" often fails, "dekalb" works
            val collapsed = city.replace(" ", "")
            if (collapsed.length >= 3 && !collapsed.equals(city, ignoreCase = true)) {
                addFrom(nominatimSearchStructured(collapsed, state))
            }
        }

        out.take(8)
    }

    /**
     * Reverse-geocode a map tap into nearest city/town + state (never a street name).
     * Uses zoom≈12 so Nominatim returns a settlement, not a house/road.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult =
        withContext(Dispatchers.IO) {
            val lat = String.format(Locale.US, "%.5f", latitude)
            val lon = String.format(Locale.US, "%.5f", longitude)
            // zoom 10–14 ≈ city/town; default 18 returns house/road names
            val url =
                "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon" +
                    "&format=json&addressdetails=1&zoom=12"
            val body = getString(url)
            val o = JSONObject(body)
            val display = o.optString("display_name")
            val addr = o.optJSONObject("address")
            val name = formatPlaceName(
                placeName = o.optString("name").takeIf { it.isNotBlank() },
                address = addr,
                displayName = display.ifBlank { "$lat, $lon" },
                osmClass = o.optString("class"),
                osmType = o.optString("type"),
            )
            GeocodeResult(
                name = name.ifBlank { String.format(Locale.US, "%.3f, %.3f", latitude, longitude) },
                displayName = display.ifBlank { name },
                latitude = latitude,
                longitude = longitude,
            )
        }

    private fun nominatimSearchFreeform(query: String): JSONArray {
        val q = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val url =
            "https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=12" +
                "&addressdetails=1&countrycodes=us"
        return runCatching { JSONArray(getString(url)) }.getOrDefault(JSONArray())
    }

    private fun nominatimSearchStructured(city: String, state: String): JSONArray {
        val c = java.net.URLEncoder.encode(city, Charsets.UTF_8.name())
        val s = java.net.URLEncoder.encode(state, Charsets.UTF_8.name())
        val url =
            "https://nominatim.openstreetmap.org/search?city=$c&state=$s&country=USA" +
                "&format=json&limit=8&addressdetails=1&countrycodes=us"
        return runCatching { JSONArray(getString(url)) }.getOrDefault(JSONArray())
    }

    /** "de kalb, IL" / "Columbus Ohio" → city + state token. */
    private fun parseCityStateQuery(query: String): Pair<String, String>? {
        val comma = query.split(",", limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
        if (comma.size == 2) {
            return comma[0] to comma[1]
        }
        // "City ST" trailing 2-letter state
        val m = Regex("""^(.+?)\s+([A-Za-z]{2})$""").matchEntire(query.trim())
        if (m != null) {
            return m.groupValues[1].trim() to m.groupValues[2]
        }
        return null
    }

    /**
     * Keep cities/towns/villages/admin places; drop roads, POIs, counties when possible.
     */
    private fun isSettlementHit(o: JSONObject): Boolean {
        val clazz = o.optString("class")
        val type = o.optString("type")
        val name = o.optString("name")
        if (clazz in setOf("highway", "railway", "waterway", "aeroway", "aerialway")) return false
        if (clazz == "landuse" || clazz == "natural" || clazz == "building") return false
        if (type in setOf(
                "residential", "commercial", "industrial", "retail", "farm",
                "house", "yes", "apartments",
            )
        ) {
            return false
        }
        if (isStreetLikeName(name)) return false
        // Counties are not useful as "city" results
        if (name.contains(" County", ignoreCase = true)) return false
        if (clazz == "place") return true
        if (clazz == "boundary" && type == "administrative") return true
        if (type in setOf(
                "city", "town", "village", "hamlet", "municipality",
                "suburb", "borough", "city_district", "administrative",
            )
        ) {
            return true
        }
        // Address payload may still describe a city even when class is odd
        val addr = o.optJSONObject("address") ?: return false
        return addrHasLocality(addr) && !isStreetLikeName(name)
    }

    private fun addrHasLocality(address: JSONObject): Boolean {
        for (k in listOf("city", "town", "village", "hamlet", "municipality")) {
            if (address.optString(k).isNotBlank()) return true
        }
        return false
    }

    private fun isStreetLikeName(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        // "123 Main Street" or pure road labels
        if (n.first().isDigit()) return true
        val lower = " ${n.lowercase(Locale.US)} "
        val tokens = listOf(
            " road", " rd", " rd.", " street", " st.", " avenue", " ave", " ave.",
            " boulevard", " blvd", " blvd.", " lane", " ln", " ln.", " drive", " dr", " dr.",
            " court", " ct", " ct.", " circle", " cir", " way", " highway", " hwy", " hwy.",
            " parkway", " pkwy", " trail", " terrace", " ter", " pike", " route", " rte",
            " freeway", " expressway", " alley", " path", " walk",
        )
        // Require suffix-style match (end of name) so "St Louis" is not treated as a street
        val end = n.lowercase(Locale.US)
        return tokens.any { t ->
            val bare = t.trim().removeSuffix(".")
            end.endsWith(" $bare") || end.endsWith(" $bare.") ||
                end.endsWith(" ${bare.replace(".", "")}")
        } || lower.contains(" road ") || lower.contains(" street ") || lower.contains(" avenue ")
    }

    /**
     * Build "Columbus OH" / "DeKalb IL" labels.
     * Prefer city/town/village from the address block — never road/house names.
     */
    private fun formatPlaceName(
        placeName: String?,
        address: JSONObject?,
        displayName: String,
        osmClass: String? = null,
        osmType: String? = null,
    ): String {
        fun addr(vararg keys: String): String? {
            if (address == null) return null
            for (k in keys) {
                val v = address.optString(k).takeIf { it.isNotBlank() }
                if (v != null) return v
            }
            return null
        }

        // Settlement fields only (skip road, house_number, suburb-as-street, etc.)
        val localityFromAddr = addr(
            "city", "town", "village", "hamlet", "municipality",
            "city_district", "borough",
        )
            // suburb only if we have nothing better (still a place, not a street)
            ?: addr("suburb")

        val usablePlaceName = placeName
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { !isStreetLikeName(it) }
            ?.takeIf {
                // Don't use highway/POI feature names as the city label
                osmClass !in setOf("highway", "railway", "landuse", "building", "amenity", "shop")
            }

        val locality = localityFromAddr
            ?: usablePlaceName
            ?: addr("county")?.removeSuffix(" County")?.takeIf { !it.equals("County", true) }
            ?: displayName.substringBefore(',').trim()
                .takeIf { it.isNotBlank() && !isStreetLikeName(it) && !it.first().isDigit() }

        val stateRaw = addr("state", "state_code")
            ?: Regex("""\b([A-Z]{2})\b""").find(displayName)?.groupValues?.getOrNull(1)
        val state = stateRaw?.let { shortState(it) }

        return when {
            !locality.isNullOrBlank() && !state.isNullOrBlank() -> {
                if (locality.equals(stateRaw, ignoreCase = true) ||
                    locality.equals(state, ignoreCase = true)
                ) {
                    state
                } else {
                    "$locality $state"
                }
            }
            !locality.isNullOrBlank() -> locality
            !state.isNullOrBlank() -> state
            else -> "Selected location"
        }
    }

    private fun fetchGridSeries(latitude: Double, longitude: Double): GridSeries {
        val points = getJson(
            "https://api.weather.gov/points/" +
                String.format(Locale.US, "%.4f,%.4f", latitude, longitude),
        )
        val gridUrl = points.optJSONObject("properties")?.optString("forecastGridData")
            ?.takeIf { it.isNotBlank() }
            ?: return GridSeries.EMPTY
        val grid = getJson(gridUrl)
        val props = grid.optJSONObject("properties") ?: return GridSeries.EMPTY

        // QPF is accumulative over the interval → split across hours.
        // Dewpoint / gust are constant over the interval → repeat the value each hour.
        val qpfByEpoch = expandGridSeries(
            props.optJSONObject("quantitativePrecipitation")?.optJSONArray("values"),
            transform = { mm -> WeatherMath.mmToInches(mm) },
            splitAcrossHours = true,
        )
        val gustByEpoch = expandGridSeries(
            props.optJSONObject("windGust")?.optJSONArray("values"),
            transform = { kmh -> WeatherMath.kmhToMph(kmh).toDouble() },
            splitAcrossHours = false,
        )
        val dewByEpoch = expandGridSeries(
            props.optJSONObject("dewpoint")?.optJSONArray("values"),
            transform = { c -> WeatherMath.celsiusToF(c).toDouble() },
            splitAcrossHours = false,
        )
        // Visibility in meters → miles
        val visByEpoch = expandGridSeries(
            props.optJSONObject("visibility")?.optJSONArray("values"),
            transform = { m -> WeatherMath.metersToMiles(m) },
            splitAcrossHours = false,
        )
        return GridSeries(
            qpfInches = qpfByEpoch,
            gustMph = gustByEpoch,
            dewPointF = dewByEpoch,
            visibilityMi = visByEpoch,
        )
    }

    /**
     * Expand NWS grid `validTime` ISO-8601 intervals (e.g. `2026-08-06T12:00:00+00:00/PT6H`)
     * into a map of epoch-hour → value.
     *
     * @param splitAcrossHours when true (QPF totals), divide the value evenly per hour;
     *   when false (dewpoint/gust), repeat the same value for each hour in the interval.
     */
    private fun expandGridSeries(
        values: JSONArray?,
        transform: (Double) -> Double,
        splitAcrossHours: Boolean,
    ): Map<Long, Double> {
        if (values == null) return emptyMap()
        val out = mutableMapOf<Long, Double>()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (i in 0 until values.length()) {
            val item = values.optJSONObject(i) ?: continue
            if (item.isNull("value")) continue
            val raw = item.optDouble("value", Double.NaN)
            if (raw.isNaN()) continue
            val valid = item.optString("validTime")
            val parts = valid.split("/", limit = 2)
            if (parts.isEmpty()) continue
            val startMs = try {
                iso.parse(parts[0].replace("Z", "+00:00"))?.time
            } catch (_: Exception) {
                try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                        .parse(parts[0])?.time
                } catch (_: Exception) {
                    null
                }
            } ?: continue

            val hours = parseIsoDurationHours(parts.getOrNull(1) ?: "PT1H").coerceAtLeast(1)
            val transformed = transform(raw)
            val perHour = if (splitAcrossHours) transformed / hours else transformed
            for (h in 0 until hours) {
                val epochHour = ((startMs / 1000L) + h * 3600L) / 3600L * 3600L
                // Later finer-grained intervals overwrite coarser ones
                out[epochHour] = perHour
            }
        }
        return out
    }

    private fun parseIsoDurationHours(duration: String): Int {
        // PT1H, PT6H, PT2H30M → hours (round up minutes)
        val h = Regex("(\\d+)H").find(duration)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val m = Regex("(\\d+)M").find(duration)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return (h + if (m > 0) 1 else 0).coerceAtLeast(1)
    }

    /**
     * Active CAP alerts from api.weather.gov for a point.
     * Returns an empty list when there are no alerts (not null).
     * Throws on network / parse failure so the caller can distinguish failure
     * from “no alerts.”
     */
    private fun fetchActiveAlerts(latitude: Double, longitude: Double): List<WeatherHazard> {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        val json = getJson(
            "https://api.weather.gov/alerts/active?point=$lat,$lon&status=actual",
        )
        val features = json.optJSONArray("features") ?: return emptyList()
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val event = props.optString("event").takeIf { it.isNotBlank() } ?: continue
                // Skip test / non-alert products (HWO is a routine discussion, not a WWA)
                val status = props.optString("status")
                if (status.equals("Test", ignoreCase = true)) continue
                if (isNonAlertProduct(event)) continue
                val messageType = props.optString("messageType")
                if (messageType.equals("Cancel", ignoreCase = true)) continue
                val desc = props.optString("description").takeIf { it.isNotBlank() }
                val instruction = props.optString("instruction").takeIf { it.isNotBlank() }
                val headline = props.optString("headline").takeIf { it.isNotBlank() }
                val severity = props.optString("severity").takeIf { it.isNotBlank() }
                val urgency = props.optString("urgency").takeIf { it.isNotBlank() }
                val id = props.optString("id").takeIf { it.isNotBlank() }
                    ?: feature.optString("id").takeIf { it.isNotBlank() }
                add(
                    WeatherHazard(
                        event = event,
                        headline = headline,
                        severity = severity,
                        urgency = urgency,
                        description = desc?.take(1200),
                        instruction = instruction?.take(800),
                        url = id, // NWS id URL often works in browser
                    ),
                )
            }
        }
    }

    private fun parseMapClickHazards(data: JSONObject): List<WeatherHazard> {
        val names = data.optJSONArray("hazard") ?: return emptyList()
        val urls = data.optJSONArray("hazardUrl")
        return buildList {
            for (i in 0 until names.length()) {
                val event = names.optString(i).takeIf { it.isNotBlank() } ?: continue
                // MapClick often lists "Hazardous Weather Outlook" even when there is
                // no active watch/warning/advisory — do not surface those as hazards.
                if (isNonAlertProduct(event)) continue
                val url = urls?.optString(i)
                    ?.takeIf { it.isNotBlank() }
                    ?.replace("&amp;", "&")
                add(
                    WeatherHazard(
                        event = event,
                        headline = event,
                        severity = null,
                        urgency = null,
                        description = null,
                        instruction = null,
                        url = url,
                    ),
                )
            }
        }
    }

    /**
     * Prefer CAP active alerts. Only fall back to MapClick when the API request
     * failed — an empty CAP result means "no alerts," not "use MapClick."
     */
    private fun mergeHazards(
        apiAlerts: List<WeatherHazard>?,
        mapClick: List<WeatherHazard>,
    ): List<WeatherHazard> {
        if (apiAlerts != null) return apiAlerts
        return mapClick
    }

    /**
     * Products that appear in MapClick / CAP but are routine discussions or
     * non-WWA outlooks — not actionable watches, warnings, or advisories.
     */
    private fun isNonAlertProduct(event: String): Boolean {
        val e = event.trim().lowercase(Locale.US)
        if (e.isEmpty() || e == "null" || e == "none") return true
        return e.contains("hazardous weather outlook") ||
            e.contains("hydrologic outlook") ||
            e.contains("weather outlook") ||
            e == "hwo" ||
            e.contains("area forecast discussion") ||
            e.contains("public information statement")
    }

    private fun parseSnapshot(
        root: JSONObject,
        digital: JSONObject?,
        grid: GridSeries,
        apiAlerts: List<WeatherHazard>?,
        tides: TideService.TideResult,
        extras: OpenMeteoService.HourlyExtras,
        latitude: Double,
        longitude: Double,
        preferredName: String?,
    ): WeatherSnapshot {
        val location = root.optJSONObject("location")
        val area = location?.optString("areaDescription").orEmpty()
        val elev = location?.optString("elevation")?.toIntOrNull()
        val name = preferredName?.takeIf { it.isNotBlank() }
            ?: area.ifBlank { "Selected location" }

        val currentObj = root.optJSONObject("currentobservation") ?: JSONObject()
        val temp = currentObj.optString("Temp").toIntOrNull()
        val relh = currentObj.optString("Relh").toIntOrNull()
        val winds = currentObj.optString("Winds").toIntOrNull()
        val windd = currentObj.optString("Windd").toIntOrNull()
        val dewp = currentObj.optString("Dewp").toIntOrNull()
            ?: WeatherMath.dewPointF(temp, relh)
        val weatherImg = currentObj.optString("Weatherimage")
        val weather = currentObj.optString("Weather").ifBlank { "—" }
        val visibility = currentObj.optString("Visibility").takeIf { it.isNotBlank() }
        val slp = currentObj.optString("SLP").takeIf { it.isNotBlank() }
        val slpMb = slp?.toDoubleOrNull()?.let {
            String.format(Locale.US, "%.2f", WeatherMath.inHgToMb(it))
        }
        val station = currentObj.optString("Name").takeIf { it.isNotBlank() }
            ?: currentObj.optString("id").takeIf { it.isNotBlank() }
            ?: area
        val obsDate = currentObj.optString("Date").takeIf { it.isNotBlank() }
        val elevObs = currentObj.optString("elev").toIntOrNull() ?: elev

        val current = CurrentConditions(
            temperatureF = temp,
            weather = weather,
            iconCode = IconMapper.codeFrom(weatherImg),
            feelsLikeF = WeatherMath.feelsLikeF(temp, relh, winds),
            humidityPct = relh,
            windDirection = WeatherMath.degreesToCardinal(windd) ?: windd?.toString(),
            windSpeedMph = winds,
            dewPointF = dewp,
            visibilityMi = visibility,
            barometerInHg = slp,
            barometerMb = slpMb,
            stationName = station,
            observedAt = obsDate,
            elevationFt = elevObs,
        )

        val time = root.optJSONObject("time") ?: JSONObject()
        val data = root.optJSONObject("data") ?: JSONObject()
        val periodNames = time.optJSONArray("startPeriodName")
        val startTimes = time.optJSONArray("startValidTime")
        val tempLabels = time.optJSONArray("tempLabel")
        val temps = data.optJSONArray("temperature")
        val pops = data.optJSONArray("pop")
        val weathers = data.optJSONArray("weather")
        val icons = data.optJSONArray("iconLink")
        val texts = data.optJSONArray("text")

        val periods = buildList {
            val n = periodNames?.length() ?: 0
            for (i in 0 until n) {
                val pName = periodNames!!.optString(i)
                val startIso = startTimes?.optString(i)
                val isDay = !pName.contains("Night", ignoreCase = true) &&
                    !pName.equals("Tonight", ignoreCase = true) &&
                    !pName.contains("Overnight", ignoreCase = true)
                val tLabel = tempLabels?.optString(i)?.ifBlank { if (isDay) "High" else "Low" }
                    ?: if (isDay) "High" else "Low"
                add(
                    ForecastPeriod(
                        name = pName,
                        startTimeIso = startIso,
                        isDaytime = isDay,
                        temperatureF = temps?.optString(i)?.toIntOrNull(),
                        tempLabel = tLabel,
                        popPct = pops?.optString(i)?.toIntOrNull(),
                        weather = weathers?.optString(i).orEmpty(),
                        detailedForecast = texts?.optString(i).orEmpty(),
                        iconCode = IconMapper.codeFrom(icons?.optString(i)),
                    ),
                )
            }
        }

        val sun = SunCalculator.times(latitude, longitude)
        val days = buildDays(periods, sun.sunrise, sun.sunset)
        // Anchor hourly timeline to MapClick period starts — digitalJSON time/unixtime
        // labels are often wrong (e.g. "6 pm" + bad unix for data that is actually 10 pm).
        val hourlyAnchorEpochSec = periods.firstOrNull()?.startTimeIso
            ?.let { parseIso(it)?.time }
            ?.let { it / 1000L }
        val hourly = mergeHourlyExtras(
            mergeTides(
                parseHourly(digital, grid, hourlyAnchorEpochSec),
                tides.heightByEpochHour,
            ),
            grid,
            extras,
        )
        val mapClickHazards = parseMapClickHazards(data)
        val hazards = mergeHazards(apiAlerts, mapClickHazards)
        val tideInfo = tides.station?.let { st ->
            val kind = when (st.kind) {
                TideService.StationKind.TidePrediction -> "tide"
                TideService.StationKind.WaterLevel -> "waterlevel"
                TideService.StationKind.UsgsGage -> "usgs"
            }
            TideInfo(
                stationId = st.id,
                stationName = st.name,
                distanceMiles = tides.distanceMiles ?: 0.0,
                unavailableReason = tides.unavailableReason,
                sourceKind = kind,
                datumLabel = tides.datumLabel,
            )
        } ?: tides.unavailableReason?.let {
            TideInfo(
                stationId = "",
                stationName = "",
                distanceMiles = tides.distanceMiles ?: 0.0,
                unavailableReason = it,
            )
        }

        return WeatherSnapshot(
            locationName = name,
            latitude = latitude,
            longitude = longitude,
            elevationFt = elevObs,
            updatedAtEpochMs = System.currentTimeMillis(),
            observationTimeLabel = obsDate,
            current = current,
            periods = periods,
            days = days,
            hourly = hourly,
            sunrise = sun.sunrise,
            sunset = sun.sunset,
            hazards = hazards,
            tideInfo = tideInfo,
        )
    }

    private fun mergeTides(
        rows: List<HourlyRow>,
        heightByEpochHour: Map<Long, Double>,
    ): List<HourlyRow> {
        if (rows.isEmpty() || heightByEpochHour.isEmpty()) return rows
        return rows.map { row ->
            val epoch = row.epochSec ?: return@map row
            val hour = (epoch / 3600L) * 3600L
            val height = heightByEpochHour[hour]
            val prev = heightByEpochHour[hour - 3600L]
            val trend = when {
                height == null || prev == null -> null
                height > prev + 0.02 -> "Rising"
                height < prev - 0.02 -> "Falling"
                else -> "Steady"
            }
            row.copy(tideFt = height, tideTrend = trend)
        }
    }

    /** Attach visibility (NWS preferred), pressure, UV, and AQI to hourly rows. */
    private fun mergeHourlyExtras(
        rows: List<HourlyRow>,
        grid: GridSeries,
        extras: OpenMeteoService.HourlyExtras,
    ): List<HourlyRow> {
        if (rows.isEmpty()) return rows
        return rows.map { row ->
            val hour = row.epochSec?.let { (it / 3600L) * 3600L } ?: return@map row
            val vis = grid.visibilityMi[hour] ?: extras.visibilityMi[hour]
            val pressure = extras.pressureMb[hour]
            val uv = extras.uvIndex[hour]
            val aqi = extras.usAqi[hour]
            val pm = extras.pm25[hour]
            if (vis == null && pressure == null && uv == null && aqi == null && pm == null) {
                return@map row
            }
            row.copy(
                visibilityMi = vis,
                pressureMb = pressure,
                uvIndex = uv,
                usAqi = aqi,
                pm25 = pm,
            )
        }
    }

    private fun buildDays(
        periods: List<ForecastPeriod>,
        sunrise: String,
        sunset: String,
    ): List<DayForecast> {
        val result = mutableListOf<DayForecast>()
        var i = 0
        while (i < periods.size) {
            val p = periods[i]
            if (p.isDaytime) {
                val night = periods.getOrNull(i + 1)?.takeIf { !it.isDaytime }
                val dayName = shortDayName(p.name, p.startTimeIso)
                val dateLabel = formatDateLabel(p.startTimeIso) ?: p.name
                result += DayForecast(
                    dayName = dayName,
                    dateLabel = dateLabel,
                    highF = p.temperatureF,
                    lowF = night?.temperatureF,
                    popPct = listOfNotNull(p.popPct, night?.popPct).maxOrNull(),
                    summary = p.weather.ifBlank { p.detailedForecast },
                    detailed = buildString {
                        append(p.detailedForecast)
                        if (!night?.detailedForecast.isNullOrBlank()) {
                            if (isNotEmpty()) append(' ')
                            append(night!!.detailedForecast)
                        }
                    },
                    iconCode = p.iconCode,
                    sunrise = sunrise,
                    sunset = sunset,
                )
                i += if (night != null) 2 else 1
            } else {
                val dayName = shortDayName(p.name, p.startTimeIso)
                result += DayForecast(
                    dayName = dayName,
                    dateLabel = formatDateLabel(p.startTimeIso) ?: p.name,
                    highF = null,
                    lowF = p.temperatureF,
                    popPct = p.popPct,
                    summary = p.weather.ifBlank { p.detailedForecast },
                    detailed = p.detailedForecast,
                    iconCode = p.iconCode,
                    sunrise = sunrise,
                    sunset = sunset,
                )
                i += 1
            }
        }
        return result
    }

    /**
     * Parse MapClick digitalJSON hourly rows.
     *
     * NWS digitalJSON often ships **incorrect** `time` labels and `unixtime` values
     * (e.g. PoP sequence that weather.gov shows at 10pm–midnight labeled "6 pm"–"8 pm"
     * with unix stamps a day later). PoP / temp / humidity arrays are correct in order.
     *
     * We therefore re-stamp hours as a continuous series starting at
     * [hourlyAnchorEpochSec] (MapClick first period startValidTime), which matches
     * weather.gov's Hour (EDT) row and api.weather.gov forecastHourly.
     */
    private fun parseHourly(
        digital: JSONObject?,
        grid: GridSeries,
        hourlyAnchorEpochSec: Long?,
    ): List<HourlyRow> {
        if (digital == null) return emptyList()
        val rows = mutableListOf<HourlyRow>()
        val skip = setOf(
            "operationalMode", "srsName", "creationDate", "productionCenter",
            "credit", "moreInformation", "location", "PeriodNumberList", "PeriodNameList",
        )

        val orderedKeys = digitalPeriodKeys(digital, skip)
        // Continuous hour cursor — digital periods are contiguous hourly slots
        var nextEpoch = hourlyAnchorEpochSec

        for (key in orderedKeys) {
            if (key in skip) continue
            val period = digital.optJSONObject(key) ?: continue
            val times = period.optJSONArray("time") ?: continue
            val unix = period.optJSONArray("unixtime")
            val temps = period.optJSONArray("temperature")
            val pops = period.optJSONArray("pop")
            val winds = period.optJSONArray("windSpeed")
            val gusts = period.optJSONArray("windGust")
            val dirs = period.optJSONArray("windDirectionCardinal")
            val clouds = period.optJSONArray("cloudAmount")
            val humidity = period.optJSONArray("relativeHumidity")
            val icons = period.optJSONArray("iconLink")
            val weather = period.optJSONArray("weather")
            val periodName = humanizePeriodName(period.optString("periodName", key))

            for (i in 0 until times.length()) {
                val temp = optInt(temps, i)
                val rh = optInt(humidity, i)
                val wspd = optInt(winds, i)
                val digitalGust = optInt(gusts, i)

                // Prefer anchored timeline; fall back to (unreliable) unixtime only if needed
                val epochSec = nextEpoch ?: optLong(unix, i)
                if (epochSec != null) {
                    nextEpoch = epochSec + 3600L
                }
                val epochHour = epochSec?.let { (it / 3600L) * 3600L }

                val gridGust = epochHour?.let { grid.gustMph[it]?.roundToInt() }
                val gust = digitalGust ?: gridGust

                val dew = epochHour?.let { grid.dewPointF[it]?.roundToInt() }
                    ?: WeatherMath.dewPointF(temp, rh)

                val precipIn = epochHour?.let { hour ->
                    grid.qpfInches[hour]?.let { inches ->
                        if (inches <= 0.0) null
                        else String.format(Locale.US, "%.2f in", inches)
                    }
                }

                val pop = optInt(pops, i)
                val tLabel = epochSec?.let { formatHourClock(it) } ?: times.optString(i)

                rows += HourlyRow(
                    periodLabel = periodName,
                    timeLabel = tLabel,
                    temperatureF = temp,
                    feelsLikeF = WeatherMath.feelsLikeF(temp, rh, wspd),
                    dewPointF = dew,
                    popPct = pop,
                    precipIn = precipIn,
                    cloudCoverPct = optInt(clouds, i),
                    humidityPct = rh,
                    windSpeedMph = wspd,
                    windGustMph = gust,
                    windDirection = dirs?.optString(i)?.takeIf { it.isNotBlank() && it != "null" },
                    weather = weather?.optString(i).orEmpty(),
                    iconCode = IconMapper.codeFrom(icons?.optString(i)),
                    epochSec = epochSec,
                )
            }
        }

        return rows.sortedBy { it.epochSec ?: Long.MAX_VALUE }
    }

    /** Ordered digitalJSON period object keys (Tonight, Monday, …). */
    private fun digitalPeriodKeys(digital: JSONObject, skip: Set<String>): List<String> {
        val orderedKeys = mutableListOf<String>()
        // PeriodNameList may be a JSON array or an object keyed "0","1",…
        digital.optJSONArray("PeriodNameList")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optString(i)
                val key = name.replace(" ", "")
                if (digital.has(key)) orderedKeys += key
                else if (digital.has(name)) orderedKeys += name
            }
        }
        if (orderedKeys.isEmpty()) {
            digital.optJSONObject("PeriodNameList")?.let { obj ->
                val idxs = obj.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted()
                for (i in idxs) {
                    val name = obj.optString(i.toString())
                    if (name.isBlank()) continue
                    val key = name.replace(" ", "")
                    if (digital.has(key)) orderedKeys += key
                    else if (digital.has(name)) orderedKeys += name
                }
            }
        }
        if (orderedKeys.isEmpty()) {
            val keys = digital.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in skip && digital.optJSONObject(key)?.optJSONArray("time") != null) {
                    orderedKeys += key
                }
            }
        }
        return orderedKeys
    }

    /** Local clock label for an epoch second (device default zone — same as hourly UI). */
    private fun formatHourClock(epochSec: Long): String {
        val fmt = SimpleDateFormat("h a", Locale.US)
        return fmt.format(Date(epochSec * 1000L))
            .replace("AM", "am")
            .replace("PM", "pm")
    }

    private fun optInt(arr: JSONArray?, index: Int): Int? {
        if (arr == null || index >= arr.length()) return null
        if (arr.isNull(index)) return null
        val s = arr.optString(index)
        if (s.isBlank() || s.equals("null", ignoreCase = true)) return null
        return s.toIntOrNull() ?: arr.optDouble(index, Double.NaN).takeIf { !it.isNaN() }?.roundToInt()
    }

    private fun optLong(arr: JSONArray?, index: Int): Long? {
        if (arr == null || index >= arr.length()) return null
        if (arr.isNull(index)) return null
        val s = arr.optString(index)
        if (s.isBlank() || s.equals("null", ignoreCase = true)) return null
        return s.toLongOrNull()
    }

    private fun humanizePeriodName(raw: String): String {
        return raw
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace("Night", " Night")
            .replace("  ", " ")
            .trim()
    }

    private fun shortDayName(periodName: String, startIso: String?): String {
        val base = periodName
            .removeSuffix(" Night")
            .removeSuffix(" Afternoon")
            .removePrefix("This ")
        // Keep Tonight/Overnight as distinct labels. After midnight NWS often
        // emits Overnight + Friday for the same calendar day — mapping both to
        // "Fri" produced duplicate LazyColumn keys and crashed the UI.
        return when {
            base.equals("Tonight", ignoreCase = true) -> "Tonight"
            base.equals("Overnight", ignoreCase = true) -> "Overnight"
            base.equals("Today", ignoreCase = true) || base.equals("This", ignoreCase = true) ->
                formatWeekday(startIso) ?: "Today"
            else -> base.take(3)
        }
    }

    private fun formatWeekday(iso: String?): String? {
        val date = parseIso(iso) ?: return null
        return SimpleDateFormat("EEE", Locale.US).format(date)
    }

    private fun formatDateLabel(iso: String?): String? {
        val parsed = parseIso(iso) ?: return null
        return SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US).format(parsed)
    }

    private fun parseIso(iso: String?): Date? {
        if (iso.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.US).parse(iso)
            } catch (_: Exception) {
            }
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.take(19))
        } catch (_: Exception) {
            null
        }
    }

    private fun shortState(state: String): String {
        val trimmed = state.trim()
        if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
            return trimmed.uppercase(Locale.US)
        }
        val map = mapOf(
            "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
            "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
            "florida" to "FL", "georgia" to "GA", "hawaii" to "HI", "idaho" to "ID",
            "illinois" to "IL", "indiana" to "IN", "iowa" to "IA", "kansas" to "KS",
            "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME", "maryland" to "MD",
            "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN", "mississippi" to "MS",
            "missouri" to "MO", "montana" to "MT", "nebraska" to "NE", "nevada" to "NV",
            "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM", "new york" to "NY",
            "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH", "oklahoma" to "OK",
            "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI", "south carolina" to "SC",
            "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX", "utah" to "UT",
            "vermont" to "VT", "virginia" to "VA", "washington" to "WA", "west virginia" to "WV",
            "wisconsin" to "WI", "wyoming" to "WY", "district of columbia" to "DC",
        )
        return map[trimmed.lowercase(Locale.US)] ?: trimmed
    }

    private fun getJson(url: String): JSONObject = JSONObject(getString(url))

    private fun getString(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/geo+json,application/json,text/plain,*/*")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} for $url")
            }
            return resp.body?.string() ?: throw IllegalStateException("Empty body")
        }
    }

    private data class GridSeries(
        val qpfInches: Map<Long, Double>,
        val gustMph: Map<Long, Double>,
        val dewPointF: Map<Long, Double>,
        val visibilityMi: Map<Long, Double> = emptyMap(),
    ) {
        companion object {
            val EMPTY = GridSeries(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        }
    }

    companion object {
        const val USER_AGENT =
            "PointForecast/1.1.3 (Android; open-source; https://github.com/crome1394/point-forecast)"

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
