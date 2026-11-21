package com.crome.forecastpoint.data

import com.crome.forecastpoint.util.WeatherMath
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Converts documented api.weather.gov responses into the MapClick JSON shapes
 * that [NwsApi] already parses, so the app keeps working when the undocumented
 * forecast.weather.gov MapClick endpoints fail or are retired.
 *
 * - [mapClickRoot] builds the `FcstType=json` shape from `/points`,
 *   `/gridpoints/.../forecast`, and `/stations/{id}/observations/latest`.
 * - [digitalFromHourly] builds the `FcstType=digitalJSON` shape from
 *   `/gridpoints/.../forecast/hourly`.
 *
 * Units follow MapClick: current observations in US units; period temperatures
 * and forecast text in the requested system (caller asks the API for `units=si`
 * when metric); digital temperatures in the requested system, wind in mph.
 */
internal object NwsApiFallback {

    fun mapClickRoot(
        points: JSONObject,
        forecast: JSONObject,
        observation: JSONObject?,
    ): JSONObject {
        val pointProps = points.optJSONObject("properties") ?: JSONObject()
        val fcProps = forecast.optJSONObject("properties") ?: JSONObject()
        val periods = fcProps.optJSONArray("periods") ?: JSONArray()

        val location = JSONObject()
        areaDescription(pointProps)?.let { location.put("areaDescription", it) }
        fcProps.optJSONObject("elevation")?.let(::quantity)?.let {
            location.put("elevation", metersToFeet(it).toString())
        }
        // IANA id; LocationTimeZone accepts it alongside MapClick's "E|Y|5" codes.
        pointProps.optString("timeZone").takeIf { it.isNotBlank() }?.let {
            location.put("timezone", it)
        }

        val names = JSONArray()
        val starts = JSONArray()
        val labels = JSONArray()
        val temps = JSONArray()
        val pops = JSONArray()
        val weather = JSONArray()
        val icons = JSONArray()
        val text = JSONArray()
        for (i in 0 until periods.length()) {
            val p = periods.optJSONObject(i) ?: continue
            names.put(p.optString("name"))
            starts.put(p.optString("startTime"))
            labels.put(if (p.optBoolean("isDaytime", true)) "High" else "Low")
            temps.put(if (p.isNull("temperature")) JSONObject.NULL else p.optInt("temperature").toString())
            val pop = p.optJSONObject("probabilityOfPrecipitation")?.let(::quantity)
            pops.put(pop?.roundToInt()?.toString() ?: JSONObject.NULL)
            weather.put(p.optString("shortForecast"))
            icons.put(p.optString("icon"))
            text.put(p.optString("detailedForecast"))
        }

        val root = JSONObject()
            .put("location", location)
            .put(
                "time",
                JSONObject()
                    .put("startPeriodName", names)
                    .put("startValidTime", starts)
                    .put("tempLabel", labels),
            )
            .put(
                "data",
                JSONObject()
                    .put("temperature", temps)
                    .put("pop", pops)
                    .put("weather", weather)
                    .put("iconLink", icons)
                    .put("text", text)
                    // CAP alerts come from /alerts/active; nothing to fall back to here.
                    .put("hazard", JSONArray())
                    .put("hazardUrl", JSONArray()),
            )
        observation?.let {
            root.put(
                "currentobservation",
                currentObservation(it, pointProps.optString("timeZone").takeIf { tz -> tz.isNotBlank() }),
            )
        }
        return root
    }

    /**
     * From `/stations/{id}/observations` (newest first), the newest report with a
     * temperature. Falls back to the newest report when none has one.
     */
    fun pickObservation(collection: JSONObject): JSONObject? {
        val features = collection.optJSONArray("features") ?: return null
        var newest: JSONObject? = null
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            if (newest == null) newest = f
            if (f.optJSONObject("properties")?.q("temperature") != null) return f
        }
        return newest
    }

    /** One station observation feature (SI) → MapClick currentobservation (US). */
    fun currentObservation(observation: JSONObject, timeZoneId: String? = null): JSONObject {
        val o = observation.optJSONObject("properties") ?: JSONObject()
        val out = JSONObject()
        fun putInt(key: String, value: Double?) {
            value?.let { out.put(key, it.roundToInt().toString()) }
        }
        putInt("Temp", o.q("temperature")?.let { WeatherMath.celsiusToF(it).toDouble() })
        putInt("Dewp", o.q("dewpoint")?.let { WeatherMath.celsiusToF(it).toDouble() })
        putInt("Relh", o.q("relativeHumidity"))
        putInt("Winds", o.q("windSpeed")?.let { WeatherMath.kmhToMph(it).toDouble() })
        putInt("Windd", o.q("windDirection"))
        o.q("visibility")?.let {
            out.put("Visibility", String.format(Locale.US, "%.2f", WeatherMath.metersToMiles(it)))
        }
        (o.q("seaLevelPressure") ?: o.q("barometricPressure"))?.let { pa ->
            out.put("SLP", String.format(Locale.US, "%.2f", WeatherMath.hPaToInHg(pa / 100.0)))
        }
        o.optString("textDescription").takeIf { it.isNotBlank() }?.let { out.put("Weather", it) }
        o.optString("icon").takeIf { it.isNotBlank() }?.let { out.put("Weatherimage", it) }
        o.optString("stationName").takeIf { it.isNotBlank() }?.let { out.put("name", it) }
        o.optString("stationId").takeIf { it.isNotBlank() }?.let { out.put("id", it) }
        o.optJSONObject("elevation")?.let(::quantity)?.let {
            out.put("elev", metersToFeet(it).toString())
        }
        o.optString("timestamp").takeIf { it.isNotBlank() }?.let { out.put("Date", observedLabel(it, timeZoneId)) }
        return out
    }

    /**
     * `/forecast/hourly` (requested in US units) → digitalJSON with one period.
     * `unixtime` is accurate here, so the caller must not re-anchor these rows.
     */
    fun digitalFromHourly(hourly: JSONObject, metric: Boolean): JSONObject {
        val periods = hourly.optJSONObject("properties")?.optJSONArray("periods") ?: JSONArray()
        val time = JSONArray()
        val unix = JSONArray()
        val temp = JSONArray()
        val pop = JSONArray()
        val wind = JSONArray()
        val dir = JSONArray()
        val rh = JSONArray()
        val icon = JSONArray()
        val weather = JSONArray()
        for (i in 0 until periods.length()) {
            val p = periods.optJSONObject(i) ?: continue
            val epoch = runCatching {
                OffsetDateTime.parse(p.optString("startTime")).toEpochSecond()
            }.getOrNull() ?: continue
            time.put("")
            unix.put(epoch.toString())
            val t = if (p.isNull("temperature")) null else p.optDouble("temperature", Double.NaN)
                .takeIf { !it.isNaN() }
                ?.let { if (p.optString("temperatureUnit") == "C") WeatherMath.celsiusToF(it).toDouble() else it }
            temp.put(
                t?.let { if (metric) WeatherMath.fahrenheitToC(it) else it.roundToInt() }?.toString()
                    ?: JSONObject.NULL,
            )
            pop.put(p.optJSONObject("probabilityOfPrecipitation")?.let(::quantity)?.roundToInt()?.toString() ?: JSONObject.NULL)
            wind.put(windMph(p.optString("windSpeed"))?.toString() ?: JSONObject.NULL)
            dir.put(p.optString("windDirection"))
            rh.put(p.optJSONObject("relativeHumidity")?.let(::quantity)?.roundToInt()?.toString() ?: JSONObject.NULL)
            icon.put(p.optString("icon"))
            weather.put(p.optString("shortForecast"))
        }
        val period = JSONObject()
            .put("periodName", "Hourly")
            .put("time", time)
            .put("unixtime", unix)
            .put("temperature", temp)
            .put("pop", pop)
            .put("windSpeed", wind)
            .put("windDirectionCardinal", dir)
            .put("relativeHumidity", rh)
            .put("iconLink", icon)
            .put("weather", weather)
        return JSONObject()
            .put("PeriodNameList", JSONArray().put("Hourly"))
            .put("Hourly", period)
    }

    /** "10 mph", "5 to 10 mph", "15 km/h" → upper value in mph. */
    fun windMph(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val value = Regex("""\d+(\.\d+)?""").findAll(raw).lastOrNull()?.value?.toDoubleOrNull()
            ?: return null
        return if (raw.contains("km", ignoreCase = true)) WeatherMath.kmhToMph(value) else value.roundToInt()
    }

    /** ISO timestamp → MapClick-style label in the location's zone, e.g. "20 Nov 11:05 pm EST". */
    fun observedLabel(isoTimestamp: String, timeZoneId: String?): String {
        val instant = runCatching { OffsetDateTime.parse(isoTimestamp).toInstant() }.getOrNull()
            ?: return isoTimestamp
        val zone = timeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        return DateTimeFormatter.ofPattern("d MMM h:mm a z", Locale.US)
            .format(instant.atZone(zone))
            .replace("AM", "am")
            .replace("PM", "pm")
    }

    private fun areaDescription(pointProps: JSONObject): String? {
        val rel = pointProps.optJSONObject("relativeLocation")?.optJSONObject("properties") ?: return null
        val city = rel.optString("city").takeIf { it.isNotBlank() } ?: return null
        val state = rel.optString("state")
        return if (state.isBlank()) city else "$city $state"
    }

    private fun quantity(o: JSONObject): Double? =
        if (o.isNull("value")) null else o.optDouble("value", Double.NaN).takeIf { !it.isNaN() }

    private fun JSONObject.q(key: String): Double? = optJSONObject(key)?.let(::quantity)

    private fun metersToFeet(m: Double): Int = (m / 0.3048).roundToInt()
}
