package com.crome.forecastpoint.util

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

/**
 * Shared osmdroid helpers: single-finger pan in scroll parents, radius-based zoom.
 */
object MapHelpers {

    /**
     * Allow one-finger pan/zoom on a map embedded in a vertical scroll parent
     * (Compose verticalScroll / LazyColumn) by blocking parent intercept while touching the map.
     */
    fun enableSingleFingerPanInScrollParent(mapView: MapView) {
        fun disallowParents(v: View, disallow: Boolean) {
            var p = v.parent
            while (p is ViewGroup) {
                p.requestDisallowInterceptTouchEvent(disallow)
                p = p.parent
            }
        }
        mapView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_POINTER_DOWN,
                -> {
                    // Own the gesture so Compose drawer / scroll parents do not steal pan
                    disallowParents(v, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    disallowParents(v, false)
                }
            }
            false // map still handles the event
        }
    }

    /**
     * Zoom so roughly [radiusMiles] in each direction is visible around [lat],[lon].
     * Uses a bounding box (diameter = 2 × radius).
     */
    fun zoomToRadiusMiles(
        mapView: MapView,
        lat: Double,
        lon: Double,
        radiusMiles: Double,
        animate: Boolean = false,
    ) {
        val r = radiusMiles.coerceIn(25.0, 1500.0)
        val latDelta = r / 69.0
        val lonDelta = r / (69.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.2))
        val box = BoundingBox(
            (lat + latDelta).coerceIn(-85.0, 85.0),
            (lon + lonDelta).coerceIn(-180.0, 180.0),
            (lat - latDelta).coerceIn(-85.0, 85.0),
            (lon - lonDelta).coerceIn(-180.0, 180.0),
        )
        try {
            mapView.zoomToBoundingBox(box, animate, 48)
        } catch (_: Exception) {
            // Fallback approximate zoom
            mapView.controller.setCenter(GeoPoint(lat, lon))
            mapView.controller.setZoom(approxZoomForRadiusMiles(r))
        }
    }

    /** Rough web-mercator zoom for a radius (miles) when bounding box fails. */
    fun approxZoomForRadiusMiles(radiusMiles: Double): Double {
        val diameterDeg = (radiusMiles * 2.0) / 69.0
        // world width 360° at zoom 0
        val z = ln(360.0 / max(diameterDeg, 0.05)) / ln(2.0)
        return z.coerceIn(3.0, 14.0)
    }
}
