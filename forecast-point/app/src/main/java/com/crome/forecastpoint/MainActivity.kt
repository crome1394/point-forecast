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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.data.SavedLocation
import com.crome.forecastpoint.data.SpaceWeatherService
import com.crome.forecastpoint.ui.WeatherViewModel
import kotlin.math.roundToInt
import com.crome.forecastpoint.ui.screens.AboutScreen
import com.crome.forecastpoint.ui.screens.CelestialBody
import com.crome.forecastpoint.ui.screens.ForecastScreen
import com.crome.forecastpoint.ui.screens.HourlyScreen
import com.crome.forecastpoint.ui.screens.MapScreen
import com.crome.forecastpoint.ui.screens.SearchScreen
import com.crome.forecastpoint.ui.screens.SettingsScreen
import com.crome.forecastpoint.ui.screens.SunMoonScreen
import com.crome.forecastpoint.ui.theme.ForecastPointTheme
import com.crome.forecastpoint.ui.theme.PrimaryBlue
import com.crome.forecastpoint.ui.theme.SurfaceDark
import com.crome.forecastpoint.util.CelestialCalculator
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
                val expandAdvisories by viewModel.expandAdvisories.collectAsState()
                val showTitleSearch by viewModel.showTitleSearch.collectAsState()
                val showTitleSunMoon by viewModel.showTitleSunMoon.collectAsState()
                val hourlyTabConfig by viewModel.hourlyTabConfig.collectAsState()
                val drawerNavConfig by viewModel.drawerNavConfig.collectAsState()
                val spaceWeatherWatchThreshold by viewModel.spaceWeatherWatchThreshold.collectAsState()
                val spaceWeatherActiveThreshold by viewModel.spaceWeatherActiveThreshold.collectAsState()
                val spaceWeatherForecastHorizonHours by viewModel.spaceWeatherForecastHorizonHours.collectAsState()
                val spaceWeather by viewModel.spaceWeather.collectAsState()
                val earthquakes by viewModel.earthquakes.collectAsState()
                val earthquakesLoading by viewModel.earthquakesLoading.collectAsState()
                val severeWeather by viewModel.severeWeather.collectAsState()
                val severeWeatherLoading by viewModel.severeWeatherLoading.collectAsState()
                val mapFocusRadiusMiles by viewModel.mapFocusRadiusMiles.collectAsState()
                val hazardHistoryDays by viewModel.hazardHistoryDays.collectAsState()

                var screen by remember { mutableStateOf(AppScreen.Forecast) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                /** Long-press target: show Rename / Remove menu. */
                var cityActionTarget by remember { mutableStateOf<SavedLocation?>(null) }
                /** City being renamed (shows text field dialog). */
                var cityRenameTarget by remember { mutableStateOf<SavedLocation?>(null) }
                var cityRenameText by remember { mutableStateOf("") }
                var sunMoonMenuOpen by remember { mutableStateOf(false) }
                var sunMoonBody by remember { mutableStateOf(CelestialBody.Sun) }

                // System back / gesture: nested screens → main forecast (not exit app)
                BackHandler(enabled = screen != AppScreen.Forecast || drawerState.isOpen) {
                    when {
                        drawerState.isOpen -> scope.launch { drawerState.close() }
                        cityRenameTarget != null -> cityRenameTarget = null
                        cityActionTarget != null -> cityActionTarget = null
                        sunMoonMenuOpen -> sunMoonMenuOpen = false
                        screen != AppScreen.Forecast -> screen = AppScreen.Forecast
                    }
                }

                val screenTitle = when (screen) {
                    AppScreen.Forecast -> "Point Forecast"
                    AppScreen.Hourly -> "Hourly Forecast"
                    AppScreen.Search -> "Add City"
                    AppScreen.Map -> "Map"
                    AppScreen.Settings -> "Settings"
                    AppScreen.About -> "About"
                    AppScreen.SunMoon -> when (sunMoonBody) {
                        CelestialBody.Sun -> "Sun"
                        CelestialBody.Moon -> "Moon"
                        CelestialBody.SpaceWeather -> "Space Weather"
                        CelestialBody.Earthquakes -> "Earthquakes"
                        CelestialBody.Storms -> "Severe Weather"
                    }
                }

                // Title-bar icon: sun by day, moon by night at the active (or default) point
                val celestialLat = snapshot?.latitude ?: 39.8283
                val celestialLon = snapshot?.longitude ?: -98.5795
                val showDayIcon = remember(
                    celestialLat,
                    celestialLon,
                    snapshot?.updatedAtEpochMs,
                    snapshot?.timeZoneId,
                ) {
                    val tz = snapshot?.timeZoneId?.let { java.util.TimeZone.getTimeZone(it) }
                        ?: com.crome.forecastpoint.util.LocationTimeZone.resolve(
                            celestialLat,
                            celestialLon,
                        )
                    CelestialCalculator.isDaytime(celestialLat, celestialLon, tz)
                }
                val spaceAlert = remember(
                    spaceWeather,
                    spaceWeatherWatchThreshold,
                    spaceWeatherActiveThreshold,
                    spaceWeatherForecastHorizonHours,
                ) {
                    spaceWeather?.alertLevel(
                        watchMin = spaceWeatherWatchThreshold,
                        activeMin = spaceWeatherActiveThreshold,
                        forecastHorizonHours = spaceWeatherForecastHorizonHours,
                    ) ?: SpaceWeatherService.SpaceWeatherAlert.Quiet
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

                // Disable edge-swipe to open the drawer while on Map (and other non-Forecast
                // screens). Otherwise a single-finger map pan is stolen as "open menu".
                // Still allow swipe-to-close when the drawer is already open.
                val drawerGesturesEnabled =
                    drawerState.isOpen || screen == AppScreen.Forecast

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerGesturesEnabled,
                    drawerContent = {
                        AppDrawer(
                            favorites = favorites,
                            activeId = activeId,
                            navConfig = drawerNavConfig,
                            onNavOrderChange = { viewModel.setDrawerNavOrder(it) },
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
                            onSun = {
                                sunMoonBody = CelestialBody.Sun
                                screen = AppScreen.SunMoon
                                scope.launch { drawerState.close() }
                            },
                            onMoon = {
                                sunMoonBody = CelestialBody.Moon
                                screen = AppScreen.SunMoon
                                scope.launch { drawerState.close() }
                            },
                            onSpaceWeather = {
                                sunMoonBody = CelestialBody.SpaceWeather
                                screen = AppScreen.SunMoon
                                scope.launch { drawerState.close() }
                            },
                            onEarthquakes = {
                                sunMoonBody = CelestialBody.Earthquakes
                                screen = AppScreen.SunMoon
                                scope.launch { drawerState.close() }
                            },
                            onStorms = {
                                sunMoonBody = CelestialBody.Storms
                                screen = AppScreen.SunMoon
                                scope.launch { drawerState.close() }
                            },
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
                    val showMainActions = screen == AppScreen.Forecast || screen == AppScreen.Hourly
                    val showSunMoonTabs = screen == AppScreen.SunMoon

                    @Composable
                    fun TitleBarActions(tintWhite: Boolean) {
                        when {
                            showMainActions -> {
                                if (showTitleSearch) {
                                    IconButton(onClick = { openAddCity() }) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = "Add City",
                                            tint = if (tintWhite) Color.White else Color.Unspecified,
                                        )
                                    }
                                }
                                if (showTitleSunMoon) {
                                    Box {
                                        IconButton(onClick = { sunMoonMenuOpen = true }) {
                                            SunMoonTitleIcon(
                                                showDayIcon = showDayIcon,
                                                alert = spaceAlert,
                                            )
                                        }
                                        SunMoonPickerMenu(
                                            expanded = sunMoonMenuOpen,
                                            onDismiss = { sunMoonMenuOpen = false },
                                            onPick = { body ->
                                                sunMoonBody = body
                                                sunMoonMenuOpen = false
                                                screen = AppScreen.SunMoon
                                            },
                                        )
                                    }
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
                                        tint = if (tintWhite) Color.White else Color.Unspecified,
                                    )
                                }
                            }
                            showSunMoonTabs -> {
                                CelestialBodyTitleTabs(
                                    selected = sunMoonBody,
                                    onSelect = { sunMoonBody = it },
                                )
                            }
                        }
                    }

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
                                        TitleBarActions(tintWhite = false)
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
                                        TitleBarActions(tintWhite = true)
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
                                    expandAdvisories = expandAdvisories,
                                    onToggleFavorite = { viewModel.toggleActiveFavorite() },
                                    onOpenHourly = { screen = AppScreen.Hourly },
                                    onDayClick = { screen = AppScreen.Hourly },
                                    onAddCity = { openAddCity() },
                                    onOpenMap = { openMap() },
                                    onRefresh = { viewModel.manualRefresh() },
                                )
                                AppScreen.Hourly -> {
                                    androidx.compose.runtime.LaunchedEffect(Unit) {
                                        viewModel.ensureSpaceWeather()
                                    }
                                    HourlyScreen(
                                        hourly = snapshot?.hourly.orEmpty(),
                                        tideInfo = snapshot?.tideInfo,
                                        spaceWeather = spaceWeather,
                                        tabConfig = hourlyTabConfig,
                                    )
                                }
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
                                    expandAdvisories = expandAdvisories,
                                    showTitleSearch = showTitleSearch,
                                    showTitleSunMoon = showTitleSunMoon,
                                    hourlyTabConfig = hourlyTabConfig,
                                    drawerNavConfig = drawerNavConfig,
                                    mapFocusRadiusMiles = mapFocusRadiusMiles,
                                    hazardHistoryDays = hazardHistoryDays,
                                    spaceWeatherWatchThreshold = spaceWeatherWatchThreshold,
                                    spaceWeatherActiveThreshold = spaceWeatherActiveThreshold,
                                    spaceWeatherForecastHorizonHours = spaceWeatherForecastHorizonHours,
                                    onAutoUpdateChange = { viewModel.setAutoUpdate(it) },
                                    onIntervalChange = { viewModel.setIntervalMinutes(it) },
                                    onTitleBarAtBottomChange = { viewModel.setTitleBarAtBottom(it) },
                                    onWidgetShowHighLowChange = { viewModel.setWidgetShowHighLow(it) },
                                    onMapSearchAtBottomChange = { viewModel.setMapSearchAtBottom(it) },
                                    onExpandCurrentConditionsChange = {
                                        viewModel.setExpandCurrentConditions(it)
                                    },
                                    onExpandAdvisoriesChange = { viewModel.setExpandAdvisories(it) },
                                    onShowTitleSearchChange = { viewModel.setShowTitleSearch(it) },
                                    onShowTitleSunMoonChange = { viewModel.setShowTitleSunMoon(it) },
                                    onHourlyTabConfigChange = { viewModel.setHourlyTabConfig(it) },
                                    onDrawerNavConfigChange = { viewModel.setDrawerNavConfig(it) },
                                    onMapFocusRadiusMilesChange = {
                                        viewModel.setMapFocusRadiusMiles(it)
                                    },
                                    onHazardHistoryDaysChange = {
                                        viewModel.setHazardHistoryDays(it)
                                    },
                                    onSpaceWeatherWatchThresholdChange = {
                                        viewModel.setSpaceWeatherWatchThreshold(it)
                                    },
                                    onSpaceWeatherActiveThresholdChange = {
                                        viewModel.setSpaceWeatherActiveThreshold(it)
                                    },
                                    onSpaceWeatherForecastHorizonHoursChange = {
                                        viewModel.setSpaceWeatherForecastHorizonHours(it)
                                    },
                                    onManualRefresh = {
                                        viewModel.manualRefresh()
                                        screen = AppScreen.Forecast
                                    },
                                )
                                AppScreen.About -> AboutScreen()
                                AppScreen.SunMoon -> SunMoonScreen(
                                    latitude = celestialLat,
                                    longitude = celestialLon,
                                    locationName = snapshot?.locationName,
                                    body = sunMoonBody,
                                    timeZoneId = snapshot?.timeZoneId,
                                    spaceWeather = spaceWeather,
                                    spaceWeatherWatchThreshold = spaceWeatherWatchThreshold,
                                    spaceWeatherActiveThreshold = spaceWeatherActiveThreshold,
                                    spaceWeatherForecastHorizonHours = spaceWeatherForecastHorizonHours,
                                    earthquakes = earthquakes,
                                    earthquakesLoading = earthquakesLoading,
                                    onEnsureEarthquakes = { radiusMiles, historyDays, startMs, endMs ->
                                        viewModel.ensureEarthquakes(
                                            celestialLat,
                                            celestialLon,
                                            focusRadiusMiles = radiusMiles,
                                            historyDays = historyDays,
                                            historyStartMs = startMs,
                                            historyEndMs = endMs,
                                        )
                                    },
                                    severeWeather = severeWeather,
                                    severeWeatherLoading = severeWeatherLoading,
                                    onEnsureSevereWeather = { radiusMiles, historyDays, startMs, endMs ->
                                        viewModel.ensureSevereWeather(
                                            celestialLat,
                                            celestialLon,
                                            focusRadiusMiles = radiusMiles,
                                            historyDays = historyDays,
                                            historyStartMs = startMs,
                                            historyEndMs = endMs,
                                        )
                                    },
                                    mapFocusRadiusMiles = mapFocusRadiusMiles,
                                    hazardHistoryDays = hazardHistoryDays,
                                    onEnsureSpaceWeather = { viewModel.ensureSpaceWeather() },
                                    onResolveTornadoPlace = { lat, lon ->
                                        viewModel.reverseGeocode(lat, lon).name
                                    },
                                )
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

/**
 * Title-bar sun/moon icon. When SWPC conditions are elevated (Watch/Active),
 * tint shifts toward space-weather purple/orange — not a system notification.
 */
@Composable
private fun SunMoonTitleIcon(
    showDayIcon: Boolean,
    alert: SpaceWeatherService.SpaceWeatherAlert,
) {
    val (tint, description) = when (alert) {
        SpaceWeatherService.SpaceWeatherAlert.Quiet -> {
            if (showDayIcon) {
                Color(0xFFFFE082) to "Sun and moon"
            } else {
                Color(0xFFBBDEFB) to "Sun and moon"
            }
        }
        SpaceWeatherService.SpaceWeatherAlert.Watch -> {
            Color(0xFFCE93D8) to "Sun and moon — space weather watch (minor activity)"
        }
        SpaceWeatherService.SpaceWeatherAlert.Active -> {
            Color(0xFFFF8A65) to "Sun and moon — space weather active (moderate or greater)"
        }
    }
    Box {
        Icon(
            imageVector = if (showDayIcon) Icons.Filled.WbSunny else Icons.Filled.NightsStay,
            contentDescription = description,
            tint = tint,
        )
        if (alert != SpaceWeatherService.SpaceWeatherAlert.Quiet) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = if (alert == SpaceWeatherService.SpaceWeatherAlert.Active) {
                    Color(0xFFFF5252)
                } else {
                    Color(0xFFE1BEE7)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .padding(0.dp),
            )
        }
    }
}

@Composable
private fun SunMoonPickerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (CelestialBody) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1B262C),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Sun, moon, space & quakes",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            CelestialMenuPill(
                icon = Icons.Filled.WbSunny,
                label = "Sun",
                tint = Color(0xFFFFB300),
                onClick = { onPick(CelestialBody.Sun) },
            )
            CelestialMenuPill(
                icon = Icons.Filled.DarkMode,
                label = "Moon",
                tint = Color(0xFF90CAF9),
                onClick = { onPick(CelestialBody.Moon) },
            )
            CelestialMenuPill(
                icon = Icons.Filled.Bolt,
                label = "Space Weather",
                tint = Color(0xFFCE93D8),
                onClick = { onPick(CelestialBody.SpaceWeather) },
            )
            CelestialMenuPill(
                icon = Icons.Filled.Public,
                label = "Earthquakes",
                tint = Color(0xFFFF8A65),
                onClick = { onPick(CelestialBody.Earthquakes) },
            )
            CelestialMenuPill(
                icon = Icons.Filled.Thunderstorm,
                label = "Severe Weather",
                tint = Color(0xFFFF7043),
                onClick = { onPick(CelestialBody.Storms) },
            )
        }
    }
}

/** Compact body switcher for the title bar on the celestial screen. */
@Composable
private fun CelestialBodyTitleTabs(
    selected: CelestialBody,
    onSelect: (CelestialBody) -> Unit,
) {
    Row(
        Modifier.padding(end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleBodyTab(
            icon = Icons.Filled.WbSunny,
            contentDescription = "Sun",
            selected = selected == CelestialBody.Sun,
            tint = Color(0xFFFFB300),
            onClick = { onSelect(CelestialBody.Sun) },
        )
        TitleBodyTab(
            icon = Icons.Filled.DarkMode,
            contentDescription = "Moon",
            selected = selected == CelestialBody.Moon,
            tint = Color(0xFF90CAF9),
            onClick = { onSelect(CelestialBody.Moon) },
        )
        TitleBodyTab(
            icon = Icons.Filled.Bolt,
            contentDescription = "Space Weather",
            selected = selected == CelestialBody.SpaceWeather,
            tint = Color(0xFFCE93D8),
            onClick = { onSelect(CelestialBody.SpaceWeather) },
        )
        TitleBodyTab(
            icon = Icons.Filled.Public,
            contentDescription = "Earthquakes",
            selected = selected == CelestialBody.Earthquakes,
            tint = Color(0xFFFF8A65),
            onClick = { onSelect(CelestialBody.Earthquakes) },
        )
        TitleBodyTab(
            icon = Icons.Filled.Thunderstorm,
            contentDescription = "Severe Weather",
            selected = selected == CelestialBody.Storms,
            tint = Color(0xFFFF7043),
            onClick = { onSelect(CelestialBody.Storms) },
        )
    }
}

@Composable
private fun TitleBodyTab(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) tint.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) tint else Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun CelestialMenuPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = tint.copy(alpha = 0.18f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

private enum class AppScreen { Forecast, Hourly, Search, Map, Settings, About, SunMoon }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    favorites: List<SavedLocation>,
    activeId: String?,
    navConfig: List<com.crome.forecastpoint.data.DrawerNavConfigItem>,
    onNavOrderChange: (List<String>) -> Unit,
    onForecast: () -> Unit,
    onCurrentLocation: () -> Unit,
    onAddCity: () -> Unit,
    onMap: () -> Unit,
    onSun: () -> Unit,
    onMoon: () -> Unit,
    onSpaceWeather: () -> Unit,
    onEarthquakes: () -> Unit,
    onStorms: () -> Unit,
    onSelectFavorite: (SavedLocation) -> Unit,
    onLongPressFavorite: (SavedLocation) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val actions = mapOf(
        "Forecast" to onForecast,
        "CurrentLocation" to onCurrentLocation,
        "Map" to onMap,
        "Sun" to onSun,
        "Moon" to onMoon,
        "SpaceWeather" to onSpaceWeather,
        "Earthquakes" to onEarthquakes,
        "Storms" to onStorms,
        "AddCity" to onAddCity,
    )
    val icons = mapOf(
        "Forecast" to Icons.AutoMirrored.Filled.List,
        "CurrentLocation" to Icons.Filled.MyLocation,
        "Map" to Icons.Filled.Map,
        "Sun" to Icons.Filled.WbSunny,
        "Moon" to Icons.Filled.DarkMode,
        "SpaceWeather" to Icons.Filled.Bolt,
        "Earthquakes" to Icons.Filled.Public,
        "Storms" to Icons.Filled.Thunderstorm,
        "AddCity" to Icons.Filled.Add,
    )

    // Visible items only (Settings controls which appear)
    val visibleIds = remember(navConfig) {
        navConfig.filter { it.enabled }.map { it.id }
    }
    // Local reorder state — do NOT re-key pointerInput on every swap (that cancels the drag)
    var order by remember { mutableStateOf(visibleIds) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 38.dp.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // Snapshot of order when drag starts; commit on end
    var dragStartOrder by remember { mutableStateOf(order) }
    LaunchedEffect(visibleIds) {
        if (draggingIndex < 0 && visibleIds != order) order = visibleIds
    }

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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Point Forecast",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                "Long-press & drag to reorder · visibility in Settings",
                color = Color(0xFF90A4AE),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            // Nav items in a fixed Column (not LazyColumn) so reorders don't reset drag
            Column(Modifier.fillMaxWidth()) {
                order.forEachIndexed { index, id ->
                    key(id) {
                        val isDragging = index == draggingIndex
                        val icon = icons[id] ?: Icons.Filled.Menu
                        val label = PreferencesRepository.drawerNavDisplayName(id)
                        val onClick = actions[id] ?: {}
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .zIndex(if (isDragging) 2f else 0f)
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = if (isDragging) dragOffsetY.roundToInt() else 0,
                                    )
                                }
                                .background(
                                    if (isDragging) Color(0xFF37474F) else Color.Transparent,
                                )
                                .pointerInput(Unit) {
                                    // Stable key() + Unit keeps the gesture alive across reorders
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            val idx = order.indexOf(id)
                                            if (idx < 0) return@detectDragGesturesAfterLongPress
                                            draggingIndex = idx
                                            dragOffsetY = 0f
                                            dragStartOrder = order
                                        },
                                        onDragCancel = {
                                            order = dragStartOrder
                                            draggingIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingIndex = -1
                                            dragOffsetY = 0f
                                            onNavOrderChange(order)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val from = draggingIndex
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            dragOffsetY += dragAmount.y
                                            val shift = (dragOffsetY / rowHeightPx).toInt()
                                            if (shift != 0) {
                                                val to = (from + shift).coerceIn(0, order.lastIndex)
                                                if (to != from) {
                                                    val next = order.toMutableList()
                                                    val item = next.removeAt(from)
                                                    next.add(to, item)
                                                    order = next
                                                    draggingIndex = to
                                                    dragOffsetY -= shift * rowHeightPx
                                                }
                                            }
                                        },
                                    )
                                }
                                .clickable(enabled = draggingIndex < 0, onClick = onClick)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = Color(0xFF78909C),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF37474F),
                modifier = Modifier.padding(vertical = 2.dp),
            )

            LazyColumn(Modifier.weight(1f)) {
                if (favorites.isNotEmpty()) {
                    item {
                        Text(
                            "Saved cities",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFCC80),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(loc.name, color = Color.White, fontSize = 14.sp)
                    }
                }
                if (favorites.isEmpty()) {
                    item {
                        Text(
                            "No saved cities yet.\nTap Add City to search.",
                            color = Color(0xFF90A4AE),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                item {
                    HorizontalDivider(
                        color = Color(0xFF37474F),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    DrawerRow(Icons.Filled.Settings, "Settings", onClick = onSettings)
                    DrawerRow(Icons.Filled.Info, "About", onClick = onAbout)
                    Text(
                        "Long-press city to rename/remove · drag menu to reorder",
                        color = Color(0xFF607D8B),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
