package com.crome.forecastpoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted

@Composable
fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Point Forecast", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Version 1.0.0", color = OnSurfaceMuted, modifier = Modifier.padding(top = 4.dp))
        Text(
            "Independent open-source weather client for U.S. National Weather Service " +
                "point forecasts, hourly data, tides, alerts, map location pick, and a " +
                "home-screen widget. Built with reliable bundled NWS forecast icons " +
                "(works well on CalyxOS and other de-Googled devices).",
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 15.sp,
        )
        Text(
            "Not affiliated with NOAA, the National Weather Service, or Pandamonium Software. " +
                "Not an official government application.",
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 12.dp),
            fontSize = 13.sp,
        )
        Text(
            "Data & libraries\n" +
                "• Forecasts & alerts: National Weather Service (weather.gov / api.weather.gov)\n" +
                "• Tides: NOAA CO-OPS\n" +
                "• Map tiles: OpenStreetMap / CARTO (via osmdroid)\n" +
                "• Geocoding: OpenStreetMap Nominatim\n" +
                "• Icons: NWS forecast icon set (bundled)",
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 13.sp,
        )
        Text(
            "Inspiration\n" +
                "UX inspired by the commercial “NOAA Weather & Tides” Android app " +
                "(Pandamonium Software). Point Forecast is a clean-room reimplementation " +
                "using public data APIs—not their source code or proprietary assets.",
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 13.sp,
        )
    }
}
