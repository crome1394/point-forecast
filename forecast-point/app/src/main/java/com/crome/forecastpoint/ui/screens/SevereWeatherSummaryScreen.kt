package com.crome.forecastpoint.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.data.SevereWeatherService
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.MapHelpers
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/** Light basemap for readable labels / roads on hazard maps. */
private val CartoLightTiles: OnlineTileSourceBase = object : XYTileSource(
    "CartoPositronStorms",
    1,
    18,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/light_all/",
        "https://b.basemaps.cartocdn.com/light_all/",
        "https://c.basemaps.cartocdn.com/light_all/",
        "https://d.basemaps.cartocdn.com/light_all/",
    ),
    "© OpenStreetMap © CARTO",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
    }
}

@Composable
fun SevereWeatherSummaryScreen(
    latitude: Double,
    longitude: Double,
    locationName: String?,
    snapshot: SevereWeatherService.Snapshot?,
    loading: Boolean,
    mapFocusRadiusMiles: Int = PreferencesRepository.DEFAULT_MAP_FOCUS_RADIUS_MILES,
) {
    var aboutExpanded by remember { mutableStateOf(false) }
    var mapFullscreen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "NOAA NHC · SPC · NWS alerts",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "Tornado & hurricane context for ${locationName ?: "selected location"}",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (loading && snapshot == null) {
            CircularProgressIndicator(
                Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFFFF7043),
            )
            Text(
                "Loading severe weather…",
                color = OnSurfaceMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
            )
            return
        }

        snapshot?.querySummary?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = OnSurfaceMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            StormMap(
                centerLat = latitude,
                centerLon = longitude,
                storms = snapshot?.tropicalStorms.orEmpty(),
                tornadoes = snapshot?.tornadoReports.orEmpty().take(25),
                focusRadiusMiles = mapFocusRadiusMiles,
                onExpandFullscreen = { mapFullscreen = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (mapFullscreen) {
            Dialog(
                onDismissRequest = { mapFullscreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    StormMap(
                        centerLat = latitude,
                        centerLon = longitude,
                        storms = snapshot?.tropicalStorms.orEmpty(),
                        tornadoes = snapshot?.tornadoReports.orEmpty().take(25),
                        focusRadiusMiles = mapFocusRadiusMiles,
                        onExpandFullscreen = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = { mapFullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close full screen", tint = Color.White)
                    }
                }
            }
        }

        Text(
            "Map focus ${mapFocusRadiusMiles} mi from city (Settings) · one-finger pan · expand for full screen",
            color = OnSurfaceMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Local alerts
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Local watches & warnings", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("NWS alerts for this point (tornado / tropical)", color = OnSurfaceMuted, fontSize = 12.sp)
                    }
                }
                val alerts = snapshot?.localAlerts.orEmpty()
                if (alerts.isEmpty()) {
                    Text(
                        "No active tornado or tropical alerts for this location right now.",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    alerts.forEach { a ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(a.event, color = Color(0xFFFFAB91), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(a.headline, color = Color.White, fontSize = 13.sp)
                            if (a.areaDesc.isNotBlank()) {
                                Text(a.areaDesc, color = OnSurfaceMuted, fontSize = 12.sp)
                            }
                            Text(
                                "Severity: ${a.severity}",
                                color = OnSurfaceMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Active tropical cyclones
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Air, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Active tropical cyclones", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "NOAA NHC · within ${mapFocusRadiusMiles} mi of city (Settings → Map focus)",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                val storms = snapshot?.tropicalStorms.orEmpty()
                if (storms.isEmpty()) {
                    Text(
                        "No active tropical cyclones within ${mapFocusRadiusMiles} mi of this city. " +
                            "Widen Map focus radius in Settings, or check NHC if storms are elsewhere.",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    storms.forEach { s ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = s.advisoryUrl != null) {
                                    s.advisoryUrl?.let { uriHandler.openUri(it) }
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                "${s.name} (${s.classification})",
                                color = Color(0xFF81D4FA),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            val wind = s.intensityKt?.let { "$it kt" } ?: "—"
                            val mb = s.pressureMb?.let { "$it mb" } ?: "—"
                            Text(
                                "Winds $wind · Pressure $mb · ${"%.0f".format(Locale.US, s.distanceMiles)} mi from city",
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                            val move = buildString {
                                s.movementDir?.let { append("Moving $it°") }
                                s.movementSpeedKt?.let {
                                    if (isNotEmpty()) append(" at ")
                                    append("$it kt")
                                }
                            }
                            if (move.isNotBlank()) {
                                Text(move, color = OnSurfaceMuted, fontSize = 12.sp)
                            }
                            s.lastUpdate?.let {
                                Text("Updated $it", color = OnSurfaceMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SPC tornado reports
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Thunderstorm, null, tint = Color(0xFFE57373), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Recent tornado reports", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            "SPC preliminary · last 7 days · within ${mapFocusRadiusMiles} mi of city",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                val reports = snapshot?.tornadoReports.orEmpty()
                if (reports.isEmpty()) {
                    Text(
                        "No SPC tornado reports within ${mapFocusRadiusMiles} mi in the last 7 days.",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text("EF", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                        Text("Report", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("Mi", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                    }
                    reports.forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                r.fScale,
                                color = Color(0xFFFFAB91),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.width(36.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${r.location}, ${r.state}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "${r.county} · UTC ${r.timeLabel}",
                                    color = OnSurfaceMuted,
                                    fontSize = 11.sp,
                                )
                                if (r.comments.isNotBlank()) {
                                    Text(r.comments, color = Color(0xFFB0BEC5), fontSize = 11.sp, maxLines = 2)
                                }
                            }
                            Text(
                                String.format(Locale.US, "%.0f", r.distanceMiles),
                                color = OnSurfaceMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.width(36.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { aboutExpanded = !aboutExpanded },
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "About tornado & hurricane data",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (aboutExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = OnSurfaceMuted,
                    )
                }
                AnimatedVisibility(visible = aboutExpanded) {
                    Text(
                        "Sources (official NOAA products)\n" +
                            "• NHC CurrentStorms.json — active tropical cyclones (Atlantic, E. Pacific, C. Pacific).\n" +
                            "• NWS api.weather.gov alerts for your lat/lon — tornado / tropical watches & warnings.\n" +
                            "• SPC daily tornado report CSVs — preliminary local storm reports (not a forecast).\n\n" +
                            "Distance filter\n" +
                            "Active tropical cyclones and SPC tornado reports are limited to the " +
                            "Map focus radius (Settings → Map), same as the map zoom (default 250 mi). " +
                            "Storms farther away are omitted from the list and map markers.\n\n" +
                            "Validation\n" +
                            "Storm names, intensity, and positions match NHC’s public JSON. " +
                            "Alert events match NWS CAP for the point. SPC rows match " +
                            "spc.noaa.gov/climo/reports for each day.\n\n" +
                            "Limits\n" +
                            "SPC reports are preliminary and can be revised. Distance is great-circle " +
                            "from your selected city to the storm center or report lat/lon. " +
                            "This is not a substitute for official NWS warnings — heed local alerts.",
                        color = Color(0xFFCFD8DC),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StormMap(
    centerLat: Double,
    centerLon: Double,
    storms: List<SevereWeatherService.TropicalStorm>,
    tornadoes: List<SevereWeatherService.TornadoReport>,
    focusRadiusMiles: Int,
    onExpandFullscreen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = context.cacheDir
        Configuration.getInstance().osmdroidTileCache = context.cacheDir.resolve("osmdroid")
        MapView(context).apply {
            setTileSource(CartoLightTiles)
            setMultiTouchControls(true)
            setFlingEnabled(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setCenter(GeoPoint(centerLat, centerLon))
            MapHelpers.enableSingleFingerPanInScrollParent(this)
        }
    }

    LaunchedEffect(centerLat, centerLon, storms, tornadoes, focusRadiusMiles) {
        mapView.overlays.removeAll { it is Marker }
        mapView.overlays.add(
            Marker(mapView).apply {
                position = GeoPoint(centerLat, centerLon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Selected location"
            },
        )
        storms.forEach { s ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(s.latitude, s.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "${s.name} (${s.classification})"
                    snippet = "${s.intensityKt ?: "—"} kt · ${"%.0f".format(s.distanceMiles)} mi"
                },
            )
        }
        tornadoes.forEach { t ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(t.latitude, t.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Tornado ${t.fScale} · ${t.location}"
                    snippet = "${t.state} · ${"%.0f".format(t.distanceMiles)} mi"
                },
            )
        }
        MapHelpers.zoomToRadiusMiles(
            mapView,
            centerLat,
            centerLon,
            focusRadiusMiles.toDouble(),
            animate = false,
        )
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color(0xEEFFFFFF),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Place, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Focus ${focusRadiusMiles} mi · one-finger pan",
                    color = Color(0xFF263238),
                    fontSize = 11.sp,
                )
            }
        }
        if (onExpandFullscreen != null) {
            Surface(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                color = Color(0xEEFFFFFF),
                shape = RoundedCornerShape(20.dp),
            ) {
                IconButton(onClick = onExpandFullscreen) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "Expand map full screen",
                        tint = Color(0xFF263238),
                    )
                }
            }
        }
    }
}
