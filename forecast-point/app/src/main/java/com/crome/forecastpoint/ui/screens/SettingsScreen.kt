package com.crome.forecastpoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.crome.forecastpoint.data.DrawerNavConfigItem
import com.crome.forecastpoint.data.HourlyTabConfigItem
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.SurfaceDark
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    autoUpdate: Boolean,
    intervalMinutes: Int,
    titleBarAtBottom: Boolean,
    widgetShowHighLow: Boolean,
    mapSearchAtBottom: Boolean,
    expandCurrentConditions: Boolean,
    expandAdvisories: Boolean,
    showTitleSearch: Boolean,
    showTitleSunMoon: Boolean,
    hourlyTabConfig: List<HourlyTabConfigItem>,
    drawerNavConfig: List<DrawerNavConfigItem>,
    mapFocusRadiusMiles: Int,
    hazardHistoryDays: Int,
    spaceWeatherWatchThreshold: Int,
    spaceWeatherActiveThreshold: Int,
    spaceWeatherForecastHorizonHours: Int,
    onAutoUpdateChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onTitleBarAtBottomChange: (Boolean) -> Unit,
    onWidgetShowHighLowChange: (Boolean) -> Unit,
    onMapSearchAtBottomChange: (Boolean) -> Unit,
    onExpandCurrentConditionsChange: (Boolean) -> Unit,
    onExpandAdvisoriesChange: (Boolean) -> Unit,
    onShowTitleSearchChange: (Boolean) -> Unit,
    onShowTitleSunMoonChange: (Boolean) -> Unit,
    onHourlyTabConfigChange: (List<HourlyTabConfigItem>) -> Unit,
    onDrawerNavConfigChange: (List<DrawerNavConfigItem>) -> Unit,
    onMapFocusRadiusMilesChange: (Int) -> Unit,
    onHazardHistoryDaysChange: (Int) -> Unit,
    onSpaceWeatherWatchThresholdChange: (Int) -> Unit,
    onSpaceWeatherActiveThresholdChange: (Int) -> Unit,
    onSpaceWeatherForecastHorizonHoursChange: (Int) -> Unit,
    onManualRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ── Title bar ──────────────────────────────────────────────
        SettingsCategory("Title bar") {
            SettingsSwitchRow(
                title = "Title bar at bottom",
                description = if (titleBarAtBottom) {
                    "Menu, search, radar, and refresh are at the bottom"
                } else {
                    "Menu, search, radar, and refresh are at the top (default)"
                },
                checked = titleBarAtBottom,
                onCheckedChange = onTitleBarAtBottomChange,
            )
            SettingsSwitchRow(
                title = "City search icon",
                description = if (showTitleSearch) {
                    "Search opens Add City on Forecast & Hourly"
                } else {
                    "Hidden — still available from the menu drawer"
                },
                checked = showTitleSearch,
                onCheckedChange = onShowTitleSearchChange,
            )
            SettingsSwitchRow(
                title = "Sun / moon icon",
                description = if (showTitleSunMoon) {
                    "Opens sun, moon, and space weather from Forecast & Hourly"
                } else {
                    "Hidden from the title bar"
                },
                checked = showTitleSunMoon,
                onCheckedChange = onShowTitleSunMoonChange,
            )
        }

        // ── Main screen ────────────────────────────────────────────
        SettingsCategory("Main screen") {
            SettingsSwitchRow(
                title = "Expand Current Conditions",
                description = if (expandCurrentConditions) {
                    "Details start open when you open the app"
                } else {
                    "Details start collapsed (tap the row to expand)"
                },
                checked = expandCurrentConditions,
                onCheckedChange = onExpandCurrentConditionsChange,
            )
            SettingsSwitchRow(
                title = "Expand advisories & alerts",
                description = if (expandAdvisories) {
                    "When a watch, warning, or advisory is active, Current Conditions starts expanded"
                } else {
                    "Advisories stay collapsed until you tap — a banner still shows the event name"
                },
                checked = expandAdvisories,
                onCheckedChange = onExpandAdvisoriesChange,
            )
        }

        // ── Map ────────────────────────────────────────────────────
        SettingsCategory("Map") {
            SettingsSwitchRow(
                title = "Search bar at bottom",
                description = if (mapSearchAtBottom) {
                    "Map and Add City search appear at the bottom (lifts above the keyboard)"
                } else {
                    "Map and Add City search appear at the top (default)"
                },
                checked = mapSearchAtBottom,
                onCheckedChange = onMapSearchAtBottomChange,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Hazard map focus radius",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "Default distance for earthquake and severe weather maps, history lists, " +
                    "and nearby storms (50–4000 miles). Hazard screens can temporarily explore " +
                    "a different radius without changing this setting. Default: 250 miles.",
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingsDropdown(
                label = "Map focus radius",
                valueLabel = mapFocusRadiusLabel(mapFocusRadiusMiles),
                options = PreferencesRepository.MAP_FOCUS_RADIUS_OPTIONS.map {
                    it to mapFocusRadiusLabel(it)
                },
                onSelect = onMapFocusRadiusMilesChange,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Hazard history window",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "Default look-back for earthquake and severe weather reports " +
                    "(1 day, 7 days, 30 days, 3 months, or 6 months). Longer ranges use " +
                    "Custom on the hazard screens (calendar date picker). Default: 7 days.",
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingsDropdown(
                label = "History window",
                valueLabel = hazardHistoryDaysLabel(hazardHistoryDays),
                options = PreferencesRepository.HAZARD_HISTORY_DAYS_OPTIONS.map {
                    it to hazardHistoryDaysLabel(it)
                },
                onSelect = onHazardHistoryDaysChange,
            )
        }

        // ── Hamburger menu ─────────────────────────────────────────
        SettingsCategory("Hamburger menu") {
            Text(
                "Choose which items appear in the side menu. Long-press and drag (or use ↑↓) " +
                    "to change order — same as Hourly tabs. You can also reorder in the menu " +
                    "itself. Settings and About always stay at the bottom.",
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            DrawerNavConfigEditor(
                config = drawerNavConfig,
                onConfigChange = onDrawerNavConfigChange,
            )
            Text(
                text = "Show all menu items (default)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable {
                        onDrawerNavConfigChange(PreferencesRepository.defaultDrawerNavConfig())
                    }
                    .padding(vertical = 8.dp),
            )
        }

        // ── Hourly tabs (order + visibility) ───────────────────────
        SettingsCategory("Hourly tabs") {
            Text(
                "Toggle which tables appear on Hourly. Long-press and drag (or use ↑↓) to " +
                    "change order — the top enabled tab is first when you open Hourly. " +
                    "Colors mark the data source; disabled tabs skip that network traffic on refresh.",
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            HourlyTabSourceLegend()
            HourlyTabConfigEditor(
                config = hourlyTabConfig,
                onConfigChange = onHourlyTabConfigChange,
            )
            Text(
                text = "Reset order & enable all",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable {
                        onHourlyTabConfigChange(PreferencesRepository.defaultHourlyTabConfig())
                    }
                    .padding(vertical = 8.dp),
            )
        }

        // ── Space weather cue ──────────────────────────────────────
        SettingsCategory("Space weather title-bar cue") {
            Text(
                "Changes the sun/moon icon color when NOAA scales reach your thresholds. " +
                    "Not a system notification. Does not affect rain, wind, or temperature.",
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            SettingsDropdown(
                label = "Watch threshold (purple)",
                valueLabel = scaleThresholdLabel(spaceWeatherWatchThreshold),
                options = PreferencesRepository.SW_SCALE_OPTIONS.map { it to scaleThresholdLabel(it) },
                onSelect = onSpaceWeatherWatchThresholdChange,
            )
            Spacer(Modifier.height(12.dp))
            SettingsDropdown(
                label = "Active threshold (orange)",
                valueLabel = scaleThresholdLabel(spaceWeatherActiveThreshold),
                options = PreferencesRepository.SW_SCALE_OPTIONS.map { it to scaleThresholdLabel(it) },
                onSelect = onSpaceWeatherActiveThresholdChange,
            )
            Spacer(Modifier.height(12.dp))
            SettingsDropdown(
                label = "Forecast look-ahead",
                valueLabel = horizonLabel(spaceWeatherForecastHorizonHours),
                options = PreferencesRepository.SW_HORIZON_OPTIONS.map { it to horizonLabel(it) },
                onSelect = onSpaceWeatherForecastHorizonHoursChange,
            )
            Text(
                "Watch uses the lower bar; Active uses the higher. Predicted geomagnetic " +
                    "(G) activity within the look-ahead window counts the same as current " +
                    "G, R, or S. Defaults: Watch 1 (minor), Active 2 (moderate), 48 hours.",
                color = OnSurfaceMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // ── Widget ─────────────────────────────────────────────────
        SettingsCategory("Widget") {
            SettingsSwitchRow(
                title = "High / low temperatures",
                description = if (widgetShowHighLow) {
                    "Each day shows ↑ high and ↓ low"
                } else {
                    "Each slot shows one period temperature (e.g. Tue AM 83°) — like the classic NWS widget"
                },
                checked = widgetShowHighLow,
                onCheckedChange = onWidgetShowHighLowChange,
            )
        }

        // ── Updates ────────────────────────────────────────────────
        SettingsCategory("Updates") {
            SettingsSwitchRow(
                title = "Auto-update",
                description = "Periodically refresh forecast and widget",
                checked = autoUpdate,
                onCheckedChange = onAutoUpdateChange,
            )
            var intervalExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = intervalExpanded,
                onExpandedChange = { intervalExpanded = it },
            ) {
                TextField(
                    value = intervalLabel(intervalMinutes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Update interval") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    enabled = autoUpdate,
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = intervalExpanded,
                    onDismissRequest = { intervalExpanded = false },
                ) {
                    PreferencesRepository.INTERVAL_OPTIONS.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(intervalLabel(minutes)) },
                            onClick = {
                                onIntervalChange(minutes)
                                intervalExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Refresh now",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .clickable(onClick = onManualRefresh)
                    .padding(16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Data from the National Weather Service (weather.gov) and NOAA Space Weather " +
                "Prediction Center. Icons are the official NWS forecast icons, bundled for " +
                "reliable display on all devices including CalyxOS.",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(description, color = OnSurfaceMuted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Enable/disable + long-press drag reorder for hamburger menu items
 * (same interaction model as [HourlyTabConfigEditor]).
 */
@Composable
private fun DrawerNavConfigEditor(
    config: List<DrawerNavConfigItem>,
    onConfigChange: (List<DrawerNavConfigItem>) -> Unit,
) {
    var items by remember { mutableStateOf(config) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val accent = Color(0xFF64B5F6)
    // Do not sync from prefs while a drag is active (would fight the gesture).
    LaunchedEffect(config) {
        if (draggingIndex < 0 && config != items) items = config
    }

    fun commit(next: List<DrawerNavConfigItem>) {
        items = next
        onConfigChange(next)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        // Forecast cannot be hidden
        if (id == "Forecast" && !enabled) return
        if (!enabled && items.count { it.enabled } <= 1 && items[index].enabled) return
        val next = items.toMutableList()
        next[index] = next[index].copy(enabled = enabled)
        commit(next)
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val next = items.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        commit(next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            key(item.id) {
                val index = items.indexOfFirst { it.id == item.id }
                val isDragging = index == draggingIndex
                val rowAccent = if (item.enabled) accent else accent.copy(alpha = 0.35f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = if (isDragging) dragOffsetY.roundToInt() else 0,
                            )
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isDragging -> Color(0xFF37474F)
                                !item.enabled -> Color(0xFF1A2226)
                                else -> SurfaceDark
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = rowAccent.copy(alpha = if (item.enabled) 0.45f else 0.2f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        // Stable key — do NOT depend on index/items or the gesture cancels on swap
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val idx = items.indexOfFirst { it.id == item.id }
                                    if (idx < 0) return@detectDragGesturesAfterLongPress
                                    draggingIndex = idx
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val from = draggingIndex
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    dragOffsetY += dragAmount.y
                                    val shift = (dragOffsetY / rowHeightPx).toInt()
                                    if (shift != 0) {
                                        val to = (from + shift).coerceIn(0, items.lastIndex)
                                        if (to != from) {
                                            val next = items.toMutableList()
                                            val moved = next.removeAt(from)
                                            next.add(to, moved)
                                            items = next
                                            onConfigChange(next)
                                            draggingIndex = to
                                            dragOffsetY -= shift * rowHeightPx
                                        }
                                    }
                                },
                            )
                        }
                        .padding(end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(48.dp)
                            .background(rowAccent),
                    )
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = OnSurfaceMuted,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp),
                    )
                    Text(
                        PreferencesRepository.drawerNavDisplayName(item.id),
                        color = if (item.enabled) Color.White else OnSurfaceMuted,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { move(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (index > 0) Color.White else Color(0xFF546E7A),
                        )
                    }
                    IconButton(
                        onClick = { move(index, index + 1) },
                        enabled = index >= 0 && index < items.lastIndex,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (index >= 0 && index < items.lastIndex) {
                                Color.White
                            } else {
                                Color(0xFF546E7A)
                            },
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { setEnabled(item.id, it) },
                        enabled = item.id != "Forecast",
                    )
                }
            }
        }
    }
}

/** Visual style for an Hourly tab’s upstream data source. */
private data class HourlyTabSourceStyle(
    val accent: Color,
    val shortLabel: String,
    val legendLabel: String,
)

private fun hourlyTabSourceStyle(tabId: String): HourlyTabSourceStyle = when (tabId) {
    "Temperature", "Precipitation", "Wind", "Conditions" -> HourlyTabSourceStyle(
        accent = Color(0xFF64B5F6), // NWS blue
        shortLabel = "NWS",
        legendLabel = "NWS forecast",
    )
    "Tides" -> HourlyTabSourceStyle(
        accent = Color(0xFF4DB6AC), // CO-OPS teal
        shortLabel = "CO-OPS",
        legendLabel = "NOAA CO-OPS tides / water",
    )
    "Visibility", "Pressure", "UvIndex" -> HourlyTabSourceStyle(
        accent = Color(0xFFFFB74D), // Open-Meteo amber
        shortLabel = "Open-Meteo",
        legendLabel = "Open-Meteo weather",
    )
    "AirQuality" -> HourlyTabSourceStyle(
        accent = Color(0xFF81C784), // AQ green
        shortLabel = "Open-Meteo AQ",
        legendLabel = "Open-Meteo air quality",
    )
    "SpaceWeather" -> HourlyTabSourceStyle(
        accent = Color(0xFFCE93D8), // SWPC purple
        shortLabel = "SWPC",
        legendLabel = "NOAA SWPC space weather",
    )
    else -> HourlyTabSourceStyle(
        accent = OnSurfaceMuted,
        shortLabel = "Other",
        legendLabel = "Other",
    )
}

@Composable
private fun HourlyTabSourceLegend() {
    val styles = remember {
        listOf(
            hourlyTabSourceStyle("Temperature"),
            hourlyTabSourceStyle("Tides"),
            hourlyTabSourceStyle("Visibility"),
            hourlyTabSourceStyle("AirQuality"),
            hourlyTabSourceStyle("SpaceWeather"),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        styles.forEach { style ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 10.dp, height = 10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(style.accent),
                )
                Text(
                    text = style.legendLabel,
                    color = OnSurfaceMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * Combined enable/disable + long-press drag reorder for hourly tabs.
 * Rows are color-coded by network data source.
 */
@Composable
private fun HourlyTabConfigEditor(
    config: List<HourlyTabConfigItem>,
    onConfigChange: (List<HourlyTabConfigItem>) -> Unit,
) {
    var items by remember { mutableStateOf(config) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(config) {
        if (draggingIndex < 0 && config != items) items = config
    }

    fun commit(next: List<HourlyTabConfigItem>) {
        items = next
        onConfigChange(next)
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val next = items.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        commit(next)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        // Keep at least one tab enabled
        if (!enabled && items.count { it.enabled } <= 1 && items[index].enabled) return
        val next = items.toMutableList()
        next[index] = next[index].copy(enabled = enabled)
        commit(next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            key(item.id) {
                val index = items.indexOfFirst { it.id == item.id }
                val isDragging = index == draggingIndex
                val source = hourlyTabSourceStyle(item.id)
                val accent = if (item.enabled) source.accent else source.accent.copy(alpha = 0.35f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = if (isDragging) dragOffsetY.roundToInt() else 0,
                            )
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isDragging -> Color(0xFF37474F)
                                !item.enabled -> Color(0xFF1A2226)
                                else -> SurfaceDark
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = accent.copy(alpha = if (item.enabled) 0.45f else 0.2f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        // Stable key — do NOT depend on index/items or the gesture cancels on swap
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val idx = items.indexOfFirst { it.id == item.id }
                                    if (idx < 0) return@detectDragGesturesAfterLongPress
                                    draggingIndex = idx
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = -1
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val from = draggingIndex
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    dragOffsetY += dragAmount.y
                                    val shift = (dragOffsetY / rowHeightPx).toInt()
                                    if (shift != 0) {
                                        val to = (from + shift).coerceIn(0, items.lastIndex)
                                        if (to != from) {
                                            val next = items.toMutableList()
                                            val moved = next.removeAt(from)
                                            next.add(to, moved)
                                            items = next
                                            onConfigChange(next)
                                            draggingIndex = to
                                            dragOffsetY -= shift * rowHeightPx
                                        }
                                    }
                                },
                            )
                        }
                        .padding(end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(48.dp)
                            .background(accent),
                    )
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = OnSurfaceMuted,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = PreferencesRepository.hourlyTabDisplayName(item.id),
                            color = if (item.enabled) Color.White else OnSurfaceMuted,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = source.shortLabel,
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(
                        onClick = { move(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (index > 0) Color.White else Color(0xFF546E7A),
                        )
                    }
                    IconButton(
                        onClick = { move(index, index + 1) },
                        enabled = index >= 0 && index < items.lastIndex,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (index >= 0 && index < items.lastIndex) {
                                Color.White
                            } else {
                                Color(0xFF546E7A)
                            },
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { setEnabled(item.id, it) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    valueLabel: String,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = valueLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes minutes"
}

private fun scaleThresholdLabel(level: Int): String {
    val name = when (level) {
        1 -> "minor"
        2 -> "moderate"
        3 -> "strong"
        4 -> "severe"
        5 -> "extreme"
        else -> "level $level"
    }
    return "$level ($name) — G$level / R$level / S$level"
}

private fun horizonLabel(hours: Int): String = when (hours) {
    24 -> "24 hours"
    48 -> "48 hours (default)"
    72 -> "72 hours"
    else -> "$hours hours"
}

private fun mapFocusRadiusLabel(miles: Int): String =
    if (miles == PreferencesRepository.DEFAULT_MAP_FOCUS_RADIUS_MILES) {
        "$miles miles (default)"
    } else {
        "$miles miles"
    }

private fun hazardHistoryDaysLabel(days: Int): String {
    val base = PreferencesRepository.historyDaysLabel(days)
    return if (days == PreferencesRepository.DEFAULT_HAZARD_HISTORY_DAYS) {
        "$base (default)"
    } else {
        base
    }
}
