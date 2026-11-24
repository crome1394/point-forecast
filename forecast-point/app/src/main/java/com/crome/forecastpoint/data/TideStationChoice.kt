package com.crome.forecastpoint.data

import kotlinx.serialization.Serializable

/** Nearest gauge remembered for a saved (or transient) city. */
@Serializable
data class TideStationChoice(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Miles from the city to the gauge. */
    val distanceMiles: Double,
    /** "tide", "waterlevel" or "usgs". */
    val sourceKind: String = "tide",
    /** "MLLW", "LWD" or "gage". */
    val datumLabel: String? = "MLLW",
) {
    fun toTideStation(): TideService.TideStation = TideService.TideStation(
        id = id,
        name = name,
        lat = lat,
        lon = lon,
        kind = when (sourceKind) {
            "waterlevel" -> TideService.StationKind.WaterLevel
            "usgs" -> TideService.StationKind.UsgsGage
            else -> TideService.StationKind.TidePrediction
        },
    )

    companion object {
        fun from(
            station: TideService.TideStation,
            latitude: Double,
            longitude: Double,
        ): TideStationChoice = TideStationChoice(
            id = station.id,
            name = station.name,
            lat = station.lat,
            lon = station.lon,
            distanceMiles = TideStationSelector.distanceMiles(
                latitude,
                longitude,
                station.lat,
                station.lon,
            ),
            sourceKind = when (station.kind) {
                TideService.StationKind.WaterLevel -> "waterlevel"
                TideService.StationKind.UsgsGage -> "usgs"
                else -> "tide"
            },
            datumLabel = when (station.kind) {
                TideService.StationKind.WaterLevel -> "LWD"
                TideService.StationKind.UsgsGage -> "gage"
                else -> "MLLW"
            },
        )
    }
}
