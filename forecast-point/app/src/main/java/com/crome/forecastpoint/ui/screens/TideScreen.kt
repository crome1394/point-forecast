package com.crome.forecastpoint.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.data.SavedLocation
import com.crome.forecastpoint.data.TideService
import com.crome.forecastpoint.data.TideStationChoice
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.Amber
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.LocationTimeZone
import com.crome.forecastpoint.util.TideGraphData
import com.crome.forecastpoint.util.TideStationLabel
import com.crome.forecastpoint.util.Units
import com.crome.forecastpoint.util.WeatherMath
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val DayOffsets = (-1..5).toList()
private val TideTeal = Color(0xFF4DD0E1)

/** Tide diagram for the active city, using the station remembered for that city. */
@Composable
fun TideScreen(
    latitude: Double,
    longitude: Double,
    locationName: String?,
    timeZoneId: String? = null,
    station: TideStationChoice? = null,
    result: TideService.TideResult? = null,
    loading: Boolean = false,
    metricUnits: Boolean = false,
    favorites: List<SavedLocation> = emptyList(),
    activeId: String? = null,
    tideStations: Map<String, TideStationChoice> = emptyMap(),
    onSelectFavorite: (SavedLocation) -> Unit = {},
    onEnsureTides: () -> Unit = {},
) {
    LaunchedEffect(latitude, longitude, station?.id) { onEnsureTides() }

    val tz = remember(latitude, longitude, timeZoneId) {
        timeZoneId?.let { TimeZone.getTimeZone(it) }
            ?: LocationTimeZone.resolve(latitude, longitude)
    }
    val now = remember(tz) {
        Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val dayLabelFmt = remember(tz) { SimpleDateFormat("EEE", Locale.US).apply { timeZone = tz } }
    val dateFmt = remember(tz) { SimpleDateFormat("d", Locale.US).apply { timeZone = tz } }
    val monthFmt = remember(tz) { SimpleDateFormat("MMM", Locale.US).apply { timeZone = tz } }

    val days = remember(result, tz) {
        TideGraphData.groupByLocalDay(
            heights = result?.heightByEpochHour.orEmpty(),
            timeZoneId = tz.id,
            dayOffsets = DayOffsets,
        )
    }
    val pageState = rememberPagerState(
        initialPage = DayOffsets.indexOf(0).coerceAtLeast(0),
        pageCount = { DayOffsets.size },
    )
    val scope = rememberCoroutineScope()
    var aboutExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        locationName?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        StationHeader(station, result, loading, metricUnits)

        if (favorites.size > 1) {
            FavoriteStrip(
                favorites = favorites,
                activeId = activeId,
                tideStations = tideStations,
                onSelect = onSelectFavorite,
            )
        }

        HorizontalPager(
            state = pageState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pageState,
                snapPositionalThreshold = 0.25f,
            ),
            key = { DayOffsets[it] },
        ) { page ->
            val day = days.getOrNull(page)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (day == null || day.points.isEmpty()) {
                    Text(
                        text = if (loading) "Loading tide predictions…"
                        else result?.unavailableReason ?: "No tide data for this station",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                } else {
                    Text(
                        "Tide height above ${result?.datumLabel ?: "MLLW"}",
                        color = OnSurfaceMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TideHeightChart(
                        day = day,
                        metricUnits = metricUnits,
                        tz = tz,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(horizontal = 8.dp),
                    )
                    ExtremesCard(day = day, metricUnits = metricUnits, tz = tz)
                }

                Spacer(Modifier.height(12.dp))
                AboutCard(expanded = aboutExpanded, onToggle = { aboutExpanded = !aboutExpanded })
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayOffsets.forEachIndexed { index, off ->
                val cal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) }
                val selected = index == pageState.currentPage
                val label = when (off) {
                    -1 -> "Yda"
                    0 -> "Today"
                    1 -> "Tmw"
                    else -> dayLabelFmt.format(cal.time)
                }
                Column(
                    Modifier
                        .width(64.dp)
                        .clickable { scope.launch { pageState.animateScrollToPage(index) } }
                        .background(
                            if (selected) PrimaryBlue.copy(alpha = 0.45f) else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        color = if (selected) Color.White else OnSurfaceMuted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        dateFmt.format(cal.time),
                        color = if (selected) Color(0xFF90CAF9) else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        monthFmt.format(cal.time),
                        color = if (selected) Color(0xFF90CAF9) else OnSurfaceMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StationHeader(
    station: TideStationChoice?,
    result: TideService.TideResult?,
    loading: Boolean,
    metricUnits: Boolean,
) {
    val kind = when (station?.sourceKind ?: result?.station?.kind?.name) {
        "waterlevel", "WaterLevel" -> "Water level (NOAA CO-OPS)"
        "usgs", "UsgsGage" -> "River / lake stage (USGS)"
        else -> "Tide (NOAA CO-OPS)"
    }
    val name = station?.name ?: result?.station?.name ?: if (loading) "Finding nearest station…" else "No station in range"
    val detail = TideStationLabel.detailLine(
        id = station?.id ?: result?.station?.id,
        lat = station?.lat ?: result?.station?.lat,
        lon = station?.lon ?: result?.station?.lon,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E2A30))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = when {
                station == null && !loading -> "$kind: no station available"
                result?.distanceMiles != null ->
                    "$kind · $name · " +
                        "${Units.distanceFromMiles(result.distanceMiles!!, metricUnits)}"
                else -> "$kind · $name"
            },
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp,
        )
        detail?.let {
            Text(
                text = it,
                color = OnSurfaceMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun FavoriteStrip(
    favorites: List<SavedLocation>,
    activeId: String?,
    tideStations: Map<String, TideStationChoice>,
    onSelect: (SavedLocation) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        favorites.forEach { loc ->
            val selected = loc.id == activeId
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) PrimaryBlue.copy(alpha = 0.35f) else SurfaceDark,
                modifier = Modifier.clickable { onSelect(loc) },
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFCC80),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tideStations[loc.id]?.name ?: loc.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtremesCard(day: TideGraphData.Day, metricUnits: Boolean, tz: TimeZone) {
    if (day.extremes.isEmpty()) return
    val split = TideGraphData.splitExtremes(day.extremes)
    val timeFmt = remember(tz) { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz } }
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Highs & lows",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            repeat(maxOf(split.lows.size, split.highs.size)) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Box(Modifier.weight(1f)) {
                        split.lows.getOrNull(index)?.let { low ->
                            TideEventCell(low, "Low", TideTeal, timeFmt, metricUnits)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        split.highs.getOrNull(index)?.let { high ->
                            TideEventCell(high, "High", Amber, timeFmt, metricUnits)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TideEventCell(
    event: TideGraphData.Extreme,
    label: String,
    tint: Color,
    timeFmt: SimpleDateFormat,
    metricUnits: Boolean,
) {
    Column {
        Text(
            "$label: ${Units.tideHeight(event.heightFt, false, metricUnits)}",
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            timeFmt.format(java.util.Date(event.epochHour * 1000L)),
            color = OnSurfaceMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AboutCard(expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onToggle),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "About the tide",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = OnSurfaceMuted,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    "Predictions come from the nearest NOAA CO-OPS gauge, chosen when a city " +
                        "is added and remembered for its tide diagram. Heights are relative to " +
                        "the station datum (MLLW for coastal tides, LWD for Great Lakes water " +
                        "levels). High and low are turning points of the hourly curve; times use " +
                        "the forecast location's time zone.\n\n" +
                        "This is a harmonic prediction, not a live observation — wind and " +
                        "pressure can shift actual water levels.",
                    color = Color(0xFFCFD8DC),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TideHeightChart(
    day: TideGraphData.Day,
    metricUnits: Boolean,
    tz: TimeZone,
    modifier: Modifier = Modifier,
) {
    if (day.points.size < 2) return
    val factor = if (metricUnits) WeatherMath.feetToMeters(1.0) else 1.0
    val values = day.points.map { it.heightFt * factor }
    val rawMin = values.minOrNull()!!
    val rawMax = values.maxOrNull()!!
    val pad = ((rawMax - rawMin) * 0.15).coerceAtLeast(if (metricUnits) 0.1 else 0.4)
    val minV = rawMin - pad
    val maxV = rawMax + pad
    val range = (maxV - minV).coerceAtLeast(0.1)
    val unit = if (metricUnits) "m" else "ft"
    val ticks = listOf(maxV, (maxV + minV) / 2.0, minV)

    fun fracFromTop(v: Double): Float = (1f - (v - minV) / range).toFloat().coerceIn(0f, 1f)

    Row(modifier) {
        BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .padding(end = 4.dp, bottom = 22.dp)
                .width(46.dp),
        ) {
            val plotH = maxHeight
            ticks.forEach { tick ->
                Text(
                    String.format(Locale.US, "%.1f", tick),
                    color = OnSurfaceMuted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = plotH * fracFromTop(tick) - 7.dp),
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    fun xAt(hour: Int): Float = w * hour.coerceIn(0, 23) / 23f
                    fun yAt(v: Double): Float = h * fracFromTop(v)

                    listOf(0, 6, 12, 18, 23).forEach { hour ->
                        drawLine(
                            Color(0x22455A64),
                            Offset(xAt(hour), 0f),
                            Offset(xAt(hour), h),
                            strokeWidth = 1f,
                        )
                    }
                    ticks.forEach { tick ->
                        drawLine(
                            Color(0x22455A64),
                            Offset(0f, yAt(tick)),
                            Offset(w, yAt(tick)),
                            strokeWidth = 1f,
                        )
                    }

                    val line = Path()
                    val fill = Path()
                    day.points.forEachIndexed { i, point ->
                        val x = xAt(point.hourOfDay)
                        val y = yAt(point.heightFt * factor)
                        if (i == 0) {
                            line.moveTo(x, y)
                            fill.moveTo(x, h)
                            fill.lineTo(x, y)
                        } else {
                            line.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                    }
                    fill.lineTo(xAt(day.points.last().hourOfDay), h)
                    fill.close()

                    drawPath(
                        fill,
                        brush = Brush.verticalGradient(
                            listOf(TideTeal.copy(alpha = 0.35f), Color.Transparent),
                            startY = 0f,
                            endY = h,
                        ),
                    )
                    drawPath(line, color = TideTeal, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

                    day.extremes.forEach { extreme ->
                        drawCircle(
                            color = if (extreme.type == "HIGH") Color(0xFFFFCC80) else TideTeal,
                            radius = 5f,
                            center = Offset(xAt(extremeHourOfDay(extreme, day)), yAt(extreme.heightFt * factor)),
                        )
                    }

                    if (day.offset == 0) {
                        val nowX = w * TideGraphData.localHourFraction(
                            epochMs = System.currentTimeMillis(),
                            timeZoneId = tz.id,
                        ) / 23f
                        drawLine(
                            color = Color.White.copy(alpha = 0.55f),
                            start = Offset(nowX, 0f),
                            end = Offset(nowX, h),
                            strokeWidth = 1.5f,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("00", "06", "12", "18", "24").forEach {
                    Text(it, color = OnSurfaceMuted, fontSize = 10.sp)
                }
            }
            Text(
                "Height ($unit)",
                color = OnSurfaceMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

private fun extremeHourOfDay(extreme: TideGraphData.Extreme, day: TideGraphData.Day): Int =
    day.points.firstOrNull { it.epochHour == extreme.epochHour }?.hourOfDay ?: 0
