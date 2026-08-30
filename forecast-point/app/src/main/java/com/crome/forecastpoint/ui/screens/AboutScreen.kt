package com.crome.forecastpoint.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.BuildConfig
import com.crome.forecastpoint.R
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark

private const val GITHUB_URL = "https://github.com/crome1394/point-forecast"
private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/crome1394"

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Point Forecast", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
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
            "Privacy\n" +
                "No accounts, ads, or analytics. Location is used only when you choose GPS or the map. " +
                "Data stays on your device; network traffic is HTTPS to public weather and map services only.",
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 13.sp,
        )
        Text(
            "Updates (F-Droid)\n" +
                "From 1.1.9 onward, F-Droid publishes the same developer-signed APK as GitHub. " +
                "If you installed an older F-Droid build (before that switch), uninstall once and " +
                "reinstall — Android cannot update across different signing keys.",
            color = OnSurfaceMuted,
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 13.sp,
        )

        Text(
            "Source code",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        AboutLinkRow(
            iconRes = R.drawable.ic_github,
            iconTint = Color.White,
            label = "GitHub",
            subtitle = GITHUB_URL,
            onClick = { openUrl(GITHUB_URL) },
        )

        Text(
            "Support",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        AboutLinkRow(
            iconRes = R.drawable.ic_buy_me_a_coffee,
            iconTint = Color(0xFFFFDD00),
            label = "Buy Me a Coffee",
            subtitle = "buymeacoffee.com/crome1394",
            onClick = { openUrl(BUY_ME_A_COFFEE_URL) },
        )

        Text(
            "Data & libraries\n" +
                "• Forecasts & alerts: National Weather Service (weather.gov / api.weather.gov)\n" +
                "• Tides / water levels: NOAA CO-OPS (coastal tides, Great Lakes) + USGS NWIS stage (rivers & inland lakes)\n" +
                "• Earthquakes: USGS Earthquake Hazards Program (FDSN)\n" +
                "• Severe weather: NHC active storms, SPC tornado reports, NWS alerts\n" +
                "• Space weather: NOAA SWPC\n" +
                "• Air quality / UV / pressure: Open-Meteo\n" +
                "• Map tiles: OpenStreetMap (via osmdroid)\n" +
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
        Text(
            "Special thanks to my dear friend Javier V. for his ideas and help " +
                "troubleshooting the UX, and the QA.",
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AboutLinkRow(
    @DrawableRes iconRes: Int,
    iconTint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        color = SurfaceDark,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    color = PrimaryBlue,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
