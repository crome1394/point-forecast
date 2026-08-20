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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.crome.forecastpoint.R
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
import kotlin.math.roundToInt

private val QuakeAccent = Color(0xFFFF8A65)

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
    settingsDefaultRadiusMiles: Int = PreferencesRepository.DEFAULT_MAP_FOCUS_RADIUS_MILES,
    settingsDefaultHistoryDays: Int = PreferencesRepository.DEFAULT_HAZARD_HISTORY_DAYS,
    onExploreParams: (
        radiusMiles: Int,
        historyDays: Int,
        historyStartMs: Long?,
        historyEndMs: Long?,
    ) -> Unit = { _, _, _, _ -> },
) {
    var aboutExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var mapFullscreen by remember { mutableStateOf(false) }
    var exploreRadius by remember(settingsDefaultRadiusMiles) {
        mutableIntStateOf(settingsDefaultRadiusMiles)
    }
    var historyDays by remember(settingsDefaultHistoryDays) {
        mutableIntStateOf(settingsDefaultHistoryDays)
    }
    var customRangeActive by remember { mutableStateOf(false) }
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }
    var minMagnitude by remember { mutableFloatStateOf(1.0f) }
    val uriHandler = LocalUriHandler.current
    val timeFmt = remember {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }

    LaunchedEffect(latitude, longitude, exploreRadius, historyDays, customRangeActive, customStartMs, customEndMs) {
        if (customRangeActive && customStartMs != null && customEndMs != null) {
            val days = (
                ((customEndMs!! - customStartMs!!) / (24L * 3600L * 1000L)).toInt()
                ).coerceAtLeast(1)
            onExploreParams(exploreRadius, days, customStartMs, customEndMs)
        } else {
            onExploreParams(exploreRadius, historyDays, null, null)
        }
    }

    val filteredReports = remember(snapshot, minMagnitude) {
        snapshot?.recentAll
            ?.filter { (it.magnitude ?: 0.0) >= minMagnitude.toDouble() }
            ?.sortedByDescending { it.timeEpochMs }
            .orEmpty()
    }

    val magLabel = "M${String.format(Locale.US, "%.1f", minMagnitude)}+"
    val historySummary = if (customRangeActive && customStartMs != null && customEndMs != null) {
        val fmt = SimpleDateFormat("MMM d", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        "${fmt.format(java.util.Date(customStartMs!!))}–${fmt.format(java.util.Date(customEndMs!!))}"
    } else {
        formatHistoryDays(historyDays)
    }
    val settingsSummary = "$historySummary · $exploreRadius mi · $magLabel"

    fun resetHazardSettings() {
        exploreRadius = settingsDefaultRadiusMiles
        historyDays = settingsDefaultHistoryDays
        customRangeActive = false
        customStartMs = null
        customEndMs = null
        minMagnitude = 1.0f
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "U.S. Geological Survey (USGS) earthquake catalog",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "Earthquake context for ${locationName ?: "selected location"}",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (loading) {
            HazardLoadingBanner(
                message = if (snapshot == null) {
                    "Loading earthquakes…"
                } else {
                    "Updating earthquakes…"
                },
                accent = QuakeAccent,
            )
        }

        if (loading && snapshot == null) {
            CircularProgressIndicator(
                Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = QuakeAccent,
            )
            Text(
                "Fetching USGS catalog…",
                color = OnSurfaceMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
            )
            return
        }

        snapshot?.error?.let { err ->
            Text(err, color = Color(0xFFEF9A9A), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }

        // Map first (same order as severe weather)
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
                recent = filteredReports.take(50),
                historical = emptyList(),
                focusRadiusMiles = exploreRadius,
                onExpandFullscreen = { mapFullscreen = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (mapFullscreen) {
            Dialog(
                onDismissRequest = { mapFullscreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    EarthquakeMap(
                        centerLat = latitude,
                        centerLon = longitude,
                        recent = filteredReports.take(50),
                        historical = emptyList(),
                        focusRadiusMiles = exploreRadius,
                        onExpandFullscreen = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = { mapFullscreen = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(Icons.Filled.Close, "Close full screen", tint = Color.White)
                    }
                }
            }
        }

        // Collapsible Settings (explore radius, history, magnitude)
        HazardScreenSettingsSection(
            accent = QuakeAccent,
            expanded = settingsExpanded,
            onExpandedChange = { settingsExpanded = it },
            summary = settingsSummary,
            onReset = { resetHazardSettings() },
        ) {
            HazardExploreRadiusCard(
                exploreRadiusMiles = exploreRadius,
                settingsDefaultMiles = settingsDefaultRadiusMiles,
                onRadiusChange = { exploreRadius = it },
                accent = QuakeAccent,
                title = "Explore distance",
                subtitle = "Look around this city — temporary, not app Settings",
                compact = true,
            )
            HazardHistoryDaysCard(
                historyDays = historyDays,
                settingsDefaultDays = settingsDefaultHistoryDays,
                onPresetDaysChange = {
                    customRangeActive = false
                    customStartMs = null
                    customEndMs = null
                    historyDays = it
                },
                onCustomRangeChange = { start, end ->
                    customRangeActive = true
                    customStartMs = start
                    customEndMs = end
                    historyDays = (
                        ((end - start) / (24L * 3600L * 1000L)).toInt()
                        ).coerceAtLeast(1)
                },
                accent = Color(0xFF90CAF9),
                title = "History window",
                subtitle = "1d–6m stock · Custom for a calendar date range",
                compact = true,
                customRangeActive = customRangeActive,
                customStartMs = customStartMs,
                customEndMs = customEndMs,
            )
            HazardFilterSliderCard(
                title = "Minimum magnitude",
                valueLabel = magLabel,
                value = minMagnitude.coerceIn(1.0f, 6.0f),
                valueRange = 1.0f..6.0f,
                steps = 9,
                accent = QuakeAccent,
                onValueChange = { minMagnitude = (it * 2).roundToInt() / 2f },
                help = "Hide smaller quakes (M = strength / magnitude)",
                compact = true,
            )
        }

        Text(
            snapshot?.querySummary
                ?: "Within $exploreRadius mi · $historySummary · $magLabel",
            color = OnSurfaceMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Single reports list (history window + magnitude filter)
        QuakeSection(
            title = "Earthquake reports",
            subtitle = "USGS · $historySummary · within $exploreRadius mi · $magLabel · newest first",
            quakes = filteredReports,
            timeFmt = timeFmt,
            emptyMessage = "No quakes at this magnitude within $exploreRadius mi for $historySummary.",
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
                        "About earthquake data",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (aboutExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null,
                        tint = OnSurfaceMuted,
                    )
                }
                AnimatedVisibility(visible = aboutExpanded) {
                    Text(
                        "Data source\n" +
                            "U.S. Geological Survey (USGS) Earthquake Hazards Program — the same " +
                            "public catalog used on earthquake.usgs.gov. Modern FDSN coverage is " +
                            "multi-decade (this app offers history windows up to 10 years).\n\n" +
                            "Screen layout\n" +
                            "• Earthquake reports — one list for your history window and filters.\n" +
                            "  Long windows raise the catalog minimum magnitude (e.g. ~M4 for 10 years) " +
                            "and sample across time so results are not only last month’s tiny events.\n\n" +
                            "Terms\n" +
                            "• Magnitude (M) — how strong the quake was (e.g. M2.5 is small; M6 is " +
                            "major). Modern catalogs use moment magnitude.\n" +
                            "• Depth (km) — kilometers below the surface.\n" +
                            "• FDSN — Federation of Digital Seismograph Networks; USGS query API.\n" +
                            "• mi — miles from your selected city (straight-line).\n\n" +
                            "Settings on this screen\n" +
                            "Explore distance, history window, and magnitude filters are temporary. " +
                            "They do not change Settings → Map focus radius or Hazard history defaults.\n\n" +
                            "Tap a row for the official USGS event page.",
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
                Icon(Icons.Filled.Public, null, tint = QuakeAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
            if (quakes.isEmpty()) {
                Text(emptyMessage, color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Text("Mag", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                    Text("When / where", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Mi", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                }
                quakes.forEach { q ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = q.url != null) { q.url?.let(onOpen) }
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
                            Text(timeFmt.format(Date(q.timeEpochMs)), color = Color(0xFFCFD8DC), fontSize = 12.sp)
                            Text(q.place, color = Color.White, fontSize = 13.sp)
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
            setMultiTouchControls(true)
            setFlingEnabled(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setCenter(GeoPoint(centerLat, centerLon))
            MapHelpers.enableSingleFingerPanInScrollParent(this)
        }
    }

    LaunchedEffect(centerLat, centerLon, recent, historical, focusRadiusMiles) {
        mapView.overlays.removeAll { it is Marker }
        // Selected city: red push pin
        mapView.overlays.add(
            Marker(mapView).apply {
                position = GeoPoint(centerLat, centerLon)
                title = "Selected location"
                MapHelpers.applyPushPin(this, context, R.drawable.ic_map_selection_pin)
            },
        )
        // Reports: green push pins
        recent.forEach { q ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(q.latitude, q.longitude)
                    title = "M${q.magnitude} · ${q.place}"
                    snippet = "Earthquake report"
                    MapHelpers.applyPushPin(this, context, R.drawable.ic_map_report_pin)
                },
            )
        }
        historical.take(10).forEach { q ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(q.latitude, q.longitude)
                    title = "M${q.magnitude} · ${q.place}"
                    snippet = "Historical"
                    MapHelpers.applyPushPin(this, context, R.drawable.ic_map_report_pin)
                },
            )
        }
        MapHelpers.zoomToRadiusMiles(mapView, centerLat, centerLon, focusRadiusMiles.toDouble())
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
            Modifier.align(Alignment.TopStart).padding(8.dp),
            color = Color(0xEEFFFFFF),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Place, null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Red pin = city · green = reports · $focusRadiusMiles mi", color = Color(0xFF263238), fontSize = 11.sp)
            }
        }
        if (onExpandFullscreen != null) {
            Surface(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                color = Color(0xEEFFFFFF),
                shape = RoundedCornerShape(20.dp),
            ) {
                IconButton(onClick = onExpandFullscreen) {
                    Icon(Icons.Filled.Fullscreen, "Expand map full screen", tint = Color(0xFF263238))
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
