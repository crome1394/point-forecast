package com.crome.forecastpoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.data.HourlyRow
import com.crome.forecastpoint.data.SpaceWeatherService
import com.crome.forecastpoint.data.TideInfo
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.util.WeatherMath
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class HourlyTab(val title: String) {
    Temperature("TEMPERATURE"),
    Precipitation("PRECIPITATION"),
    Wind("WIND"),
    Tides("TIDES"),
    Conditions("CONDITIONS"),
    AirQuality("AIR QUALITY"),
    Visibility("VISIBILITY"),
    Pressure("PRESSURE"),
    UvIndex("UV INDEX"),
    SpaceWeather("SPACE WX"),
}

private val TableFont = FontFamily.SansSerif
private val HeaderStyle = TextStyle(
    fontFamily = TableFont,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    color = Color(0xFFB0BEC5),
    textAlign = TextAlign.Center,
)
private val TimeCellStyle = TextStyle(
    fontFamily = TableFont,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    color = Color(0xFF212121),
    textAlign = TextAlign.Center,
    lineHeight = 13.sp,
)
private val TimeColumnBg = Color(0xFFECEFF1)
private val TimeColumnBgAlt = Color(0xFFDEE2E5)
private val RowBgEven = Color(0xFF263238)
private val RowBgOdd = Color(0xFF1E2A30)
private val HeaderBg = Color(0xFF263238)
private val TimeColWidth = 62.dp
private val HeaderHeight = 32.dp
private val RowHeight = 36.dp

/** Dew point / cool metrics — blue like the original app. */
private val DewPointBlue = Color(0xFF42A5F5)
private val PopBlue = Color(0xFF64B5F6)
private val TideTeal = Color(0xFF4DD0E1)
private val WindWhite = Color(0xFFECEFF1)
private val ConditionsWhite = Color(0xFFF5F5F5)

@Composable
fun HourlyScreen(
    hourly: List<HourlyRow>,
    tideInfo: TideInfo? = null,
    showTidesTab: Boolean = true,
    showSpaceWeather: Boolean = true,
    showAirQuality: Boolean = true,
    showVisibility: Boolean = true,
    showPressure: Boolean = true,
    showUvIndex: Boolean = true,
    spaceWeather: SpaceWeatherService.Snapshot? = null,
) {
    val hasLocalHourly = hourly.isNotEmpty()
    val tabs = remember(
        showTidesTab,
        showSpaceWeather,
        showAirQuality,
        showVisibility,
        showPressure,
        showUvIndex,
        hasLocalHourly,
    ) {
        HourlyTab.entries.filter { tab ->
            when (tab) {
                HourlyTab.Tides -> showTidesTab && hasLocalHourly
                HourlyTab.SpaceWeather -> showSpaceWeather
                HourlyTab.AirQuality -> showAirQuality && hasLocalHourly
                HourlyTab.Visibility -> showVisibility && hasLocalHourly
                HourlyTab.Pressure -> showPressure && hasLocalHourly
                HourlyTab.UvIndex -> showUvIndex && hasLocalHourly
                else -> hasLocalHourly
            }
        }
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val tabModels = remember(
        hourly,
        showTidesTab,
        showSpaceWeather,
        showAirQuality,
        showVisibility,
        showPressure,
        showUvIndex,
        spaceWeather,
    ) {
        buildTabModels(
            hourly = hourly,
            includeTides = showTidesTab,
            includeSpaceWeather = showSpaceWeather,
            includeAirQuality = showAirQuality,
            includeVisibility = showVisibility,
            includePressure = showPressure,
            includeUvIndex = showUvIndex,
            spaceWeather = spaceWeather,
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
            containerColor = PrimaryBlue,
            contentColor = Color.White,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                val page = pagerState.currentPage
                if (page in tabPositions.indices) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[page]),
                        color = Color.White,
                        height = 2.dp,
                    )
                }
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, t ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.height(40.dp),
                    text = {
                        Text(
                            text = t.title,
                            fontFamily = TableFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                    },
                )
            }
        }

        if (tabModels.isEmpty()) {
            Text(
                "Hourly data unavailable for this location.",
                color = Color(0xFFB0BEC5),
                fontFamily = TableFont,
                modifier = Modifier.padding(24.dp),
            )
            return
        }

        when (tabs.getOrNull(pagerState.currentPage)) {
            HourlyTab.Tides -> TideStationBar(tideInfo)
            HourlyTab.SpaceWeather -> SpaceWeatherBar(spaceWeather)
            else -> Unit
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.25f,
            ),
            key = { tabs[it].name },
        ) { page ->
            val model = tabModels[page]
            HourlyTable(
                headers = model.headers,
                rows = model.rows,
            )
        }
    }
}

@Composable
private fun TideStationBar(tideInfo: TideInfo?) {
    val text = when {
        tideInfo == null -> "Tide: station lookup unavailable"
        !tideInfo.unavailableReason.isNullOrBlank() && tideInfo.stationName.isBlank() ->
            "Tide: ${tideInfo.unavailableReason}"
        !tideInfo.unavailableReason.isNullOrBlank() ->
            "Tide: ${tideInfo.unavailableReason}"
        else -> "Station: ${tideInfo.stationName} · ${"%.0f".format(tideInfo.distanceMiles)} mi · MLLW ft"
    }
    Text(
        text = text,
        color = Color(0xFFB0BEC5),
        fontSize = 11.sp,
        fontFamily = TableFont,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E2A30))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SpaceWeatherBar(snap: SpaceWeatherService.Snapshot?) {
    val text = when {
        snap == null -> "Space weather: loading NOAA SWPC… (planetary Kp, UTC)"
        snap.currentGScale != null ->
            "NOAA SWPC · Now ${snap.currentGScale}" +
                (snap.currentGText?.let { " ($it)" } ?: "") +
                " · 3-hour Kp (UTC) · not location-specific"
        else -> "NOAA SWPC · planetary Kp (UTC) · not location-specific"
    }
    Text(
        text = text,
        color = Color(0xFFB0BEC5),
        fontSize = 11.sp,
        fontFamily = TableFont,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E2A30))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Immutable
private data class ColoredCell(
    val text: String,
    val color: Color,
)

@Immutable
private data class TabModel(
    val headers: List<String>,
    val rows: List<HourlyTableRow>,
)

@Immutable
private data class HourlyTableRow(
    val time: String,
    val cells: List<ColoredCell>,
)

/**
 * Temperature / feels-like heat scale matching the original NOAA app screenshot:
 * cool greens → yellow-greens → yellow → orange as it warms.
 */
private fun temperatureColor(tempF: Int?): Color {
    if (tempF == null) return Color.White
    val t = tempF.toFloat()
    // Key stops (F → RGB) sampled from classic NWS-style hourly coloring
    val stops = listOf(
        20f to Color(0xFF26C6DA),  // cold cyan
        32f to Color(0xFF66BB6A),  // cool green
        42f to Color(0xFF81C784),  // soft green
        50f to Color(0xFFAED581),  // yellow-green
        55f to Color(0xFFDCE775),  // lime
        62f to Color(0xFFFFEE58),  // yellow
        70f to Color(0xFFFFCA28),  // amber
        78f to Color(0xFFFFA726),  // orange
        88f to Color(0xFFFF7043),  // deep orange
        98f to Color(0xFFEF5350),  // hot red
    )
    if (t <= stops.first().first) return stops.first().second
    if (t >= stops.last().first) return stops.last().second
    for (i in 0 until stops.lastIndex) {
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        if (t in t0..t1) {
            val f = (t - t0) / (t1 - t0)
            return lerpColor(c0, c1, f)
        }
    }
    return Color.White
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = 1f,
    )
}

private fun popColor(pop: Int?): Color {
    if (pop == null) return Color.White
    // Stronger blue as chance rises
    val f = (pop / 100f).coerceIn(0f, 1f)
    return lerpColor(Color(0xFF90CAF9), Color(0xFF1565C0), f)
}

private fun buildTabModels(
    hourly: List<HourlyRow>,
    includeTides: Boolean = true,
    includeSpaceWeather: Boolean = true,
    includeAirQuality: Boolean = true,
    includeVisibility: Boolean = true,
    includePressure: Boolean = true,
    includeUvIndex: Boolean = true,
    spaceWeather: SpaceWeatherService.Snapshot? = null,
): List<TabModel> {
    // Space weather can still show when local hourly is empty (e.g. no city yet)
    if (hourly.isEmpty() && !(includeSpaceWeather && spaceWeather != null && spaceWeather.periods.isNotEmpty())) {
        return emptyList()
    }
    val times = hourly.map { formatTimeLabel(it) }
    val models = mutableListOf<TabModel>()
    if (hourly.isNotEmpty()) {
        models += TabModel(
            headers = listOf("Temperature", "Feels Like", "Dew Point"),
            rows = hourly.mapIndexed { i, row ->
                HourlyTableRow(
                    time = times[i],
                    cells = listOf(
                        ColoredCell(
                            row.temperatureF?.let { "$it° F" }.orEmpty(),
                            temperatureColor(row.temperatureF),
                        ),
                        ColoredCell(
                            row.feelsLikeF?.let { "$it° F" }.orEmpty(),
                            temperatureColor(row.feelsLikeF),
                        ),
                        ColoredCell(
                            row.dewPointF?.let { "$it° F" }.orEmpty(),
                            DewPointBlue,
                        ),
                    ),
                )
            },
        )
        models += TabModel(
            headers = listOf("Chance", "Amount", "Cloud Cover", "Humidity"),
            rows = hourly.mapIndexed { i, row ->
                HourlyTableRow(
                    time = times[i],
                    cells = listOf(
                        ColoredCell(row.popPct?.let { "$it%" }.orEmpty(), popColor(row.popPct)),
                        ColoredCell(row.precipIn.orEmpty(), PopBlue),
                        ColoredCell(
                            row.cloudCoverPct?.let { "$it%" }.orEmpty(),
                            Color(0xFFB0BEC5),
                        ),
                        ColoredCell(
                            row.humidityPct?.let { "$it%" }.orEmpty(),
                            Color(0xFF80CBC4),
                        ),
                    ),
                )
            },
        )
        models += TabModel(
            headers = listOf("Speed", "Gust", "Direction"),
            rows = hourly.mapIndexed { i, row ->
                HourlyTableRow(
                    time = times[i],
                    cells = listOf(
                        ColoredCell(row.windSpeedMph?.let { "$it mph" }.orEmpty(), WindWhite),
                        ColoredCell(row.windGustMph?.let { "$it mph" }.orEmpty(), Color(0xFFFFCC80)),
                        ColoredCell(row.windDirection.orEmpty(), Color(0xFF80DEEA)),
                    ),
                )
            },
        )
        if (includeTides) {
            models += TabModel(
                headers = listOf("Height", "Trend"),
                rows = hourly.mapIndexed { i, row ->
                    val trendColor = when (row.tideTrend) {
                        "Rising" -> Color(0xFF81C784)
                        "Falling" -> Color(0xFFE57373)
                        else -> TideTeal
                    }
                    HourlyTableRow(
                        time = times[i],
                        cells = listOf(
                            ColoredCell(
                                row.tideFt?.let { String.format(Locale.US, "%.2f ft", it) }.orEmpty(),
                                TideTeal,
                            ),
                            ColoredCell(row.tideTrend.orEmpty(), trendColor),
                        ),
                    )
                },
            )
        }
        models += TabModel(
            headers = listOf("Conditions"),
            rows = hourly.mapIndexed { i, row ->
                HourlyTableRow(
                    time = times[i],
                    cells = listOf(ColoredCell(row.weather, ConditionsWhite)),
                )
            },
        )
        if (includeAirQuality) {
            models += TabModel(
                headers = listOf("US AQI", "Category", "PM2.5"),
                rows = hourly.mapIndexed { i, row ->
                    val aqi = row.usAqi
                    HourlyTableRow(
                        time = times[i],
                        cells = listOf(
                            ColoredCell(aqi?.toString().orEmpty(), aqiColor(aqi)),
                            ColoredCell(aqiCategory(aqi), aqiColor(aqi)),
                            ColoredCell(
                                row.pm25?.let { String.format(Locale.US, "%.1f", it) }.orEmpty(),
                                Color(0xFFB0BEC5),
                            ),
                        ),
                    )
                },
            )
        }
        if (includeVisibility) {
            models += TabModel(
                headers = listOf("Visibility", "Miles"),
                rows = hourly.mapIndexed { i, row ->
                    val mi = row.visibilityMi
                    HourlyTableRow(
                        time = times[i],
                        cells = listOf(
                            ColoredCell(visibilityLabel(mi), visibilityColor(mi)),
                            ColoredCell(
                                mi?.let { String.format(Locale.US, "%.1f mi", it) }.orEmpty(),
                                Color(0xFF80DEEA),
                            ),
                        ),
                    )
                },
            )
        }
        if (includePressure) {
            models += TabModel(
                headers = listOf("Pressure", "in Hg"),
                rows = hourly.mapIndexed { i, row ->
                    val mb = row.pressureMb
                    HourlyTableRow(
                        time = times[i],
                        cells = listOf(
                            ColoredCell(
                                mb?.let { String.format(Locale.US, "%.0f mb", it) }.orEmpty(),
                                Color(0xFFCE93D8),
                            ),
                            ColoredCell(
                                mb?.let {
                                    String.format(Locale.US, "%.2f", WeatherMath.hPaToInHg(it))
                                }.orEmpty(),
                                Color(0xFFB0BEC5),
                            ),
                        ),
                    )
                },
            )
        }
        if (includeUvIndex) {
            models += TabModel(
                headers = listOf("UV Index", "Risk"),
                rows = hourly.mapIndexed { i, row ->
                    val uv = row.uvIndex
                    HourlyTableRow(
                        time = times[i],
                        cells = listOf(
                            ColoredCell(
                                uv?.let { String.format(Locale.US, "%.1f", it) }.orEmpty(),
                                uvColor(uv),
                            ),
                            ColoredCell(uvRisk(uv), uvColor(uv)),
                        ),
                    )
                },
            )
        }
    }
    if (includeSpaceWeather) {
        val periods = spaceWeather?.periods.orEmpty()
        models += TabModel(
            headers = listOf("Kp", "G-Scale", "Status"),
            rows = if (periods.isEmpty()) {
                listOf(
                    HourlyTableRow(
                        time = "—",
                        cells = listOf(
                            ColoredCell("…", Color.White),
                            ColoredCell("…", Color.White),
                            ColoredCell("Loading or unavailable", Color(0xFFB0BEC5)),
                        ),
                    ),
                )
            } else {
                periods.map { p ->
                    HourlyTableRow(
                        time = p.timeLabelUtc,
                        cells = listOf(
                            ColoredCell(
                                p.kp?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                                kpColor(p.kp),
                            ),
                            ColoredCell(p.gScale, gScaleColor(p.gScale)),
                            ColoredCell(p.status, Color(0xFFB0BEC5)),
                        ),
                    )
                }
            },
        )
    }
    return models
}

private fun kpColor(kp: Double?): Color {
    if (kp == null) return Color.White
    return when {
        kp < 4.0 -> Color(0xFF81C784)
        kp < 5.0 -> Color(0xFFFFF176)
        kp < 6.0 -> Color(0xFFFFB74D)
        kp < 7.0 -> Color(0xFFFF8A65)
        else -> Color(0xFFEF5350)
    }
}

private fun gScaleColor(scale: String): Color = when (scale.uppercase(Locale.US)) {
    "G0", "—" -> Color(0xFF81C784)
    "G1" -> Color(0xFFFFF176)
    "G2" -> Color(0xFFFFB74D)
    "G3" -> Color(0xFFFF8A65)
    "G4", "G5" -> Color(0xFFEF5350)
    else -> Color.White
}

private fun aqiCategory(aqi: Int?): String = when {
    aqi == null -> "—"
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "USG"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very Unhealthy"
    else -> "Hazardous"
}

private fun aqiColor(aqi: Int?): Color = when {
    aqi == null -> Color.White
    aqi <= 50 -> Color(0xFF66BB6A)
    aqi <= 100 -> Color(0xFFFFEE58)
    aqi <= 150 -> Color(0xFFFFA726)
    aqi <= 200 -> Color(0xFFEF5350)
    aqi <= 300 -> Color(0xFFAB47BC)
    else -> Color(0xFF8D6E63)
}

private fun visibilityLabel(mi: Double?): String = when {
    mi == null -> "—"
    mi >= 10 -> "Excellent"
    mi >= 6 -> "Good"
    mi >= 3 -> "Moderate"
    mi >= 1 -> "Poor"
    else -> "Very Poor"
}

private fun visibilityColor(mi: Double?): Color = when {
    mi == null -> Color.White
    mi >= 10 -> Color(0xFF66BB6A)
    mi >= 6 -> Color(0xFF9CCC65)
    mi >= 3 -> Color(0xFFFFEE58)
    mi >= 1 -> Color(0xFFFFA726)
    else -> Color(0xFFEF5350)
}

private fun uvRisk(uv: Double?): String = when {
    uv == null -> "—"
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very High"
    else -> "Extreme"
}

private fun uvColor(uv: Double?): Color = when {
    uv == null -> Color.White
    uv < 3 -> Color(0xFF66BB6A)
    uv < 6 -> Color(0xFFFFEE58)
    uv < 8 -> Color(0xFFFFA726)
    uv < 11 -> Color(0xFFEF5350)
    else -> Color(0xFFAB47BC)
}


@Composable
private fun HourlyTable(
    headers: List<String>,
    rows: List<HourlyTableRow>,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .height(HeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(TimeColWidth)
                    .fillMaxHeight()
                    .background(HeaderBg)
                    .border(width = 0.5.dp, color = Color(0xFF37474F)),
            )
            headers.forEach { h ->
                Text(
                    text = h,
                    style = HeaderStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                )
            }
        }
        HorizontalDivider(color = Color(0xFF37474F), thickness = 1.dp)

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(
                items = rows,
                key = { index, _ -> index },
                contentType = { _, _ -> "hourly_row" },
            ) { index, row ->
                val dataBg = if (index % 2 == 0) RowBgEven else RowBgOdd
                val timeBg = if (index % 2 == 0) TimeColumnBg else TimeColumnBgAlt
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .background(dataBg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(TimeColWidth)
                            .fillMaxHeight()
                            .background(timeBg)
                            .border(width = 0.5.dp, color = Color(0xFFCFD8DC)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = row.time,
                            style = TimeCellStyle,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                    row.cells.forEach { cell ->
                        Text(
                            text = cell.text,
                            fontFamily = TableFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = cell.color,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeLabel(row: HourlyRow): String {
    val epoch = row.epochSec
    if (epoch != null) {
        val date = Date(epoch * 1000L)
        val dayFmt = SimpleDateFormat("EEE M/d", Locale.US)
        val timeFmt = SimpleDateFormat("h a", Locale.US)
        dayFmt.timeZone = TimeZone.getDefault()
        timeFmt.timeZone = TimeZone.getDefault()
        return "${dayFmt.format(date)}\n${timeFmt.format(date)}"
    }
    val head = row.periodLabel
        .removePrefix("This ")
        .take(6)
    val time = row.timeLabel
        .replace("am", "AM", ignoreCase = true)
        .replace("pm", "PM", ignoreCase = true)
    return "$head\n$time"
}
