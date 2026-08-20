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
import androidx.compose.material.icons.filled.Thunderstorm
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
import kotlinx.coroutines.delay
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
import java.text.SimpleDateFormat
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
    settingsDefaultHistoryDays: Int = PreferencesRepository.DEFAULT_HAZARD_HISTORY_DAYS,
    onExploreParams: (
        radiusMiles: Int,
        historyDays: Int,
        historyStartMs: Long?,
        historyEndMs: Long?,
    ) -> Unit = { _, _, _, _ -> },
    /**
     * Optional reverse-geocode (e.g. Nominatim) for WCM tornado rows that only have lat/lon.
     * Called at most ~1/sec to respect OSM usage policy.
     */
    onResolvePlace: (suspend (lat: Double, lon: Double) -> String)? = null,
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
    // Min tropical wind (knots): 0 = any, 34 ≈ tropical storm, 64 ≈ hurricane, 96 ≈ major
    var minWindKt by remember { mutableFloatStateOf(0f) }
    // Min tornado EF category: 0 = any (including unknown), 1–5 = EF1+
    var minTornadoEf by remember { mutableFloatStateOf(0f) }
    val uriHandler = LocalUriHandler.current

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

    val filteredStorms = remember(snapshot, minWindKt) {
        snapshot?.tropicalStorms
            ?.filter { (it.intensityKt ?: 0) >= minWindKt.roundToInt() }
            ?.sortedByDescending { it.intensityKt ?: 0 }
            .orEmpty()
    }
    val filteredTornadoes = remember(snapshot, minTornadoEf) {
        val minEf = minTornadoEf.roundToInt()
        snapshot?.tornadoReports
            ?.filter { report ->
                val ef = parseEfScale(report.fScale)
                if (minEf <= 0) true else ef != null && ef >= minEf
            }
            ?.sortedByDescending { it.epochMs }
            .orEmpty()
    }

    // Resolve city/town names for coordinate-only WCM rows (Nominatim, rate-limited).
    var resolvedPlaces by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(filteredTornadoes, onResolvePlace) {
        val resolve = onResolvePlace ?: return@LaunchedEffect
        val pending = filteredTornadoes.filter { r ->
            r.locationIsCoordinate && !resolvedPlaces.containsKey(r.id)
        }
        for (r in pending) {
            val cacheKey = placeCacheKey(r.latitude, r.longitude)
            val cached = tornadoPlaceCache[cacheKey]
            if (cached != null) {
                resolvedPlaces = resolvedPlaces + (r.id to cached)
                continue
            }
            val name = runCatching { resolve(r.latitude, r.longitude) }.getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (name != null) {
                tornadoPlaceCache[cacheKey] = name
                resolvedPlaces = resolvedPlaces + (r.id to name)
            }
            // Nominatim policy: max 1 request/second
            delay(1_100)
        }
    }

    val historySummary = if (customRangeActive && customStartMs != null && customEndMs != null) {
        val fmt = SimpleDateFormat("MMM d", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        "${fmt.format(java.util.Date(customStartMs!!))}–${fmt.format(java.util.Date(customEndMs!!))}"
    } else {
        formatHistoryDays(historyDays)
    }
    val settingsSummary =
        "$historySummary · $exploreRadius mi · " +
            "${tropicalLabel(minWindKt.roundToInt())} · ${tornadoLabel(minTornadoEf.roundToInt())}"

    fun resetHazardSettings() {
        exploreRadius = settingsDefaultRadiusMiles
        historyDays = settingsDefaultHistoryDays
        customRangeActive = false
        customStartMs = null
        customEndMs = null
        minWindKt = 0f
        minTornadoEf = 0f
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
            "Severe weather context for ${locationName ?: "selected location"}",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (loading) {
            HazardLoadingBanner(
                message = if (snapshot == null) {
                    "Loading severe weather…"
                } else {
                    "Updating severe weather…"
                },
                accent = StormAccent,
            )
        }

        if (loading && snapshot == null) {
            CircularProgressIndicator(
                Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = StormAccent,
            )
            Text(
                "Fetching NHC / SPC / NWS data…",
                color = OnSurfaceMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
            )
            return
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

        // Collapsible Settings (explore radius, history, filters)
        HazardScreenSettingsSection(
            accent = StormAccent,
            expanded = settingsExpanded,
            onExpandedChange = { settingsExpanded = it },
            summary = settingsSummary,
            onReset = { resetHazardSettings() },
        ) {
            HazardExploreRadiusCard(
                exploreRadiusMiles = exploreRadius,
                settingsDefaultMiles = settingsDefaultRadiusMiles,
                onRadiusChange = { exploreRadius = it },
                accent = StormAccent,
                title = "Explore distance",
                subtitle = "Ad-hoc look-around — does not change app Settings",
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
                title = "Tropical strength",
                valueLabel = tropicalLabel(minWindKt.roundToInt()),
                value = minWindKt,
                valueRange = 0f..120f,
                steps = 23,
                accent = Color(0xFF4FC3F7),
                onValueChange = { minWindKt = (it / 5f).roundToInt() * 5f },
                help = "Hide weaker systems (kt = knots; 64 kt ≈ hurricane)",
                compact = true,
            )
            HazardFilterSliderCard(
                title = "Tornado category",
                valueLabel = tornadoLabel(minTornadoEf.roundToInt()),
                value = minTornadoEf,
                valueRange = 0f..5f,
                steps = 4,
                accent = Color(0xFFE57373),
                onValueChange = { minTornadoEf = it.roundToInt().toFloat() },
                help = "EF scale: EF0 weak → EF5 violent (unknown at “Any”)",
                compact = true,
            )
        }

        Text(
            snapshot?.querySummary
                ?: "Within $exploreRadius mi · last $historyDays d · " +
                "${tropicalLabel(minWindKt.roundToInt())} · ${tornadoLabel(minTornadoEf.roundToInt())}",
            color = OnSurfaceMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(8.dp))

        // One combined list (alerts + tropical + tornado), like Earthquake reports.
        val combinedReports = remember(
            snapshot,
            filteredStorms,
            filteredTornadoes,
        ) {
            buildCombinedSevereReports(
                alerts = snapshot?.localAlerts.orEmpty(),
                storms = filteredStorms,
                tornadoes = filteredTornadoes,
            )
        }

        SevereReportsSection(
            subtitle = "NWS · NHC · SPC · $historySummary · within $exploreRadius mi · " +
                "${tropicalLabel(minWindKt.roundToInt())} · ${tornadoLabel(minTornadoEf.roundToInt())}",
            reports = combinedReports,
            resolvedPlaces = resolvedPlaces,
            emptyMessage = "No watches, tropical cyclones, or tornado reports match these filters " +
                "within $exploreRadius mi.",
            onOpenUrl = { uriHandler.openUri(it) },
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
                        "About severe weather data",
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
                            "• National Hurricane Center (NHC) — active tropical cyclones.\n" +
                            "• National Weather Service (NWS) — watches/warnings for this point.\n" +
                            "• Storm Prediction Center (SPC) — tornado reports in your history window.\n\n" +
                            "Screen layout\n" +
                            "• Severe weather reports — one list mixing:\n" +
                            "  – Alert — active NWS watch/warning at this city\n" +
                            "  – Tropical — active NHC cyclone in explore distance\n" +
                            "  – Tornado / EF# — SPC tornado report (tap for daily page)\n" +
                            "  Historical WCM rows start as coordinates; the app looks up a nearby " +
                            "city/town via OpenStreetMap (Nominatim) when online.\n" +
                            "  Recent windows use daily CSVs; older/custom ranges use WCM archives.\n\n" +
                            "Terms & scales\n" +
                            "• kt (knots) — wind speed (~1.15 mph). TS ≈ 34+ kt; hurricane ≈ 64+ kt.\n" +
                            "• EF scale — tornado damage EF0–EF5 (UNK = not rated yet).\n" +
                            "• mi — miles from your selected city (straight-line).\n\n" +
                            "Settings on this screen are temporary (do not change app Settings defaults).\n\n" +
                            "Limits\n" +
                            "SPC reports can be revised. This is not a substitute for official NWS warnings.",
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

/** One row in the combined severe-weather list. */
private sealed class SevereListItem {
    abstract val sortKey: Long
    abstract val distanceMiles: Double?

    data class Alert(
        val data: SevereWeatherService.ActiveAlert,
    ) : SevereListItem() {
        // Active alerts always sort to the top.
        override val sortKey: Long get() = Long.MAX_VALUE
        override val distanceMiles: Double? get() = null
    }

    data class Tropical(
        val data: SevereWeatherService.TropicalStorm,
    ) : SevereListItem() {
        override val sortKey: Long get() = Long.MAX_VALUE - 1
        override val distanceMiles: Double get() = data.distanceMiles
    }

    data class Tornado(
        val data: SevereWeatherService.TornadoReport,
    ) : SevereListItem() {
        override val sortKey: Long get() = data.epochMs
        override val distanceMiles: Double get() = data.distanceMiles
    }
}

private fun buildCombinedSevereReports(
    alerts: List<SevereWeatherService.ActiveAlert>,
    storms: List<SevereWeatherService.TropicalStorm>,
    tornadoes: List<SevereWeatherService.TornadoReport>,
): List<SevereListItem> {
    val items = ArrayList<SevereListItem>(alerts.size + storms.size + tornadoes.size)
    alerts.forEach { items += SevereListItem.Alert(it) }
    storms.forEach { items += SevereListItem.Tropical(it) }
    tornadoes.forEach { items += SevereListItem.Tornado(it) }
    // Alerts & tropical first, then tornadoes newest-first.
    return items.sortedWith(
        compareByDescending<SevereListItem> { it.sortKey }
            .thenBy { it.distanceMiles ?: 0.0 },
    )
}

/** Session cache so reopening the same WCM points does not re-hit Nominatim. */
private val tornadoPlaceCache = mutableMapOf<String, String>()

private fun placeCacheKey(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.3f,%.3f", lat, lon)

@Composable
private fun SevereReportsSection(
    subtitle: String,
    reports: List<SevereListItem>,
    resolvedPlaces: Map<String, String>,
    emptyMessage: String,
    onOpenUrl: (String) -> Unit,
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
                Icon(
                    Icons.Filled.Thunderstorm,
                    null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Severe weather reports",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(subtitle, color = OnSurfaceMuted, fontSize = 12.sp)
                }
            }
            if (reports.isEmpty()) {
                Text(emptyMessage, color = OnSurfaceMuted, fontSize = 13.sp)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Text("Type", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                    Text("Report", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Mi", color = OnSurfaceMuted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
                }
                reports.forEach { item ->
                    when (item) {
                        is SevereListItem.Alert -> AlertReportRow(item.data)
                        is SevereListItem.Tropical -> TropicalReportRow(item.data, onOpenUrl)
                        is SevereListItem.Tornado -> TornadoReportRow(
                            r = item.data,
                            resolvedPlace = resolvedPlaces[item.data.id],
                            onOpenUrl = onOpenUrl,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(label: String, color: Color) {
    Text(
        label,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.width(56.dp),
    )
}

@Composable
private fun AlertReportRow(a: SevereWeatherService.ActiveAlert) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TypeBadge("Alert", Color(0xFFFFAB91))
        Column(Modifier.weight(1f)) {
            Text(
                a.event,
                color = Color(0xFFFFAB91),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(a.headline, color = Color.White, fontSize = 13.sp)
            if (a.areaDesc.isNotBlank()) {
                Text(a.areaDesc, color = OnSurfaceMuted, fontSize = 12.sp, maxLines = 2)
            }
            Text(
                "NWS watch/warning · Severity: ${a.severity}",
                color = OnSurfaceMuted,
                fontSize = 11.sp,
            )
        }
        Text("—", color = OnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun TropicalReportRow(
    s: SevereWeatherService.TropicalStorm,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = s.advisoryUrl != null) {
                s.advisoryUrl?.let { onOpenUrl(it) }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TypeBadge("Trop", Color(0xFF4FC3F7))
        Column(Modifier.weight(1f)) {
            Text(
                "${s.name} · ${s.classification}",
                color = Color(0xFF81D4FA),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            val wind = s.intensityKt?.let { "$it kt" } ?: "—"
            val mb = s.pressureMb?.let { "$it mb" } ?: "—"
            Text(
                "Active tropical cyclone · Winds $wind · Pressure $mb",
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
            Text(
                buildString {
                    append("NHC")
                    s.lastUpdate?.let { append(" · Updated $it") }
                    if (s.advisoryUrl != null) append(" · tap for advisory")
                },
                color = OnSurfaceMuted,
                fontSize = 11.sp,
            )
        }
        Text(
            String.format(Locale.US, "%.0f", s.distanceMiles),
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun TornadoReportRow(
    r: SevereWeatherService.TornadoReport,
    resolvedPlace: String?,
    onOpenUrl: (String) -> Unit,
) {
    val typeLabel = when {
        r.fScale.uppercase(Locale.US).startsWith("EF") -> r.fScale.uppercase(Locale.US)
        r.fScale.uppercase(Locale.US).startsWith("F") -> r.fScale.uppercase(Locale.US)
        else -> "Torn"
    }
    val placeLine = when {
        !resolvedPlace.isNullOrBlank() -> resolvedPlace
        r.location.isNotBlank() -> r.location
        else -> "Tornado"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = r.detailUrl != null) {
                r.detailUrl?.let { onOpenUrl(it) }
            }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TypeBadge(typeLabel, Color(0xFFFFAB91))
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append("Tornado")
                    if (placeLine.isNotBlank()) {
                        append(" · ")
                        append(placeLine)
                    }
                    if (r.state.isNotBlank() &&
                        !placeLine.contains(r.state, ignoreCase = true)
                    ) {
                        append(" · ")
                        append(r.state)
                    }
                },
                color = if (r.detailUrl != null) Color(0xFF81D4FA) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    // Keep coordinates as secondary when we resolved a place name
                    if (!resolvedPlace.isNullOrBlank() && r.locationIsCoordinate && r.location.isNotBlank()) {
                        append(r.location)
                        append(" · ")
                    }
                    if (r.county.isNotBlank()) {
                        append(r.county)
                        append(" · ")
                    }
                    if (r.locationIsCoordinate && resolvedPlace.isNullOrBlank()) {
                        append("Looking up place… · ")
                    }
                    append("UTC ${r.timeLabel}")
                    if (r.detailUrl != null) append(" · tap for SPC details")
                },
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
        // Selected city: red push pin
        mapView.overlays.add(
            Marker(mapView).apply {
                position = GeoPoint(centerLat, centerLon)
                title = "Selected location"
                MapHelpers.applyPushPin(this, context, R.drawable.ic_map_selection_pin)
            },
        )
        // Tropical systems: cyan push pins
        storms.forEach { s ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(s.latitude, s.longitude)
                    title = "${s.name} (${s.classification})"
                    snippet = "${s.intensityKt ?: "—"} kt · ${"%.0f".format(s.distanceMiles)} mi"
                    MapHelpers.applyPushPin(this, context, R.drawable.ic_map_storm_pin)
                },
            )
        }
        // Tornado reports: green push pins
        tornadoes.forEach { t ->
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(t.latitude, t.longitude)
                    title = "Tornado ${t.fScale} · ${t.location}"
                    snippet = "${t.state} · ${"%.0f".format(t.distanceMiles)} mi"
                    MapHelpers.applyPushPin(this, context, R.drawable.ic_map_report_pin)
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
                Icon(Icons.Filled.Place, null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Red = city · cyan = storms · green = reports · ${focusRadiusMiles} mi",
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
