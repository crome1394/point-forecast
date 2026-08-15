package com.crome.forecastpoint.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.math.roundToInt

/**
 * Stylish ad-hoc explore panel for hazard screens.
 * Does **not** write Settings — only changes the current session’s look-around radius.
 */
@Composable
fun HazardExploreRadiusCard(
    exploreRadiusMiles: Int,
    settingsDefaultMiles: Int,
    onRadiusChange: (Int) -> Unit,
    accent: Color,
    title: String = "Explore radius",
    subtitle: String = "Ad-hoc only — does not change Settings default",
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
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                    ),
                )
                .padding(16.dp),
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PreferencesRepository.MAP_FOCUS_RADIUS_CHIPS.forEach { miles ->
                    val selected = miles == exploreRadiusMiles
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) accent.copy(alpha = 0.35f) else Color(0xFF2A363C),
                        modifier = Modifier
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
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                )
                .padding(16.dp),
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
