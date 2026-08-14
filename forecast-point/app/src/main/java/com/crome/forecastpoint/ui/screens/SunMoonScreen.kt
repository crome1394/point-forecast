package com.crome.forecastpoint.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.CelestialCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

enum class CelestialBody { Sun, Moon }

@Composable
fun SunMoonScreen(
    latitude: Double,
    longitude: Double,
    locationName: String?,
    initialBody: CelestialBody = CelestialBody.Sun,
) {
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    val tz = remember { TimeZone.getDefault() }
    val today = remember {
        Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var dayOffset by remember { mutableIntStateOf(0) }

    val day = remember(dayOffset) {
        (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
    }

    val sun = remember(latitude, longitude, dayOffset) {
        CelestialCalculator.sunDay(latitude, longitude, tz, day)
    }
    val moon = remember(latitude, longitude, dayOffset) {
        CelestialCalculator.moonDay(latitude, longitude, tz, day)
    }
    val dayLabelFmt = remember { SimpleDateFormat("EEE", Locale.US) }
    val dateFmt = remember { SimpleDateFormat("d", Locale.US) }
    val monthFmt = remember { SimpleDateFormat("MMM", Locale.US) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Body pills
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CelestialPill(
                selected = body == CelestialBody.Sun,
                icon = Icons.Filled.WbSunny,
                label = "Sun",
                onClick = { body = CelestialBody.Sun },
                selectedColor = Color(0xFFFFB300),
            )
            CelestialPill(
                selected = body == CelestialBody.Moon,
                icon = Icons.Filled.DarkMode,
                label = "Moon",
                onClick = { body = CelestialBody.Moon },
                selectedColor = Color(0xFF90CAF9),
            )
        }

        locationName?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // Day strip: -1 .. +5
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (off in -1..5) {
                val cal = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) }
                val selected = off == dayOffset
                val label = when (off) {
                    -1 -> "Yda"
                    0 -> "Today"
                    1 -> "Tmw"
                    else -> dayLabelFmt.format(cal.time)
                }
                Column(
                    Modifier
                        .width(64.dp)
                        .clickable { dayOffset = off }
                        .background(
                            if (selected) PrimaryBlue.copy(alpha = 0.35f) else Color.Transparent,
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

        // Path chart
        val alts = if (body == CelestialBody.Sun) sun.altitudeByHour else moon.altitudeByHour
        val pathColor = if (body == CelestialBody.Sun) Color(0xFFFFB300) else Color(0xFF90CAF9)
        val fillColor = pathColor.copy(alpha = 0.25f)
        AltitudeChart(
            altitudes = alts,
            pathColor = pathColor,
            fillColor = fillColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("06:00", "12:00", "18:00").forEach {
                Text(it, color = OnSurfaceMuted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(20.dp))

        Text(
            if (body == CelestialBody.Sun) "About the sun" else "About the moon",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = SurfaceDark,
            shape = RoundedCornerShape(16.dp),
        ) {
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
                        "The phase cycle is about 29.5 days. Northern lights chances rise with " +
                        "geomagnetic activity (see Hourly → Space Wx), not moon phase alone—though " +
                        "a bright full moon can wash out fainter auroras."
                },
                color = Color(0xFFCFD8DC),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun CelestialPill(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    selectedColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (selected) selectedColor.copy(alpha = 0.25f) else SurfaceDark,
        modifier = Modifier
            .height(40.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) selectedColor else OnSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (selected) Color.White else OnSurfaceMuted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
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
    Canvas(modifier = modifier) {
        if (altitudes.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val minA = min(-10f, altitudes.minOrNull() ?: -10f)
        val maxA = max(60f, altitudes.maxOrNull() ?: 60f)
        val range = (maxA - minA).coerceAtLeast(1f)

        fun xAt(i: Int): Float = w * i / (altitudes.size - 1).toFloat()
        fun yAt(a: Float): Float = h * (1f - (a - minA) / range)

        // Horizon
        val y0 = yAt(0f)
        drawLine(
            color = Color(0xFF546E7A),
            start = Offset(0f, y0),
            end = Offset(w, y0),
            strokeWidth = 1.5f,
        )

        val path = Path()
        val fill = Path()
        altitudes.forEachIndexed { i, a ->
            val x = xAt(i)
            val y = yAt(a)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, y0)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(xAt(altitudes.lastIndex), y0)
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

        // Current-ish marker at solar noon-ish peak for sun path
        val peakIdx = altitudes.indices.maxByOrNull { altitudes[it] } ?: 12
        if (altitudes[peakIdx] > 0) {
            drawCircle(
                color = pathColor,
                radius = 6f,
                center = Offset(xAt(peakIdx), yAt(altitudes[peakIdx])),
            )
        }
    }
}
