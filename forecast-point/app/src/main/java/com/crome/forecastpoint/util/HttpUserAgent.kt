package com.crome.forecastpoint.util

import com.crome.forecastpoint.BuildConfig

/**
 * The one User-Agent for every request (NWS, NOAA, USGS, Open-Meteo, Nominatim,
 * OSM tiles). NWS and the OSM policies ask apps to identify themselves; using
 * the build's version name keeps it current without hand edits.
 */
object HttpUserAgent {
    const val VALUE =
        "PointForecast/${BuildConfig.VERSION_NAME} (Android; open-source; https://github.com/crome1394/point-forecast)"
}
