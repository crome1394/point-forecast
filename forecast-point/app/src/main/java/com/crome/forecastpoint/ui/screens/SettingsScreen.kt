package com.crome.forecastpoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.ui.theme.OnSurfaceMuted
import com.crome.forecastpoint.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    autoUpdate: Boolean,
    intervalMinutes: Int,
    titleBarAtBottom: Boolean,
    widgetShowHighLow: Boolean,
    mapSearchAtBottom: Boolean,
    expandCurrentConditions: Boolean,
    showTidesTab: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onTitleBarAtBottomChange: (Boolean) -> Unit,
    onWidgetShowHighLowChange: (Boolean) -> Unit,
    onMapSearchAtBottomChange: (Boolean) -> Unit,
    onExpandCurrentConditionsChange: (Boolean) -> Unit,
    onShowTidesTabChange: (Boolean) -> Unit,
    onManualRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Display", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Title bar at bottom", color = Color.White, fontSize = 16.sp)
                Text(
                    if (titleBarAtBottom) {
                        "Title and actions are at the bottom of the screen"
                    } else {
                        "Title and actions are at the top of the screen"
                    },
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = titleBarAtBottom,
                onCheckedChange = onTitleBarAtBottomChange,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Expand Current Conditions", color = Color.White, fontSize = 16.sp)
                Text(
                    if (expandCurrentConditions) {
                        "Details start open when you open the app"
                    } else {
                        "Details start collapsed (tap the row to expand)"
                    },
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = expandCurrentConditions,
                onCheckedChange = onExpandCurrentConditionsChange,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show tides in hourly", color = Color.White, fontSize = 16.sp)
                Text(
                    if (showTidesTab) {
                        "Tides tab appears on the Hourly screen (when a station is nearby)"
                    } else {
                        "Tides tab is hidden — useful inland"
                    },
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = showTidesTab,
                onCheckedChange = onShowTidesTabChange,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Map search at bottom", color = Color.White, fontSize = 16.sp)
                Text(
                    if (mapSearchAtBottom) {
                        "Search bar appears at the bottom of the map"
                    } else {
                        "Search bar appears at the top of the map (default)"
                    },
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = mapSearchAtBottom,
                onCheckedChange = onMapSearchAtBottomChange,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Widget", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show high / low temperatures", color = Color.White, fontSize = 16.sp)
                Text(
                    if (widgetShowHighLow) {
                        "Each day shows ↑ high and ↓ low"
                    } else {
                        "Each slot shows one period temperature (e.g. Tue AM 83°) — like the classic NWS widget"
                    },
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = widgetShowHighLow,
                onCheckedChange = onWidgetShowHighLowChange,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Updates", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Auto-update", color = Color.White, fontSize = 16.sp)
                Text(
                    "Periodically refresh forecast and widget",
                    color = OnSurfaceMuted,
                    fontSize = 13.sp,
                )
            }
            Switch(checked = autoUpdate, onCheckedChange = onAutoUpdateChange)
        }

        Spacer(Modifier.height(12.dp))

        var expanded by remember { mutableStateOf(false) }
        val label = intervalLabel(intervalMinutes)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            TextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Update interval") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                enabled = autoUpdate,
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PreferencesRepository.INTERVAL_OPTIONS.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(intervalLabel(minutes)) },
                        onClick = {
                            onIntervalChange(minutes)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Manual", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Refresh now",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .clickable(onClick = onManualRefresh)
                .padding(16.dp),
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Data from the National Weather Service (weather.gov). " +
                "Icons are the official NWS forecast icons, bundled for reliable display on all devices including CalyxOS.",
            color = OnSurfaceMuted,
            fontSize = 13.sp,
        )
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes minutes"
}
