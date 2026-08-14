package com.crome.forecastpoint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.crome.forecastpoint.data.SavedLocation
import com.crome.forecastpoint.ui.WeatherViewModel
import com.crome.forecastpoint.ui.screens.AboutScreen
import com.crome.forecastpoint.ui.screens.ForecastScreen
import com.crome.forecastpoint.ui.screens.HourlyScreen
import com.crome.forecastpoint.ui.screens.MapScreen
import com.crome.forecastpoint.ui.screens.SearchScreen
import com.crome.forecastpoint.ui.screens.SettingsScreen
import com.crome.forecastpoint.ui.theme.ForecastPointTheme
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.RadarUrl
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ForecastPointTheme {
                val snapshot by viewModel.snapshot.collectAsState()
                val loading by viewModel.loading.collectAsState()
                val error by viewModel.error.collectAsState()
                val favorites by viewModel.favorites.collectAsState()
                val autoUpdate by viewModel.autoUpdate.collectAsState()
                val interval by viewModel.intervalMinutes.collectAsState()
                val activeId by viewModel.activeLocationId.collectAsState()
                val searchResults by viewModel.searchResults.collectAsState()
                val searching by viewModel.searching.collectAsState()
                val titleBarAtBottom by viewModel.titleBarAtBottom.collectAsState()
                val widgetShowHighLow by viewModel.widgetShowHighLow.collectAsState()
                val mapSearchAtBottom by viewModel.mapSearchAtBottom.collectAsState()
                val expandCurrentConditions by viewModel.expandCurrentConditions.collectAsState()
                val showTidesTab by viewModel.showTidesTab.collectAsState()

                var screen by remember { mutableStateOf(AppScreen.Forecast) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                /** Long-press target: show Rename / Remove menu. */
                var cityActionTarget by remember { mutableStateOf<SavedLocation?>(null) }
                /** City being renamed (shows text field dialog). */
                var cityRenameTarget by remember { mutableStateOf<SavedLocation?>(null) }
                var cityRenameText by remember { mutableStateOf("") }

                // System back / gesture: nested screens → main forecast (not exit app)
                BackHandler(enabled = screen != AppScreen.Forecast || drawerState.isOpen) {
                    when {
                        drawerState.isOpen -> scope.launch { drawerState.close() }
                        cityRenameTarget != null -> cityRenameTarget = null
                        cityActionTarget != null -> cityActionTarget = null
                        screen != AppScreen.Forecast -> screen = AppScreen.Forecast
                    }
                }

                val screenTitle = when (screen) {
                    AppScreen.Forecast -> "Forecast Point"
                    AppScreen.Hourly -> "Hourly Forecast"
                    AppScreen.Search -> "Add City"
                    AppScreen.Map -> "Map"
                    AppScreen.Settings -> "Settings"
                    AppScreen.About -> "About"
                }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { grants ->
                    if (grants.values.any { it }) {
                        requestLastLocation()?.let { viewModel.useDeviceLocation(it) }
                    }
                }

                fun requestCurrentLocation() {
                    val fine = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) {
                        requestLastLocation()?.let { viewModel.useDeviceLocation(it) }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                }

                fun openAddCity() {
                    screen = AppScreen.Search
                    scope.launch { drawerState.close() }
                }

                fun openMap() {
                    screen = AppScreen.Map
                    scope.launch { drawerState.close() }
                }

                fun openRadar() {
                    val url = snapshot?.let {
                        RadarUrl.forCoordinates(it.latitude, it.longitude)
                    } ?: RadarUrl.generic()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }

                // Long-press menu: Rename or Remove
                cityActionTarget?.let { loc ->
                    AlertDialog(
                        onDismissRequest = { cityActionTarget = null },
                        title = { Text(loc.name) },
                        text = { Text("What would you like to do with this saved city?") },
                        confirmButton = {
                            TextButton(onClick = {
                                cityRenameText = loc.name
                                cityRenameTarget = loc
                                cityActionTarget = null
                            }) { Text("Rename") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    viewModel.removeFavorite(loc.id)
                                    cityActionTarget = null
                                }) {
                                    Text("Remove", color = Color(0xFFEF9A9A))
                                }
                                TextButton(onClick = { cityActionTarget = null }) {
                                    Text("Cancel")
                                }
                            }
                        },
                    )
                }

                // Rename dialog
                cityRenameTarget?.let { loc ->
                    AlertDialog(
                        onDismissRequest = { cityRenameTarget = null },
                        title = { Text("Rename city") },
                        text = {
                            OutlinedTextField(
                                value = cityRenameText,
                                onValueChange = { cityRenameText = it },
                                singleLine = true,
                                label = { Text("Display name") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val name = cityRenameText.trim()
                                    if (name.isNotEmpty()) {
                                        viewModel.renameFavorite(loc.id, name)
                                    }
                                    cityRenameTarget = null
                                },
                                enabled = cityRenameText.trim().isNotEmpty(),
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { cityRenameTarget = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(
                            favorites = favorites,
                            activeId = activeId,
                            onForecast = {
                                screen = AppScreen.Forecast
                                scope.launch { drawerState.close() }
                            },
                            onCurrentLocation = {
                                scope.launch { drawerState.close() }
                                requestCurrentLocation()
                                screen = AppScreen.Forecast
                            },
                            onAddCity = { openAddCity() },
                            onMap = { openMap() },
                            onSelectFavorite = { loc ->
                                viewModel.selectFavorite(loc)
                                screen = AppScreen.Forecast
                                scope.launch { drawerState.close() }
                            },
                            onLongPressFavorite = { loc -> cityActionTarget = loc },
                            onSettings = {
                                screen = AppScreen.Settings
                                scope.launch { drawerState.close() }
                            },
                            onAbout = {
                                screen = AppScreen.About
                                scope.launch { drawerState.close() }
                            },
                        )
                    },
                ) {
                    val onNavClick: () -> Unit = {
                        if (screen == AppScreen.Forecast) {
                            scope.launch { drawerState.open() }
                        } else {
                            screen = AppScreen.Forecast
                        }
                    }
                    val showActions = screen == AppScreen.Forecast || screen == AppScreen.Hourly

                    Scaffold(
                        topBar = {
                            if (!titleBarAtBottom) {
                                TopAppBar(
                                    title = {
                                        Text(screenTitle, fontWeight = FontWeight.SemiBold)
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = onNavClick) {
                                            Icon(
                                                if (screen == AppScreen.Forecast) {
                                                    Icons.Filled.Menu
                                                } else {
                                                    Icons.AutoMirrored.Filled.ArrowBack
                                                },
                                                contentDescription = if (screen == AppScreen.Forecast) {
                                                    "Menu"
                                                } else {
                                                    "Back"
                                                },
                                            )
                                        }
                                    },
                                    actions = {
                                        if (showActions) {
                                            IconButton(onClick = { openAddCity() }) {
                                                Icon(Icons.Filled.Search, contentDescription = "Add City")
                                            }
                                            IconButton(onClick = { openRadar() }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_radar_colorful),
                                                    contentDescription = "National Weather Radar",
                                                    // Keep multicolor drawable as-authored
                                                    tint = Color.Unspecified,
                                                )
                                            }
                                            IconButton(onClick = { viewModel.manualRefresh() }) {
                                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = PrimaryBlue,
                                        titleContentColor = Color.White,
                                        navigationIconContentColor = Color.White,
                                        actionIconContentColor = Color.White,
                                    ),
                                )
                            }
                        },
                        bottomBar = {
                            if (titleBarAtBottom) {
                                BottomAppBar(
                                    containerColor = PrimaryBlue,
                                    contentColor = Color.White,
                                    actions = {
                                        IconButton(onClick = onNavClick) {
                                            Icon(
                                                if (screen == AppScreen.Forecast) {
                                                    Icons.Filled.Menu
                                                } else {
                                                    Icons.AutoMirrored.Filled.ArrowBack
                                                },
                                                contentDescription = if (screen == AppScreen.Forecast) {
                                                    "Menu"
                                                } else {
                                                    "Back"
                                                },
                                            )
                                        }
                                        Text(
                                            text = screenTitle,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 4.dp),
                                        )
                                        if (showActions) {
                                            IconButton(onClick = { openAddCity() }) {
                                                Icon(
                                                    Icons.Filled.Search,
                                                    contentDescription = "Add City",
                                                    tint = Color.White,
                                                )
                                            }
                                            IconButton(onClick = { openRadar() }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_radar_colorful),
                                                    contentDescription = "National Weather Radar",
                                                    tint = Color.Unspecified,
                                                )
                                            }
                                            IconButton(onClick = { viewModel.manualRefresh() }) {
                                                Icon(
                                                    Icons.Filled.Refresh,
                                                    contentDescription = "Refresh",
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        },
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                            when (screen) {
                                AppScreen.Forecast -> ForecastScreen(
                                    snapshot = snapshot,
                                    loading = loading,
                                    error = error,
                                    isFavorite = favorites.any { it.id == activeId },
                                    expandCurrentConditions = expandCurrentConditions,
                                    onToggleFavorite = { },
                                    onOpenHourly = { screen = AppScreen.Hourly },
                                    onDayClick = { screen = AppScreen.Hourly },
                                    onAddCity = { openAddCity() },
                                    onOpenMap = { openMap() },
                                )
                                AppScreen.Hourly -> HourlyScreen(
                                    hourly = snapshot?.hourly.orEmpty(),
                                    tideInfo = snapshot?.tideInfo,
                                    showTidesTab = showTidesTab,
                                )
                                AppScreen.Search -> SearchScreen(
                                    results = searchResults,
                                    searching = searching,
                                    onQueryChange = { viewModel.search(it) },
                                    onSelect = {
                                        viewModel.selectGeocode(it)
                                        screen = AppScreen.Forecast
                                    },
                                )
                                AppScreen.Map -> MapScreen(
                                    initialLat = snapshot?.latitude ?: 39.8283,
                                    initialLon = snapshot?.longitude ?: -98.5795,
                                    initialZoom = if (snapshot != null) 8.0 else 4.0,
                                    searchAtBottom = mapSearchAtBottom,
                                    onSearch = { q -> viewModel.searchPlaces(q) },
                                    onReverseGeocode = { lat, lon ->
                                        viewModel.reverseGeocode(lat, lon)
                                    },
                                    onConfirmLocation = { result ->
                                        viewModel.confirmMapLocation(result)
                                        screen = AppScreen.Forecast
                                    },
                                )
                                AppScreen.Settings -> SettingsScreen(
                                    autoUpdate = autoUpdate,
                                    intervalMinutes = interval,
                                    titleBarAtBottom = titleBarAtBottom,
                                    widgetShowHighLow = widgetShowHighLow,
                                    mapSearchAtBottom = mapSearchAtBottom,
                                    expandCurrentConditions = expandCurrentConditions,
                                    showTidesTab = showTidesTab,
                                    onAutoUpdateChange = { viewModel.setAutoUpdate(it) },
                                    onIntervalChange = { viewModel.setIntervalMinutes(it) },
                                    onTitleBarAtBottomChange = { viewModel.setTitleBarAtBottom(it) },
                                    onWidgetShowHighLowChange = { viewModel.setWidgetShowHighLow(it) },
                                    onMapSearchAtBottomChange = { viewModel.setMapSearchAtBottom(it) },
                                    onExpandCurrentConditionsChange = {
                                        viewModel.setExpandCurrentConditions(it)
                                    },
                                    onShowTidesTabChange = { viewModel.setShowTidesTab(it) },
                                    onManualRefresh = {
                                        viewModel.manualRefresh()
                                        screen = AppScreen.Forecast
                                    },
                                )
                                AppScreen.About -> AboutScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestLastLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers.mapNotNull { provider ->
            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    lm.getLastKnownLocation(provider)
                } else {
                    null
                }
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }
    }
}

private enum class AppScreen { Forecast, Hourly, Search, Map, Settings, About }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    favorites: List<SavedLocation>,
    activeId: String?,
    onForecast: () -> Unit,
    onCurrentLocation: () -> Unit,
    onAddCity: () -> Unit,
    onMap: () -> Unit,
    onSelectFavorite: (SavedLocation) -> Unit,
    onLongPressFavorite: (SavedLocation) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = SurfaceDark,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .background(SurfaceDark),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Forecast Point",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(Modifier.weight(1f)) {
                item {
                    DrawerRow(Icons.AutoMirrored.Filled.List, "Forecast", onClick = onForecast)
                    DrawerRow(Icons.Filled.MyLocation, "Current Location", onClick = onCurrentLocation)
                    DrawerRow(Icons.Filled.Map, "Map", onClick = onMap)
                    DrawerRow(Icons.Filled.Add, "Add City", onClick = onAddCity)
                    HorizontalDivider(color = Color(0xFF37474F), modifier = Modifier.padding(vertical = 4.dp))
                    if (favorites.isNotEmpty()) {
                        Text(
                            "Saved cities",
                            color = Color(0xFF90A4AE),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                items(favorites, key = { it.id }) { loc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSelectFavorite(loc) },
                                onLongClick = { onLongPressFavorite(loc) },
                            )
                            .background(
                                if (loc.id == activeId) Color(0xFF37474F) else Color.Transparent,
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFCC80),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(loc.name, color = Color.White, fontSize = 15.sp)
                    }
                }
                if (favorites.isEmpty()) {
                    item {
                        Text(
                            "No saved cities yet.\nTap Add City to search.",
                            color = Color(0xFF90A4AE),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    HorizontalDivider(color = Color(0xFF37474F), modifier = Modifier.padding(vertical = 4.dp))
                    DrawerRow(Icons.Filled.Settings, "Settings", onClick = onSettings)
                    DrawerRow(Icons.Filled.Info, "About", onClick = onAbout)
                    Text(
                        "Long-press a city to rename or remove it",
                        color = Color(0xFF607D8B),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = Color.White,
            unselectedIconColor = Color.White,
        ),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
