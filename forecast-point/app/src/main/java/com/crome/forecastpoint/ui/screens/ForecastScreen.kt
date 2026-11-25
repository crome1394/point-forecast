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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import com.crome.forecastpoint.data.CurrentConditions
import com.crome.forecastpoint.data.DayForecast
import com.crome.forecastpoint.data.WeatherHazard
import com.crome.forecastpoint.data.WeatherSnapshot
import com.crome.forecastpoint.ui.components.NwsIcon
import com.crome.forecastpoint.ui.theme.Amber
import com.crome.forecastpoint.ui.theme.HighTemp
import com.crome.forecastpoint.ui.theme.LowTemp
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.ClockTime
import com.crome.forecastpoint.util.TideGraphData
import com.crome.forecastpoint.util.Units
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(
    snapshot: WeatherSnapshot?,
    loading: Boolean,
    error: String?,
    isFavorite: Boolean,
    expandCurrentConditions: Boolean = false,
    expandAdvisories: Boolean = true,
    metricUnits: Boolean = false,
    onToggleFavorite: () -> Unit,
    onOpenHourly: () -> Unit,
    onDayClick: (DayForecast) -> Unit,
    onAddCity: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (snapshot == null && loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return
        }

        PullToRefreshBox(
            isRefreshing = loading && snapshot != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val context = LocalContext.current
            val use24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
            // LazyColumn: only visible day cards compose → smoother scroll
            LazyColumn(Modifier.fillMaxSize()) {
                if (snapshot != null) {
                    item(key = "header") {
                        LocationHeader(
                            snapshot = snapshot,
                            isFavorite = isFavorite,
                            metricUnits = metricUnits,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
                    item(key = "current") {
                        CurrentConditionsCard(
                            current = snapshot.current,
                            snapshot = snapshot,
                            expandByDefault = expandCurrentConditions,
                            expandAdvisories = expandAdvisories,
                            metricUnits = metricUnits,
                        )
                    }
                    if (error != null) {
                        item(key = "error") {
                            Text(
                                text = error,
                                color = Color(0xFFEF9A9A),
                                modifier = Modifier.padding(16.dp),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    itemsIndexed(
                        items = snapshot.days,
                        key = { index, day -> "${index}|${day.dateLabel}|${day.dayName}" },
                        contentType = { _, _ -> "day" },
                    ) { _, day ->
                            DayCard(
                                day = day,
                                storedMetric = snapshot.units.isMetric,
                                metricUnits = metricUnits,
                                use24Hour = use24Hour,
                                onClick = { onDayClick(day); onOpenHourly() },
                            )
                    }
                    item(key = "bottom_pad") { Spacer(Modifier.height(24.dp)) }
                } else {
                    item(key = "empty") {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                text = error
                                    ?: "No city selected yet.\n\nAdd a city by search, map, or current location.\n\nPull down to refresh when a city is loaded.",
                                color = OnSurfaceMuted,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Add City",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable(onClick = onAddCity)
                                    .padding(vertical = 8.dp),
                            )
                            Text(
                                text = "Open Map",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable(onClick = onOpenMap)
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationHeader(
    snapshot: WeatherSnapshot,
    isFavorite: Boolean,
    metricUnits: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val elev = Units.elevationSuffix(snapshot.elevationFt, metricUnits)
            Text(
                text = "${snapshot.locationName}$elev",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = relativeUpdateLabel(snapshot),
                color = OnSurfaceMuted,
                fontSize = 13.sp,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = if (isFavorite) {
                    "Remove from saved cities"
                } else {
                    "Save city to favorites"
                },
                tint = if (isFavorite) Amber else OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun CurrentConditionsCard(
    current: CurrentConditions,
    snapshot: WeatherSnapshot,
    expandByDefault: Boolean = false,
    expandAdvisories: Boolean = true,
    metricUnits: Boolean = false,
) {
    val hazards = snapshot.hazards
    val storedMetric = snapshot.units.isMetric
    // Expand when: user always wants conditions open, or advisories present and that setting is on
    val initialExpanded = expandByDefault || (hazards.isNotEmpty() && expandAdvisories)
    var expanded by remember(hazards.size, expandByDefault, expandAdvisories) {
        mutableStateOf(initialExpanded)
    }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Current Conditions", color = Color.White, fontWeight = FontWeight.SemiBold)
            current.stationName?.let {
                Text(
                    "Observed at: $it",
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            current.observedAt?.let {
                Text("Updated $it", color = OnSurfaceMuted, fontSize = 12.sp)
            }

            // Collapsed summary of hazards so they are visible even before expand
            if (hazards.isNotEmpty()) {
                HazardBanner(
                    hazards = hazards,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NwsIcon(current.iconCode, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = Units.temperature(current.temperatureF, storedMetric, metricUnits),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(current.weather, color = OnSurfaceMuted, fontSize = 15.sp)
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = OnSurfaceMuted,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    DetailLine(
                        "Feels Like",
                        current.feelsLikeF?.let {
                            Units.temperature(it, storedMetric, metricUnits)
                        },
                    )
                    DetailLine("Relative Humidity", current.humidityPct?.let { "$it%" })
                    DetailLine("Wind Direction", current.windDirection)
                    DetailLine("Wind Speed", Units.wind(current.windSpeedMph, storedMetric, metricUnits))
                    DetailLine(
                        "Dew Point",
                        current.dewPointF?.let {
                            Units.temperature(it, storedMetric, metricUnits)
                        },
                    )
                    DetailLine(
                        "Visibility",
                        Units.visibility(current.visibilityMi, storedMetric, metricUnits),
                    )
                    DetailLine(
                        "Barometer",
                        Units.barometer(current.barometerInHg, current.barometerMb, metricUnits),
                    )

                    val tideInfo = snapshot.tideInfo
                    if (tideInfo != null && tideInfo.stationName.isNotBlank()) {
                        val tideHeights = remember(snapshot.hourly) {
                            snapshot.hourly.mapNotNull { row ->
                                val epoch = row.epochSec ?: return@mapNotNull null
                                val height = row.tideFt ?: return@mapNotNull null
                                (epoch / 3600L) * 3600L to height
                            }.toMap()
                        }
                        val zone = remember(snapshot.timeZoneId) {
                            snapshot.timeZoneId?.let { TimeZone.getTimeZone(it) }
                        }
                        val tideEvents = remember(tideHeights, zone, snapshot.updatedAtEpochMs) {
                            TideGraphData.todayEvents(
                                heights = tideHeights,
                                timeZoneId = zone?.id,
                                nowEpochMs = snapshot.updatedAtEpochMs,
                            )
                        }
                        if (tideEvents.lows.isNotEmpty() || tideEvents.highs.isNotEmpty()) {
                            TodayTideTable(
                                events = tideEvents,
                                stationName = tideInfo.stationName,
                                timeZone = zone,
                                storedMetric = storedMetric,
                                metricUnits = metricUnits,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }

                    if (hazards.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Hazards & Alerts",
                            color = Color(0xFFFFCC80),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        hazards.forEach { hazard ->
                            HazardDetailCard(
                                hazard = hazard,
                                onOpenUrl = { url ->
                                    runCatching { uriHandler.openUri(url) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTideTable(
    events: TideGraphData.TodayTideEvents,
    stationName: String,
    timeZone: TimeZone?,
    storedMetric: Boolean,
    metricUnits: Boolean,
    modifier: Modifier = Modifier,
) {
    val timeFmt = remember(timeZone) {
        SimpleDateFormat("HH:mm", Locale.US).apply {
            if (timeZone != null) this.timeZone = timeZone
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Today's tides",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                stationName,
                color = OnSurfaceMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(8.dp))
            val rows = events.rows()
            repeat(rows.size) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Box(Modifier.weight(1f)) {
                        rows[index].low?.let { low ->
                            TideEventCell(low, "Low", Color(0xFF4DD0E1), timeFmt, storedMetric, metricUnits)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        rows[index].high?.let { high ->
                            TideEventCell(high, "High", Amber, timeFmt, storedMetric, metricUnits)
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
    storedMetric: Boolean,
    metricUnits: Boolean,
) {
    Column {
        Text(
            "$label: ${Units.tideHeight(event.heightFt, storedMetric, metricUnits)}",
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            timeFmt.format(Date(event.epochHour * 1000L)),
            color = OnSurfaceMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HazardBanner(
    hazards: List<WeatherHazard>,
    modifier: Modifier = Modifier,
) {
    val top = hazards.first()
    val more = hazards.size - 1
    val bg = severityColor(top.severity)
    Row(
        modifier
            .fillMaxWidth()
            .background(bg.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = bg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildString {
                append(top.event)
                if (more > 0) append(" (+$more more)")
            },
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HazardDetailCard(
    hazard: WeatherHazard,
    onOpenUrl: (String) -> Unit,
) {
    val bg = severityColor(hazard.severity)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bg.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        Text(
            hazard.event,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        val meta = listOfNotNull(
            hazard.severity?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) },
            hazard.urgency?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, color = OnSurfaceMuted, fontSize = 12.sp)
        }
        hazard.headline?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = Color(0xFFFFE0B2),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        hazard.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.trim(),
                color = Color(0xFFECEFF1),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        hazard.instruction?.takeIf { it.isNotBlank() }?.let {
            Text(
                "Instruction: ${it.trim()}",
                color = Color(0xFFFFCC80),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        hazard.url?.takeIf { it.startsWith("http") }?.let { url ->
            Text(
                "View full alert",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { onOpenUrl(url) },
            )
        }
    }
}

private fun severityColor(severity: String?): Color = when (severity?.lowercase()) {
    "extreme" -> Color(0xFFD32F2F)
    "severe" -> Color(0xFFE65100)
    "moderate" -> Color(0xFFF9A825)
    "minor" -> Color(0xFF0288D1)
    else -> Color(0xFFFF9800)
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = "$label: $value",
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun DayCard(
    day: DayForecast,
    storedMetric: Boolean,
    metricUnits: Boolean,
    use24Hour: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(day.dateLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NwsIcon(day.iconCode, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        day.highF?.let {
                            Text(
                                "↑ ${Units.temperature(it, storedMetric, metricUnits)}",
                                color = HighTemp,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        day.lowF?.let {
                            Text(
                                "↓ ${Units.temperature(it, storedMetric, metricUnits)}",
                                color = LowTemp,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    day.popPct?.let {
                        Text("$it%", color = OnSurfaceMuted, fontSize = 13.sp)
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = OnSurfaceMuted,
                )
            }
            Text(
                text = day.detailed.ifBlank { day.summary },
                color = OnSurfaceMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbSunny, null, tint = Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ClockTime.format(day.sunrise, use24Hour) ?: "—", color = OnSurfaceMuted, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbSunny, null, tint = Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ClockTime.format(day.sunset, use24Hour) ?: "—", color = OnSurfaceMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun relativeUpdateLabel(snapshot: WeatherSnapshot): String {
    val obs = snapshot.observationTimeLabel
    if (!obs.isNullOrBlank()) {
        val ago = System.currentTimeMillis() - snapshot.updatedAtEpochMs
        val hours = TimeUnit.MILLISECONDS.toHours(ago)
        return if (hours <= 0) {
            "Updated just now · $obs"
        } else {
            "Updated $hours hour${if (hours == 1L) "" else "s"} ago, $obs"
        }
    }
    val fmt = SimpleDateFormat("h:mm a", Locale.US)
    return "Updated ${fmt.format(Date(snapshot.updatedAtEpochMs))}"
}
