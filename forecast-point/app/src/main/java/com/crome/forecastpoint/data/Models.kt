package com.crome.forecastpoint.data

import kotlinx.serialization.Serializable

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean = true,
)

@Serializable
data class CurrentConditions(
    val temperatureF: Int?,
    val weather: String,
    val iconCode: String,
    val feelsLikeF: Int?,
    val humidityPct: Int?,
    val windDirection: String?,
    val windSpeedMph: Int?,
    val dewPointF: Int?,
    val visibilityMi: String?,
    val barometerInHg: String?,
    val barometerMb: String?,
    val stationName: String?,
    val observedAt: String?,
    val elevationFt: Int?,
)

/** Active NWS watch / warning / advisory for a point. */
@Serializable
data class WeatherHazard(
    val event: String,
    val headline: String?,
    val severity: String?,
    val urgency: String?,
    val description: String?,
    val instruction: String?,
    val url: String?,
)

@Serializable
data class ForecastPeriod(
    val name: String,
    val startTimeIso: String?,
    val isDaytime: Boolean,
    val temperatureF: Int?,
    val tempLabel: String,
    val popPct: Int?,
    val weather: String,
    val detailedForecast: String,
    val iconCode: String,
)

/** One calendar day: high/low + daytime summary. */
@Serializable
data class DayForecast(
    val dayName: String,
    val dateLabel: String,
    val highF: Int?,
    val lowF: Int?,
    val popPct: Int?,
    val summary: String,
    val detailed: String,
    val iconCode: String,
    val sunrise: String?,
    val sunset: String?,
)

@Serializable
data class HourlyRow(
    val periodLabel: String,
    val timeLabel: String,
    val temperatureF: Int?,
    val feelsLikeF: Int?,
    val dewPointF: Int?,
    val popPct: Int?,
    val precipIn: String?,
    val cloudCoverPct: Int?,
    val humidityPct: Int?,
    val windSpeedMph: Int?,
    val windGustMph: Int?,
    val windDirection: String?,
    val weather: String,
    val iconCode: String,
    val epochSec: Long? = null,
    /** Predicted tide height in feet (MLLW), if a nearby station exists. */
    val tideFt: Double? = null,
    /** Rising / Falling / Steady vs previous hour, when known. */
    val tideTrend: String? = null,
    /** Visibility in miles (NWS grid and/or Open-Meteo). */
    val visibilityMi: Double? = null,
    /** Surface pressure in millibars / hPa. */
    val pressureMb: Double? = null,
    /** UV index (0–11+). */
    val uvIndex: Double? = null,
    /** US AQI (0–500 scale). */
    val usAqi: Int? = null,
    /** PM2.5 µg/m³ when available. */
    val pm25: Double? = null,
)

@Serializable
data class TideInfo(
    val stationId: String,
    val stationName: String,
    val distanceMiles: Double,
    val unavailableReason: String? = null,
    /**
     * Source kind for UI:
     * - "tide" NOAA coastal predictions (MLLW)
     * - "waterlevel" NOAA Great Lakes / CO-OPS (LWD)
     * - "usgs" USGS river/lake gage height
     */
    val sourceKind: String = "tide",
    /** Datum / units label, e.g. "MLLW", "LWD", or "gage". */
    val datumLabel: String? = null,
) {
    val isWaterLevel: Boolean get() = sourceKind == "waterlevel" || sourceKind == "usgs"
}

@Serializable
data class WeatherSnapshot(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val elevationFt: Int?,
    val updatedAtEpochMs: Long,
    val observationTimeLabel: String?,
    val current: CurrentConditions,
    val periods: List<ForecastPeriod>,
    val days: List<DayForecast>,
    val hourly: List<HourlyRow>,
    val sunrise: String?,
    val sunset: String?,
    val hazards: List<WeatherHazard> = emptyList(),
    val tideInfo: TideInfo? = null,
    /** IANA or GMT offset id for the forecast point (from NWS when available). */
    val timeZoneId: String? = null,
)

data class GeocodeResult(
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)
