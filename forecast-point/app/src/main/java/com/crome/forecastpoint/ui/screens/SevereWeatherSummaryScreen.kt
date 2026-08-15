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
import kotlin.math.roundToInt

private val StormAccent = Color(0xFFFF7043)

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
    settingsDefaultRadiusMiles: Int = PreferencesRepository.DEFAULT_MAP_FOCUS_RADIUS_MILES,
    onExploreRadius: (Int) -> Unit = {},
) {
    var aboutExpanded by remember { mutableStateOf(false) }
    var mapFullscreen by remember { mutableStateOf(false) }
    var exploreRadius by remember(settingsDefaultRadiusMiles) {
        mutableIntStateOf(settingsDefaultRadiusMiles)
    }
    // Min tropical wind (knots): 0 = any, 34 ≈ tropical storm, 64 ≈ hurricane, 96 ≈ major
    var minWindKt by remember { mutableFloatStateOf(0f) }
    // Min tornado EF category: 0 = any (including unknown), 1–5 = EF1+
    var minTornadoEf by remember { mutableFloatStateOf(0f) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(latitude, longitude, exploreRadius) {
        onExploreRadius(exploreRadius)
    }

    val filteredStorms = remember(snapshot, minWindKt) {
        snapshot?.tropicalStorms
            ?.filter { (it.intensityKt ?: 0) >= minWindKt.roundToInt() }
            .orEmpty()
    }
    val filteredTornadoes = remember(snapshot, minTornadoEf) {
        val minEf = minTornadoEf.roundToInt()
        snapshot?.tornadoReports
            ?.filter { report ->
                val ef = parseEfScale(report.fScale)
                if (minEf <= 0) true else ef != null && ef >= minEf
            }
            .orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "National Weather Service · Hurricane Center · Storm Prediction Center",
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

        HazardExploreRadiusCard(
            exploreRadiusMiles = exploreRadius,
            settingsDefaultMiles = settingsDefaultRadiusMiles,
            onRadiusChange = { exploreRadius = it },
            accent = StormAccent,
            title = "Explore distance",
            subtitle = "Ad-hoc look-around — does not change Settings",
        )

        HazardFilterSliderCard(
            title = "Tropical strength",
            valueLabel = tropicalLabel(minWindKt.roundToInt()),
            value = minWindKt,
            valueRange = 0f..120f,
            steps = 23, // ~5 kt steps
            accent = Color(0xFF4FC3F7),
            onValueChange = { minWindKt = (it / 5f).roundToInt() * 5f },
            help = "Hide weaker systems (kt = knots of wind; 64 kt ≈ hurricane)",
        )

        HazardFilterSliderCard(
            title = "Tornado category",
            valueLabel = tornadoLabel(minTornadoEf.roundToInt()),
            value = minTornadoEf,
            valueRange = 0f..5f,
            steps = 4,
            accent = Color(0xFFE57373),
            onValueChange = { minTornadoEf = it.roundToInt().toFloat() },
            help = "EF scale: EF0 weak → EF5 violent (unknown ratings still show at “Any”)",
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
                storms = filteredStorms,
                tornadoes = filteredTornadoes.take(25),
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    StormMap(
                        centerLat = latitude,
                        centerLon = longitude,
                        storms = filteredStorms,
                        tornadoes = filteredTornadoes.take(25),
                        focusRadiusMiles = exploreRadius,
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
            snapshot?.querySummary
                ?: "Within $exploreRadius mi · ${tropicalLabel(minWindKt.roundToInt())} · ${tornadoLabel(minTornadoEf.roundToInt())}",
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
                            "National Hurricane Center · within $exploreRadius mi · ${tropicalLabel(minWindKt.roundToInt())}",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (filteredStorms.isEmpty()) {
                    Text(
                        "No active tropical cyclones within $exploreRadius mi at this strength filter. " +
                            "Widen explore distance or lower tropical strength.",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    filteredStorms.forEach { s ->
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
                            "Storm Prediction Center · last 7 days · within $exploreRadius mi · ${tornadoLabel(minTornadoEf.roundToInt())}",
                            color = OnSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (filteredTornadoes.isEmpty()) {
                    Text(
                        "No tornado reports within $exploreRadius mi matching this category filter.",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text("EF", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                        Text("Report", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("Mi", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                    }
                    filteredTornadoes.forEach { r ->
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
                        "Data sources (official U.S. government)\n" +
                            "• National Hurricane Center (NHC) — active tropical cyclones worldwide in " +
                            "NOAA basins (Atlantic, Eastern Pacific, Central Pacific).\n" +
                            "• National Weather Service (NWS) — watches and warnings for your city " +
                            "(tornado, hurricane, tropical storm, storm surge).\n" +
                            "• Storm Prediction Center (SPC) — preliminary tornado reports from " +
                            "the last week (not a forecast).\n\n" +
                            "Terms & scales\n" +
                            "• kt (knots) — wind speed. About 1.15 mph. Tropical storm ≈ 34+ kt; " +
                            "hurricane ≈ 64+ kt; major hurricane ≈ 96+ kt.\n" +
                            "• mb (millibars) — air pressure at the storm center (lower often means stronger).\n" +
                            "• EF scale (Enhanced Fujita) — tornado damage rating from EF0 (weak) " +
                            "to EF5 (violent). UNK means rating not assigned yet.\n" +
                            "• mi — miles from your selected city (straight-line).\n" +
                            "• CAP — Common Alerting Protocol; how NWS publishes machine-readable alerts.\n\n" +
                            "Explore distance\n" +
                            "Chips and sliders on this screen are temporary. They do not change " +
                            "Settings → Map focus radius (your app-wide default).\n\n" +
                            "Limits\n" +
                            "SPC reports are preliminary and can be revised. This is not a substitute " +
                            "for official NWS warnings — always heed local alerts.",
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

/** Parse EF0–EF5 from SPC f_scale text; null if unknown. */
private fun parseEfScale(raw: String): Int? {
    val u = raw.uppercase(Locale.US)
    if (u.contains("UNK") || u.isBlank() || u == "—" || u == "-") return null
    val digit = Regex("([0-5])").find(u)?.groupValues?.get(1)?.toIntOrNull()
    return digit
}

private fun tropicalLabel(minKt: Int): String = when {
    minKt <= 0 -> "Any strength"
    minKt < 34 -> "≥ $minKt kt"
    minKt < 64 -> "TS+ (≥ $minKt kt)"
    minKt < 96 -> "Hurricane+ (≥ $minKt kt)"
    else -> "Major+ (≥ $minKt kt)"
}

private fun tornadoLabel(minEf: Int): String = when {
    minEf <= 0 -> "Any EF"
    else -> "EF$minEf+"
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
