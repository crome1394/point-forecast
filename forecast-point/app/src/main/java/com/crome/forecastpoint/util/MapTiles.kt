package com.crome.forecastpoint.util

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Shared raster basemap for all osmdroid maps.
 *
 * CARTO's free raster CDN (`basemaps.cartocdn.com`) started requiring an API key
 * (Aug 2026) and watermarks unauthenticated tiles. We use OpenStreetMap's
 * standard tile layer instead — no key, attribution required, identify the app
 * via [Configuration.userAgentValue] (see OSM tile usage policy).
 */
object MapTiles {
    /** Meaningful User-Agent for OSM tile / Nominatim etiquette. */
    const val USER_AGENT =
        "PointForecast/1.1.8 (Android; https://github.com/crome1394/point-forecast)"

    val OsmMapnik: OnlineTileSourceBase = object : XYTileSource(
        "Mapnik",
        0,
        19,
        256,
        ".png",
        arrayOf("https://tile.openstreetmap.org/"),
        "© OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
        }
    }

    fun configureOsmdroid(context: Context) {
        Configuration.getInstance().userAgentValue = USER_AGENT
        Configuration.getInstance().osmdroidBasePath = context.cacheDir
        // Separate cache dir so stale CARTO watermark tiles are not reused.
        Configuration.getInstance().osmdroidTileCache =
            context.cacheDir.resolve("osmdroid-osm")
    }
}
