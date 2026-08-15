package com.crome.forecastpoint.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
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
import com.crome.forecastpoint.data.EarthquakeService
import com.crome.forecastpoint.data.SevereWeatherService
import com.crome.forecastpoint.data.SpaceWeatherService
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.CelestialCalculator
import com.crome.forecastpoint.util.LocationTimeZone
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/** Targets from the title-bar celestial menu / body switcher. */
enum class CelestialBody { Sun, Moon, SpaceWeather, Earthquakes, Storms }

/** Day offsets relative to today: yesterday … +5 days. */
private val DayOffsets = (-1..5).toList()

@Composable
fun SunMoonScreen(
    latitude: Double,
    longitude: Double,
    locationName: String?,
    /** Controlled from the title bar (Sun / Moon / Space weather / Earthquakes). */
    body: CelestialBody,
    /** Forecast-location zone id (IANA or GMT±); null falls back to coordinate estimate. */
    timeZoneId: String? = null,
    spaceWeather: SpaceWeatherService.Snapshot? = null,
    spaceWeatherWatchThreshold: Int = 1,
    spaceWeatherActiveThreshold: Int = 2,
    spaceWeatherForecastHorizonHours: Int = 48,
    earthquakes: EarthquakeService.Snapshot? = null,
    earthquakesLoading: Boolean = false,
    onEnsureEarthquakes: ((
        radiusMiles: Int,
        historyDays: Int,
        historyStartMs: Long?,
        historyEndMs: Long?,
    ) -> Unit)? = null,
    severeWeather: SevereWeatherService.Snapshot? = null,
    severeWeatherLoading: Boolean = false,
    onEnsureSevereWeather: ((
        radiusMiles: Int,
        historyDays: Int,
        historyStartMs: Long?,
        historyEndMs: Long?,
    ) -> Unit)? = null,
    mapFocusRadiusMiles: Int = 250,
    hazardHistoryDays: Int = 7,
    onEnsureSpaceWeather: (() -> Unit)? = null,
) {
    if (body == CelestialBody.SpaceWeather) {
        LaunchedEffect(Unit) { onEnsureSpaceWeather?.invoke() }
        SpaceWeatherSummaryScreen(
            snapshot = spaceWeather,
            watchThreshold = spaceWeatherWatchThreshold,
            activeThreshold = spaceWeatherActiveThreshold,
            forecastHorizonHours = spaceWeatherForecastHorizonHours,
        )
        return
    }
    if (body == CelestialBody.Earthquakes) {
        EarthquakeSummaryScreen(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            snapshot = earthquakes,
            loading = earthquakesLoading,
            settingsDefaultRadiusMiles = mapFocusRadiusMiles,
            settingsDefaultHistoryDays = hazardHistoryDays,
            onExploreParams = { r, d, s, e -> onEnsureEarthquakes?.invoke(r, d, s, e) },
        )
        return
    }
    if (body == CelestialBody.Storms) {
        SevereWeatherSummaryScreen(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            snapshot = severeWeather,
            loading = severeWeatherLoading,
            settingsDefaultRadiusMiles = mapFocusRadiusMiles,
            settingsDefaultHistoryDays = hazardHistoryDays,
            onExploreParams = { r, d, s, e -> onEnsureSevereWeather?.invoke(r, d, s, e) },
        )
        return
    }

    var aboutExpanded by remember { mutableStateOf(false) }
    // Use the forecast location's zone, not the device zone (e.g. RI while phone is in CEST).
    val tz = remember(latitude, longitude, timeZoneId) {
        timeZoneId?.let { TimeZone.getTimeZone(it) }
            ?: LocationTimeZone.resolve(latitude, longitude)
    }
    val today = remember(tz) {
        Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val dayPagerState = rememberPagerState(
        initialPage = DayOffsets.indexOf(0).coerceAtLeast(0),
        pageCount = { DayOffsets.size },
    )
    val scope = rememberCoroutineScope()

    val dayLabelFmt = remember(tz) {
        SimpleDateFormat("EEE", Locale.US).apply { timeZone = tz }
    }
    val dateFmt = remember(tz) {
        SimpleDateFormat("d", Locale.US).apply { timeZone = tz }
    }
    val monthFmt = remember(tz) {
        SimpleDateFormat("MMM", Locale.US).apply { timeZone = tz }
    }

    LaunchedEffect(body) { aboutExpanded = false }

    @Composable
    fun DayStrip() {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayOffsets.forEachIndexed { index, off ->
                val cal = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) }
                val selected = index == dayPagerState.currentPage
                val label = when (off) {
                    -1 -> "Yda"
                    0 -> "Today"
                    1 -> "Tmw"
                    else -> dayLabelFmt.format(cal.time)
                }
                Column(
                    Modifier
                        .width(64.dp)
                        .clickable {
                            scope.launch { dayPagerState.animateScrollToPage(index) }
                        }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        locationName?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        HorizontalPager(
            state = dayPagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = dayPagerState,
                snapPositionalThreshold = 0.25f,
            ),
            key = { DayOffsets[it] },
        ) { dayPage ->
            val off = DayOffsets[dayPage]
            val dayCal = remember(off, tz) {
                (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) }
            }
            val sun = remember(latitude, longitude, off, tz) {
                CelestialCalculator.sunDay(latitude, longitude, tz, dayCal)
            }
            val moon = remember(latitude, longitude, off, tz) {
                CelestialCalculator.moonDay(
                    latitude = latitude,
                    longitude = longitude,
                    timeZone = tz,
                    day = dayCal,
                    useCurrentInstantForPhase = off == 0,
                )
            }
            DayDetailContent(
                body = body,
                sun = sun,
                moon = moon,
                aboutExpanded = aboutExpanded,
                onAboutToggle = { aboutExpanded = !aboutExpanded },
            )
        }

        DayStrip()
    }
}

@Composable
private fun DayDetailContent(
    body: CelestialBody,
    sun: CelestialCalculator.SunDay,
    moon: CelestialCalculator.MoonDay,
    aboutExpanded: Boolean,
    onAboutToggle: () -> Unit,
) {
    // Space weather is handled by SpaceWeatherSummaryScreen
    if (body == CelestialBody.SpaceWeather) return

    val alts = if (body == CelestialBody.Sun) sun.altitudeByHour else moon.altitudeByHour
    val pathColor = if (body == CelestialBody.Sun) Color(0xFFFFB300) else Color(0xFF90CAF9)
    val fillColor = pathColor.copy(alpha = 0.25f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        Text(
            if (body == CelestialBody.Sun) {
                "Sun altitude above the horizon"
            } else {
                "Moon altitude above the horizon"
            },
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        AltitudeChart(
            altitudes = alts,
            pathColor = pathColor,
            fillColor = fillColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(horizontal = 8.dp),
        )
        Text(
            if (body == CelestialBody.Sun) {
                "Peak = highest altitude (near solar noon). 0° = horizon; below 0° = below horizon."
            } else {
                "Peak = highest altitude for the night. 0° = horizon; below 0° = below horizon."
            },
            color = OnSurfaceMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (body == CelestialBody.Sun) {
                    InfoRow(
                        icon = Icons.Filled.WbSunny,
                        iconTint = Color(0xFFFFB300),
                        text = "${sun.sunrise} ↑  /  ${sun.sunset} ↓  ·  ${sun.daylightLabel}",
                    )
                    InfoRow(
                        icon = Icons.Filled.WbTwilight,
                        iconTint = Color(0xFFFFCC80),
                        text = "Civil dawn ${sun.civilDawn}  ·  dusk ${sun.civilDusk}",
                    )
                    InfoRow(
                        icon = Icons.Filled.WbSunny,
                        iconTint = Color(0xFFFFF59D),
                        text = "Solar noon ${sun.solarNoon}",
                    )
                } else {
                    InfoRow(
                        icon = Icons.Filled.NightsStay,
                        iconTint = Color(0xFF90CAF9),
                        text = "${moon.moonrise} ↑  /  ${moon.moonset} ↓",
                    )
                    InfoRow(
                        icon = Icons.Filled.DarkMode,
                        iconTint = Color(0xFFE1BEE7),
                        text = "${moon.phaseName} · ${moon.illuminationPct}% lit",
                    )
                    InfoRow(
                        icon = Icons.Filled.DarkMode,
                        iconTint = Color(0xFFB0BEC5),
                        text = "Age ~${String.format(Locale.US, "%.1f", moon.ageDays)} days into cycle",
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Expandable About (default collapsed)
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onAboutToggle),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (body == CelestialBody.Sun) "About the sun" else "About the moon",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (aboutExpanded) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = if (aboutExpanded) "Collapse" else "Expand",
                        tint = OnSurfaceMuted,
                    )
                }
                AnimatedVisibility(visible = aboutExpanded) {
                    Text(
                        text = if (body == CelestialBody.Sun) {
                            "Rise is when the upper limb of the sun appears above the horizon; " +
                                "set is when it fully disappears. Civil dawn/dusk mark when the sun is " +
                                "6° below the horizon—outdoor activities are still possible without " +
                                "artificial light.\n\n" +
                                "Day length is the span from sunrise to sunset at this latitude and " +
                                "longitude. Solar noon is when the sun reaches its highest altitude " +
                                "for the day (not always 12:00 on the clock).\n\n" +
                                "Times use your device time zone and the selected location’s coordinates."
                        } else {
                            "Moonrise and moonset are when the moon’s center crosses the horizon " +
                                "(approximate). The moon’s path and timing shift each day as it orbits Earth.\n\n" +
                                "Phase and illumination describe how much of the moon’s Earth-facing side " +
                                "is sunlit. Waxing means growing toward full; waning means shrinking " +
                                "toward new.\n\n" +
                                "The phase cycle is about 29.5 days. Illumination is the fraction of the " +
                                "moon’s disc that is sunlit (approximate). Near new moon, apps often differ " +
                                "by a few percent (e.g. 3% vs 4–7%) depending on formula and exact time.\n\n" +
                                "Northern lights chances rise with geomagnetic activity (Hourly → Space Wx), " +
                                "not moon phase alone—though a bright full moon can wash out fainter auroras."
                        },
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
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun AltitudeChart(
    altitudes: List<Float>,
    pathColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    if (altitudes.size < 2) return

    val minA = min(-10f, altitudes.minOrNull() ?: -10f)
    val maxA = max(60f, altitudes.maxOrNull() ?: 60f)
    val peakAlt = altitudes.maxOrNull() ?: 0f
    val peakIdx = altitudes.indices.maxByOrNull { altitudes[it] } ?: 12
    val range = (maxA - minA).coerceAtLeast(1f)
    // Y ticks: max, horizon (0°) if in range, min
    val yTicks = buildList {
        add(maxA)
        if (minA < -1f && maxA > 5f) add(0f)
        add(minA)
    }.distinctBy { (it * 10).toInt() }

    fun fracFromTop(a: Float): Float = (1f - (a - minA) / range).coerceIn(0f, 1f)

    Row(modifier = modifier) {
        // Y-axis: altitude in degrees (aligned to plot scale)
        BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .padding(end = 4.dp, bottom = 22.dp)
                .width(44.dp),
        ) {
            val plotH = maxHeight
            yTicks.forEach { tick ->
                val yDp = plotH * fracFromTop(tick)
                Text(
                    text = if (tick == 0f) "0°" else "${tick.toInt()}°",
                    color = if (tick == 0f) Color(0xFF90A4AE) else OnSurfaceMuted,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = yDp - 7.dp),
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    fun xAt(i: Int): Float = w * i / (altitudes.size - 1).toFloat()
                    fun yAt(a: Float): Float = h * (1f - (a - minA) / range)

                    val y0 = yAt(0f)
                    if (0f in minA..maxA) {
                        drawLine(
                            color = Color(0xFF546E7A),
                            start = Offset(0f, y0),
                            end = Offset(w, y0),
                            strokeWidth = 1.5f,
                        )
                    }
                    drawLine(Color(0x22455A64), Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
                    drawLine(Color(0x22455A64), Offset(0f, h), Offset(w, h), strokeWidth = 1f)

                    val path = Path()
                    val fill = Path()
                    altitudes.forEachIndexed { i, a ->
                        val x = xAt(i)
                        val y = yAt(a)
                        if (i == 0) {
                            path.moveTo(x, y)
                            fill.moveTo(x, y0.coerceIn(0f, h))
                            fill.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                    }
                    fill.lineTo(xAt(altitudes.lastIndex), y0.coerceIn(0f, h))
                    fill.close()

                    drawPath(
                        fill,
                        brush = Brush.verticalGradient(
                            listOf(fillColor, Color.Transparent),
                            startY = 0f,
                            endY = h,
                        ),
                    )
                    drawPath(
                        path,
                        color = pathColor,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                    )

                    if (peakAlt > 0) {
                        drawCircle(
                            color = pathColor,
                            radius = 6f,
                            center = Offset(xAt(peakIdx), yAt(peakAlt)),
                        )
                    }
                }

                if (peakAlt > 0) {
                    Text(
                        "peak ${peakAlt.toInt()}°",
                        color = pathColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach {
                    Text(it, color = OnSurfaceMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
