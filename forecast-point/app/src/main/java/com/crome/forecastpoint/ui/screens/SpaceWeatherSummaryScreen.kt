package com.crome.forecastpoint.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.data.SpaceWeatherService
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.SurfaceDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SpaceWeatherSummaryScreen(
    snapshot: SpaceWeatherService.Snapshot?,
    watchThreshold: Int = 1,
    activeThreshold: Int = 2,
    forecastHorizonHours: Int = 48,
) {
    var aboutExpanded by remember { mutableStateOf(false) }
    val latestObserved = snapshot?.periods?.lastOrNull { it.status.equals("Observed", true) }
        ?: snapshot?.periods?.lastOrNull()
    val upcoming = snapshot?.periods
        ?.filter { it.status.equals("Predicted", true) }
        ?.take(12)
        .orEmpty()
    val chartPeriods = snapshot?.periods?.takeLast(24).orEmpty()
    val alert = remember(snapshot, watchThreshold, activeThreshold, forecastHorizonHours) {
        snapshot?.alertLevel(
            watchMin = watchThreshold,
            activeMin = activeThreshold,
            forecastHorizonHours = forecastHorizonHours,
        ) ?: SpaceWeatherService.SpaceWeatherAlert.Quiet
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            "NOAA Space Weather Prediction Center",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            "Planetary conditions (not location-specific) · times in UTC",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (snapshot == null) {
            CircularProgressIndicator(
                Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFFCE93D8),
            )
            Text(
                "Loading space weather…",
                color = OnSurfaceMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
            )
            return
        }

        Spacer(Modifier.height(12.dp))

        // Current scales card
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Now", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (alert != SpaceWeatherService.SpaceWeatherAlert.Quiet) {
                        val (label, color) = when (alert) {
                            SpaceWeatherService.SpaceWeatherAlert.Watch ->
                                "Title-bar watch" to Color(0xFFCE93D8)
                            SpaceWeatherService.SpaceWeatherAlert.Active ->
                                "Title-bar active" to Color(0xFFFF8A65)
                            else -> "" to Color.Transparent
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.22f),
                        ) {
                            Text(
                                label,
                                color = color,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                ScaleRow(
                    label = "Geomagnetic (G)",
                    scale = snapshot.currentGScale ?: "—",
                    detail = snapshot.currentGText ?: "—",
                    color = gScaleColor(snapshot.currentGScale),
                )
                ScaleRow(
                    label = "Radio blackout (R)",
                    scale = snapshot.currentRScale ?: "—",
                    detail = snapshot.currentRText ?: "—",
                    color = Color(0xFFFFB74D),
                )
                ScaleRow(
                    label = "Solar radiation (S)",
                    scale = snapshot.currentSScale ?: "—",
                    detail = snapshot.currentSText ?: "—",
                    color = Color(0xFF4FC3F7),
                )
                latestObserved?.let { p ->
                    Text(
                        "Latest Kp ${p.kp?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} · ${p.gScale} · ${p.status}",
                        color = OnSurfaceMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val updated = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }.format(Date(snapshot.updatedAtEpochMs))
                Text("Updated $updated (device time)", color = OnSurfaceMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Kp index (recent)",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "Planetary geomagnetic activity · higher Kp = stronger disturbance (0 quiet … 9 extreme)",
            color = OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (chartPeriods.isNotEmpty()) {
            KpSparkline(
                periods = chartPeriods,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timeline, null, tint = Color(0xFFCE93D8), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Upcoming (predicted)", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                if (upcoming.isEmpty()) {
                    Text("No predicted periods loaded.", color = OnSurfaceMuted, fontSize = 13.sp)
                } else {
                    upcoming.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                p.timeLabelUtc.replace("\n", " "),
                                color = Color(0xFFCFD8DC),
                                fontSize = 13.sp,
                            )
                            Text(
                                "Kp ${p.kp?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} · ${p.gScale}",
                                color = gScaleColor(p.gScale),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
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
                        "About space weather",
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
                        ABOUT_SPACE_WEATHER,
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
private fun ScaleRow(
    label: String,
    scale: String,
    detail: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Bolt, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OnSurfaceMuted, fontSize = 12.sp)
            Text("$scale · $detail", color = Color.White, fontSize = 15.sp)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.22f),
        ) {
            Text(
                scale,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun KpSparkline(
    periods: List<SpaceWeatherService.Period>,
    modifier: Modifier = Modifier,
) {
    val values = periods.map { (it.kp ?: 0.0).toFloat() }
    if (values.size < 2) return
    val maxV = maxOf(9f, values.maxOrNull() ?: 9f)
    val firstLabel = periods.firstOrNull()?.timeLabelUtc?.replace("\n", " ") ?: ""
    val lastLabel = periods.lastOrNull()?.timeLabelUtc?.replace("\n", " ") ?: ""

    Column(
        modifier
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Y-axis: Kp scale
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("Kp ${maxV.toInt()}", color = OnSurfaceMuted, fontSize = 10.sp)
                Text("5 G1", color = Color(0xFFFFB74D), fontSize = 10.sp)
                Text("0", color = OnSurfaceMuted, fontSize = 10.sp)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                fun x(i: Int) = w * i / (values.size - 1).toFloat()
                fun y(v: Float) = h * (1f - v / maxV)

                // Grid lines at Kp 0, 5 (G1), max
                drawLine(Color(0x33455A64), Offset(0f, y(0f)), Offset(w, y(0f)), strokeWidth = 1f)
                val y5 = y(5f)
                drawLine(Color(0x66FFB74D), Offset(0f, y5), Offset(w, y5), strokeWidth = 1.5f)
                drawLine(Color(0x33455A64), Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)

                val path = Path()
                values.forEachIndexed { i, v ->
                    val pt = Offset(x(i), y(v))
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, Color(0xFFCE93D8), style = Stroke(3f, cap = StrokeCap.Round))
                val last = values.last()
                drawCircle(Color(0xFFE1BEE7), 5f, Offset(x(values.lastIndex), y(last)))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(firstLabel, color = OnSurfaceMuted, fontSize = 10.sp, maxLines = 1)
            Text("time (UTC)", color = OnSurfaceMuted, fontSize = 10.sp)
            Text(lastLabel, color = OnSurfaceMuted, fontSize = 10.sp, maxLines = 1)
        }
        Text(
            "Orange line ≈ G1 (minor storm, Kp 5). Peaks above it mean stronger geomagnetic activity.",
            color = OnSurfaceMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun gScaleColor(scale: String?): Color = when (scale?.uppercase(Locale.US)) {
    "G0", "—" -> Color(0xFF81C784)
    "G1" -> Color(0xFFFFF176)
    "G2" -> Color(0xFFFFB74D)
    "G3" -> Color(0xFFFF8A65)
    "G4", "G5" -> Color(0xFFEF5350)
    else -> Color(0xFFCE93D8)
}

/**
 * In-app copy for the expandable About block. Explains SWPC scales, what space weather
 * is (and is not), and how the title-bar sun/moon cue works.
 */
private const val ABOUT_SPACE_WEATHER =
    "What it is\n" +
        "Space weather is driven by the Sun—solar flares, coronal mass ejections (CMEs), " +
        "and the solar wind. Those disturbances reach Earth’s magnetic field and upper " +
        "atmosphere. They can affect radio, GPS, power grids, satellites, aviation on " +
        "polar routes, and aurora visibility.\n\n" +
        "What it is not\n" +
        "Space weather is not the same as tropospheric weather. A geomagnetic storm does " +
        "not mean rain, wind, or temperature changes at your city. The data here are " +
        "planetary (same worldwide), not tied to your selected location.\n\n" +
        "NOAA scales (this app)\n" +
        "• Kp — planetary geomagnetic activity from 0 (quiet) to 9 (extreme), in 3-hour bins.\n" +
        "• G (geomagnetic) — storm severity G0–G5. G1 ≈ Kp 5 (minor); G5 is extreme.\n" +
        "• R (radio blackout) — HF radio / navigation impacts from solar X-ray flares (R0–R5).\n" +
        "• S (solar radiation) — energetic particle storms (S0–S5), mainly polar aviation " +
        "and spacecraft concerns.\n\n" +
        "Title-bar cue (not a system notification)\n" +
        "When conditions reach your thresholds, the sun/moon icon on Forecast and Hourly " +
        "changes color and shows a small bolt. No Android notification is posted.\n" +
        "• Quiet — below your Watch threshold.\n" +
        "• Watch (purple) — current G/R/S or predicted G reaches Watch threshold.\n" +
        "• Active (orange) — reaches Active threshold (higher than Watch).\n\n" +
        "Defaults and Settings\n" +
        "Default Watch = 1 (minor G1/R1/S1), Active = 2 (moderate), look-ahead = 48 hours. " +
        "Change all three under Settings → Space weather title-bar cue. " +
        "G1 is NOAA’s first storm step; raise thresholds if the icon feels too sensitive.\n\n" +
        "Aurora tip: higher Kp/G improves chances at higher latitudes, but a bright moon " +
        "can still wash out faint displays.\n\n" +
        "Source: NOAA Space Weather Prediction Center (SWPC)."
