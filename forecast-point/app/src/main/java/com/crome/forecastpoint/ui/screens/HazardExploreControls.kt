package com.crome.forecastpoint.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.SurfaceDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Collapsible Settings panel on hazard screens (collapsed by default).
 * Holds ad-hoc explore radius, history, and filters — does **not** write app Settings.
 * Expanded state includes Reset + Collapse footers so you need not scroll to the header.
 */
@Composable
fun HazardScreenSettingsSection(
    accent: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    summary: String,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = SurfaceDark,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                    ),
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Settings",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        if (expanded) {
                            "Ad-hoc only — does not change app Settings defaults"
                        } else {
                            summary
                        },
                        color = OnSurfaceMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse settings" else "Expand settings",
                    tint = OnSurfaceMuted,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    content()
                    if (onReset != null) {
                        Surface(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable(onClick = onReset),
                            color = Color(0xFF2A363C),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Filled.RestartAlt,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Reset to defaults",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                    // Footer: collapse without scrolling back to the header
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .clickable { onExpandedChange(false) },
                        color = accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.ExpandLess,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Collapse settings",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact inline banner shown while hazard APIs are fetching (including refresh).
 */
@Composable
fun HazardLoadingBanner(
    message: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = SurfaceDark,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = accent,
                strokeWidth = 2.dp,
            )
            Text(
                message,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Stylish ad-hoc explore panel for hazard screens.
 * Does **not** write Settings — only changes the current session’s look-around radius.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HazardExploreRadiusCard(
    exploreRadiusMiles: Int,
    settingsDefaultMiles: Int,
    onRadiusChange: (Int) -> Unit,
    accent: Color,
    title: String = "Explore radius",
    subtitle: String = "Ad-hoc only — does not change Settings default",
    compact: Boolean = false,
) {
    val pad = if (compact) 12.dp else 16.dp
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 16.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
        color = if (compact) Color(0xFF1E2A30) else SurfaceDark,
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                    ),
                )
                .padding(pad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MyLocation, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = OnSurfaceMuted, fontSize = 11.sp)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.22f),
                ) {
                    Text(
                        formatMiles(exploreRadiusMiles),
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val radiusChips = PreferencesRepository.MAP_FOCUS_RADIUS_CHIPS
            val radiusChipScroll = rememberScrollState()
            val radiusBringers = remember {
                radiusChips.associateWith { BringIntoViewRequester() }
            }
            val focusedRadiusChip = radiusChips.minByOrNull {
                kotlin.math.abs(it - exploreRadiusMiles)
            } ?: radiusChips.first()
            LaunchedEffect(focusedRadiusChip) {
                radiusBringers[focusedRadiusChip]?.bringIntoView()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(radiusChipScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                radiusChips.forEach { miles ->
                    val selected = miles == exploreRadiusMiles || miles == focusedRadiusChip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) accent.copy(alpha = 0.35f) else Color(0xFF2A363C),
                        modifier = Modifier
                            .bringIntoViewRequester(
                                radiusBringers[miles] ?: BringIntoViewRequester(),
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { onRadiusChange(miles) },
                    ) {
                        Text(
                            formatMilesShort(miles),
                            color = if (selected) Color.White else OnSurfaceMuted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = exploreRadiusMiles.toFloat().coerceIn(50f, 4000f),
                onValueChange = { onRadiusChange(snapRadius(it.roundToInt())) },
                valueRange = 50f..4000f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color(0xFF455A64),
                ),
            )
            if (exploreRadiusMiles != settingsDefaultMiles) {
                Text(
                    "Settings default stays ${formatMiles(settingsDefaultMiles)} · this is temporary",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Stock history presets (1d / 7d / 30d / 3m / 6m) plus a **Custom** pill that opens
 * a calendar date-range picker. Does **not** write app Settings.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HazardHistoryDaysCard(
    historyDays: Int,
    settingsDefaultDays: Int,
    onPresetDaysChange: (Int) -> Unit,
    onCustomRangeChange: (startMs: Long, endMs: Long) -> Unit,
    accent: Color,
    title: String = "History window",
    subtitle: String = "Stock ranges keep queries small · Custom for longer look-backs",
    compact: Boolean = false,
    /** When true, the Custom pill is selected (custom start/end active). */
    customRangeActive: Boolean = false,
    customStartMs: Long? = null,
    customEndMs: Long? = null,
) {
    val pad = if (compact) 12.dp else 16.dp
    val chips = PreferencesRepository.HAZARD_HISTORY_DAYS_CHIPS
    var showCustomPicker by remember { mutableStateOf(false) }
    val badgeLabel = if (customRangeActive && customStartMs != null && customEndMs != null) {
        formatCustomRangeLabel(customStartMs, customEndMs)
    } else {
        formatHistoryDays(historyDays)
    }

    Surface(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 16.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
        color = if (compact) Color(0xFF1E2A30) else SurfaceDark,
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                    ),
                )
                .padding(pad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(subtitle, color = OnSurfaceMuted, fontSize = 11.sp)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.22f),
                ) {
                    Text(
                        badgeLabel,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val historyChipScroll = rememberScrollState()
            val historyBringers = remember(chips) {
                chips.associateWith { BringIntoViewRequester() }
            }
            val customBring = remember { BringIntoViewRequester() }
            val focusedPreset = if (!customRangeActive) {
                chips.minByOrNull { kotlin.math.abs(it - historyDays) } ?: chips.first()
            } else {
                null
            }
            LaunchedEffect(focusedPreset, customRangeActive) {
                if (customRangeActive) {
                    customBring.bringIntoView()
                } else if (focusedPreset != null) {
                    historyBringers[focusedPreset]?.bringIntoView()
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(historyChipScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { days ->
                    val selected = !customRangeActive && days == focusedPreset
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) accent.copy(alpha = 0.35f) else Color(0xFF2A363C),
                        modifier = Modifier
                            .bringIntoViewRequester(
                                historyBringers[days] ?: BringIntoViewRequester(),
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { onPresetDaysChange(days) },
                    ) {
                        Text(
                            PreferencesRepository.historyDaysChipLabel(days),
                            color = if (selected) Color.White else OnSurfaceMuted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                // Custom pill → calendar date-range dialog
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (customRangeActive) accent.copy(alpha = 0.35f) else Color(0xFF2A363C),
                    modifier = Modifier
                        .bringIntoViewRequester(customBring)
                        .border(
                            width = if (customRangeActive) 1.5.dp else 0.dp,
                            color = if (customRangeActive) accent else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { showCustomPicker = true },
                ) {
                    Text(
                        "Custom",
                        color = if (customRangeActive) Color.White else OnSurfaceMuted,
                        fontWeight = if (customRangeActive) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            if (!customRangeActive) {
                Spacer(Modifier.height(10.dp))
                val selectedIndex = chips.indexOfFirst { it == focusedPreset }
                    .coerceAtLeast(0)
                Slider(
                    value = selectedIndex.toFloat(),
                    onValueChange = { idx ->
                        val i = idx.roundToInt().coerceIn(0, chips.lastIndex)
                        onPresetDaysChange(chips[i])
                    },
                    valueRange = 0f..chips.lastIndex.toFloat(),
                    steps = (chips.size - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = Color(0xFF455A64),
                    ),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Custom range · tap Custom to change dates",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
            if (!customRangeActive && historyDays != settingsDefaultDays) {
                Text(
                    "Settings default stays ${formatHistoryDays(settingsDefaultDays)} · this is temporary",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }

    if (showCustomPicker) {
        HazardCustomDateRangeDialog(
            accent = accent,
            initialStartMs = customStartMs,
            initialEndMs = customEndMs,
            onDismiss = { showCustomPicker = false },
            onConfirm = { start, end ->
                showCustomPicker = false
                onCustomRangeChange(start, end)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HazardCustomDateRangeDialog(
    accent: Color,
    initialStartMs: Long?,
    initialEndMs: Long?,
    onDismiss: () -> Unit,
    onConfirm: (startMs: Long, endMs: Long) -> Unit,
) {
    val now = System.currentTimeMillis()
    val defaultEnd = endOfUtcDay(now)
    val defaultStart = startOfUtcDay(now - 30L * 24L * 3600L * 1000L)
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMs ?: defaultStart,
        initialSelectedEndDateMillis = initialEndMs ?: defaultEnd,
        yearRange = IntRange(1950, Calendar.getInstance().get(Calendar.YEAR)),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) {
                        // Normalize to full UTC days so API queries are stable
                        onConfirm(startOfUtcDay(s), endOfUtcDay(e))
                    }
                },
                enabled = state.selectedStartDateMillis != null &&
                    state.selectedEndDateMillis != null,
            ) {
                Text("Apply", color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceMuted)
            }
        },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    "Custom history range",
                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            headline = {
                val s = state.selectedStartDateMillis
                val e = state.selectedEndDateMillis
                Text(
                    when {
                        s != null && e != null -> formatCustomRangeLabel(s, e)
                        s != null -> "Start ${formatShortDate(s)} · pick end"
                        else -> "Select start and end dates"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                )
            },
            showModeToggle = true,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = accent,
                selectedYearContainerColor = accent,
                todayDateBorderColor = accent,
                dayInSelectionRangeContainerColor = accent.copy(alpha = 0.25f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp),
        )
    }
}

private fun formatCustomRangeLabel(startMs: Long, endMs: Long): String {
    val a = formatShortDate(startMs)
    val b = formatShortDate(endMs)
    return if (a == b) a else "$a – $b"
}

private fun formatShortDate(epochMs: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    return fmt.format(Date(epochMs))
}

private fun startOfUtcDay(epochMs: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun endOfUtcDay(epochMs: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

@Composable
fun HazardFilterSliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    onValueChange: (Float) -> Unit,
    help: String,
    compact: Boolean = false,
) {
    val pad = if (compact) 12.dp else 16.dp
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 16.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
        color = if (compact) Color(0xFF1E2A30) else SurfaceDark,
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                )
                .padding(pad),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(help, color = OnSurfaceMuted, fontSize = 11.sp)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.22f),
                ) {
                    Text(
                        valueLabel,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color(0xFF455A64),
                ),
            )
        }
    }
}

private fun formatMiles(miles: Int): String = when {
    miles >= 1000 -> String.format(java.util.Locale.US, "%.1fk mi", miles / 1000.0)
    else -> "$miles mi"
}

private fun formatMilesShort(miles: Int): String = when {
    miles >= 1000 -> "${miles / 1000}k"
    else -> "$miles"
}

fun formatHistoryDays(days: Int): String = PreferencesRepository.historyDaysLabel(days)

/** Snap slider to a sensible mile step. */
private fun snapRadius(raw: Int): Int {
    val r = raw.coerceIn(50, 4000)
    return when {
        r <= 150 -> (r / 25) * 25
        r <= 500 -> (r / 50) * 50
        r <= 1500 -> (r / 50) * 50
        else -> (r / 100) * 100
    }.coerceIn(50, 4000)
}
