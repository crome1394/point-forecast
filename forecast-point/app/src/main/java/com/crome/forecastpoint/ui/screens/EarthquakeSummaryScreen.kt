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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
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
import com.crome.forecastpoint.data.EarthquakeService
import com.crome.forecastpoint.data.PreferencesRepository
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Light basemap for readable labels / roads on hazard maps. */
private val CartoLightTiles: OnlineTileSourceBase = object : XYTileSource(
    "CartoPositronQuake",
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
fun EarthquakeSummaryScreen(
    latitude: Double,
    longitude: Double,
    locationName: String?,
    snapshot: EarthquakeService.Snapshot?,
    loading: Boolean,
    mapFocusRadiusMiles: Int = PreferencesRepository.DEFAULT_MAP_FOCUS_RADIUS_MILES,
) {
    var aboutExpanded by remember { mutableStateOf(false) }
    var mapFullscreen by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val timeFmt = remember {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "USGS Earthquake Hazards Program",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "Near ${locationName ?: "selected location"} · map + regional list",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (loading && snapshot == null) {
            CircularProgressIndicator(
                Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFFFF8A65),
            )
            Text(
                "Loading earthquakes…",
                color = OnSurfaceMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
            )
            return
        }

        snapshot?.error?.let { err ->
            Text(
                err,
                color = Color(0xFFEF9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )
        }

        // Map centered on city with quake markers
        Surface(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            EarthquakeMap(
                centerLat = latitude,
                centerLon = longitude,
                recent = snapshot?.recentAll.orEmpty().take(40),
                historical = snapshot?.historical.orEmpty().take(12),
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
                    EarthquakeMap(
                        centerLat = latitude,
                        centerLon = longitude,
                        recent = snapshot?.recentAll.orEmpty().take(40),
                        historical = snapshot?.historical.orEmpty().take(12),
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
            (snapshot?.querySummary
                ?: "Search radius expands if the area is quiet (USGS FDSN)") +
                " · map focus ${mapFocusRadiusMiles} mi (Settings)",
            color = OnSurfaceMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        QuakeSection(
            title = "Nearest M2.5+ (30 days)",
            subtitle = "Matches many public maps that hide microquakes · nearest first",
            quakes = snapshot?.recentNotable.orEmpty(),
            timeFmt = timeFmt,
            emptyMessage = "No M2.5+ events in this search radius (30 days).",
            onOpen = { url -> uriHandler.openUri(url) },
        )

        Spacer(Modifier.height(12.dp))

        QuakeSection(
            title = "All M1.0+ (30 days)",
            subtitle = "Full USGS catalog including microquakes · nearest first",
            quakes = snapshot?.recentAll.orEmpty(),
            timeFmt = timeFmt,
            emptyMessage = "No M1.0+ events nearby — radius expands up to ~800 km for quiet regions.",
            onOpen = { url -> uriHandler.openUri(url) },
        )

        Spacer(Modifier.height(12.dp))

        QuakeSection(
            title = "Historical notable (10 years)",
            subtitle = "M4.0+ within ${mapFocusRadiusMiles} mi of city · strongest first",
            quakes = snapshot?.historical.orEmpty(),
            timeFmt = timeFmt,
            emptyMessage = "No M4.0+ events within ${mapFocusRadiusMiles} mi over the last 10 years.",
            onOpen = { url -> uriHandler.openUri(url) },
        )

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
                        "About earthquakes",
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
                        "Source of truth: USGS Earthquake Hazards Program FDSN API " +
                            "(earthquake.usgs.gov/fdsnws/event/1) — the same catalog used by " +
                            "earthquake.usgs.gov maps.\n\n" +
                            "Why other apps look different\n" +
                            "• Many apps default to M2.5+ or “significant only”; we also list M1.0+.\n" +
                            "• California (Sacramento, Hemet) has many small geothermal/tectonic " +
                            "events — large M1+ counts are expected and match USGS.\n" +
                            "• New York City often has zero M1+ within 200 km in a quiet month; " +
                            "we expand the radius (up to ~800 km) so you still see regional events " +
                            "(e.g. New England). That is USGS data, not a local gap in our app.\n\n" +
                            "What we show\n" +
                            "• M2.5+ (30 days) — nearest first — closer to common consumer maps\n" +
                            "• M1.0+ (30 days) — full regional catalog, nearest first\n" +
                            "• Historical M4.0+ (10 years) — strongest first\n\n" +
                            "Tap a row for the official USGS event page. Times use your device zone.",
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
private fun QuakeSection(
    title: String,
    subtitle: String,
    quakes: List<EarthquakeService.Quake>,
    timeFmt: SimpleDateFormat,
    emptyMessage: String,
    onOpen: (String) -> Unit,
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Public, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
            if (quakes.isEmpty()) {
                Text(emptyMessage, color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                // Header
                Row(Modifier.fillMaxWidth()) {
                    Text("Mag", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                    Text("When / where", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Mi", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                }
                quakes.forEach { q ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = q.url != null) {
                                q.url?.let(onOpen)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            q.magnitude?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                            color = magColor(q.magnitude),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.width(40.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                timeFmt.format(Date(q.timeEpochMs)),
                                color = Color(0xFFCFD8DC),
                                fontSize = 12.sp,
                            )
                            Text(
                                q.place,
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                            q.depthKm?.let {
                                Text(
                                    "Depth ${String.format(Locale.US, "%.1f", it)} km",
                                    color = OnSurfaceMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Text(
                            String.format(Locale.US, "%.0f", q.distanceMiles),
                            color = OnSurfaceMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EarthquakeMap(
    centerLat: Double,
    centerLon: Double,
    recent: List<EarthquakeService.Quake>,
    historical: List<EarthquakeService.Quake>,
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
            setMultiTouchControls(true) // pinch zoom; single-finger pan still works
            setFlingEnabled(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setCenter(GeoPoint(centerLat, centerLon))
            MapHelpers.enableSingleFingerPanInScrollParent(this)
        }
    }

    // Update markers + focus radius when data / settings change
    LaunchedEffect(centerLat, centerLon, recent, historical, focusRadiusMiles) {
        mapView.overlays.removeAll { it is Marker }
        mapView.overlays.add(
            Marker(mapView).apply {
                position = GeoPoint(centerLat, centerLon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Selected location"
            },
        )
        recent.forEach { q ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(q.latitude, q.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "M${q.magnitude} · ${q.place}"
                    snippet = "Recent"
                },
            )
        }
        historical.take(10).forEach { q ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(q.latitude, q.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "M${q.magnitude} · ${q.place}"
                    snippet = "Historical"
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
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
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

private fun magColor(mag: Double?): Color {
    if (mag == null) return Color.White
    return when {
        mag < 2.0 -> Color(0xFF81C784)
        mag < 3.5 -> Color(0xFFFFF176)
        mag < 5.0 -> Color(0xFFFFB74D)
        mag < 6.0 -> Color(0xFFFF8A65)
        else -> Color(0xFFEF5350)
    }
}
