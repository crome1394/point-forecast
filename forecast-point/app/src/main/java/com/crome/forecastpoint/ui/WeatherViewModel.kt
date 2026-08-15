package com.crome.forecastpoint.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crome.forecastpoint.data.GeocodeResult
import com.crome.forecastpoint.data.DrawerNavConfigItem
import com.crome.forecastpoint.data.EarthquakeService
import com.crome.forecastpoint.data.HourlyTabConfigItem
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.data.SavedLocation
import com.crome.forecastpoint.data.SevereWeatherService
import com.crome.forecastpoint.data.SpaceWeatherService
import com.crome.forecastpoint.data.WeatherRepository
import com.crome.forecastpoint.data.WeatherSnapshot
import com.crome.forecastpoint.worker.WeatherUpdateScheduler
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = WeatherRepository(app.applicationContext)
    private val prefs = repo.preferences
    private val spaceWeatherService = SpaceWeatherService()
    private val earthquakeService = EarthquakeService()
    private val severeWeatherService = SevereWeatherService()

    val snapshot: StateFlow<WeatherSnapshot?> = repo.snapshot
    val loading: StateFlow<Boolean> = repo.loading
    val error: StateFlow<String?> = repo.error

    private val share = SharingStarted.WhileSubscribed(5_000)

    val favorites = prefs.favorites.stateIn(viewModelScope, share, emptyList())
    val autoUpdate = prefs.autoUpdateEnabled.stateIn(viewModelScope, share, true)
    val intervalMinutes =
        prefs.updateIntervalMinutes.stateIn(
            viewModelScope,
            share,
            PreferencesRepository.DEFAULT_INTERVAL_MIN,
        )
    val activeLocationId = prefs.activeLocationId.stateIn(viewModelScope, share, null)
    val titleBarAtBottom = prefs.titleBarAtBottom.stateIn(viewModelScope, share, false)
    val widgetShowHighLow = prefs.widgetShowHighLow.stateIn(viewModelScope, share, false)
    val mapSearchAtBottom = prefs.mapSearchAtBottom.stateIn(viewModelScope, share, false)
    val expandCurrentConditions =
        prefs.expandCurrentConditions.stateIn(viewModelScope, share, false)
    val expandAdvisories = prefs.expandAdvisories.stateIn(viewModelScope, share, true)
    val showTitleSearch = prefs.showTitleSearch.stateIn(viewModelScope, share, true)
    val showTitleSunMoon = prefs.showTitleSunMoon.stateIn(viewModelScope, share, true)
    val hourlyTabConfig = prefs.hourlyTabConfig.stateIn(
        viewModelScope,
        share,
        PreferencesRepository.defaultHourlyTabConfig(),
    )
    val drawerNavConfig = prefs.drawerNavConfig.stateIn(
        viewModelScope,
        share,
        PreferencesRepository.defaultDrawerNavConfig(),
    )
    val mapFocusRadiusMiles = prefs.mapFocusRadiusMiles.stateIn(
        viewModelScope,
        share,
        PreferencesRepository.DEFAULT_MAP_FOCUS_RADIUS_MILES,
    )
    val hazardHistoryDays = prefs.hazardHistoryDays.stateIn(
        viewModelScope,
        share,
        PreferencesRepository.DEFAULT_HAZARD_HISTORY_DAYS,
    )
    val spaceWeatherWatchThreshold =
        prefs.spaceWeatherWatchThreshold.stateIn(
            viewModelScope,
            share,
            PreferencesRepository.DEFAULT_SW_WATCH_THRESHOLD,
        )
    val spaceWeatherActiveThreshold =
        prefs.spaceWeatherActiveThreshold.stateIn(
            viewModelScope,
            share,
            PreferencesRepository.DEFAULT_SW_ACTIVE_THRESHOLD,
        )
    val spaceWeatherForecastHorizonHours =
        prefs.spaceWeatherForecastHorizonHours.stateIn(
            viewModelScope,
            share,
            PreferencesRepository.DEFAULT_SW_HORIZON_HOURS,
        )

    private val _spaceWeather = MutableStateFlow<SpaceWeatherService.Snapshot?>(null)
    val spaceWeather: StateFlow<SpaceWeatherService.Snapshot?> = _spaceWeather.asStateFlow()

    private val _earthquakes = MutableStateFlow<EarthquakeService.Snapshot?>(null)
    val earthquakes: StateFlow<EarthquakeService.Snapshot?> = _earthquakes.asStateFlow()

    private val _earthquakesLoading = MutableStateFlow(false)
    val earthquakesLoading: StateFlow<Boolean> = _earthquakesLoading.asStateFlow()

    private val _severeWeather = MutableStateFlow<SevereWeatherService.Snapshot?>(null)
    val severeWeather: StateFlow<SevereWeatherService.Snapshot?> = _severeWeather.asStateFlow()

    private val _severeWeatherLoading = MutableStateFlow(false)
    val severeWeatherLoading: StateFlow<Boolean> = _severeWeatherLoading.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodeResult>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null
    private var earthquakeJob: Job? = null
    private var severeWeatherJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repo.bootstrap()
                if (prefs.getActiveLocationOnce() != null) {
                    repo.refreshActive(manual = false)
                    // Planetary SWPC feed only after a location exists (not on empty cold start).
                    refreshSpaceWeather()
                }
                WeatherUpdateScheduler.applyFromPrefs(getApplication())
            }
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            runCatching { repo.refreshActive(manual = true) }
            if (prefs.getActiveLocationOnce() != null || snapshot.value != null) {
                refreshSpaceWeather()
            }
            val snap = snapshot.value
            if (snap != null) {
                refreshEarthquakes(snap.latitude, snap.longitude)
                refreshSevereWeather(snap.latitude, snap.longitude)
            }
        }
    }

    /** Load SWPC when the user opens Space Weather / needs the hourly tab indicator. */
    fun ensureSpaceWeather() {
        viewModelScope.launch {
            val current = _spaceWeather.value
            if (current != null &&
                System.currentTimeMillis() - current.updatedAtEpochMs < 15 * 60 * 1000L
            ) {
                return@launch
            }
            refreshSpaceWeather()
        }
    }

    private suspend fun refreshSpaceWeather() {
        runCatching {
            _spaceWeather.value = spaceWeatherService.fetch()
        }
    }

    /**
     * Load USGS quakes around a point.
     * @param focusRadiusMiles optional ad-hoc radius (does not change Settings default).
     * @param historyDays optional ad-hoc look-back when custom range is null.
     * @param historyStartMs / [historyEndMs] optional custom date range.
     */
    fun ensureEarthquakes(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int? = null,
        historyDays: Int? = null,
        historyStartMs: Long? = null,
        historyEndMs: Long? = null,
    ) {
        val radius = focusRadiusMiles ?: mapFocusRadiusMiles.value
        val days = historyDays ?: hazardHistoryDays.value
        val current = _earthquakes.value
        val rangeMatches = if (historyStartMs != null && historyEndMs != null) {
            current != null &&
                current.historyStartMs == historyStartMs &&
                current.historyEndMs == historyEndMs
        } else {
            current != null &&
                current.historyDays == days &&
                System.currentTimeMillis() - current.historyEndMs < 15 * 60 * 1000L
        }
        if (current != null &&
            kotlin.math.abs(current.latitude - latitude) < 0.05 &&
            kotlin.math.abs(current.longitude - longitude) < 0.05 &&
            kotlin.math.abs(current.radiusKm - radius * 1.609344) < 5.0 &&
            rangeMatches &&
            System.currentTimeMillis() - current.updatedAtEpochMs < 15 * 60 * 1000L
        ) {
            return
        }
        earthquakeJob?.cancel()
        earthquakeJob = viewModelScope.launch {
            refreshEarthquakes(latitude, longitude, radius, days, historyStartMs, historyEndMs)
        }
    }

    private suspend fun refreshEarthquakes(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int = mapFocusRadiusMiles.value,
        historyDays: Int = hazardHistoryDays.value,
        historyStartMs: Long? = null,
        historyEndMs: Long? = null,
    ) {
        _earthquakesLoading.value = true
        runCatching {
            _earthquakes.value = earthquakeService.fetchAround(
                latitude,
                longitude,
                focusRadiusMiles = focusRadiusMiles,
                historyDays = historyDays,
                historyStartMs = historyStartMs,
                historyEndMs = historyEndMs,
            )
        }.onFailure {
            _earthquakes.value = EarthquakeService.Snapshot(
                latitude = latitude,
                longitude = longitude,
                radiusKm = 0.0,
                historyDays = historyDays,
                historyStartMs = historyStartMs ?: 0L,
                historyEndMs = historyEndMs ?: 0L,
                recentAll = emptyList(),
                recentNotable = emptyList(),
                historical = emptyList(),
                updatedAtEpochMs = System.currentTimeMillis(),
                error = it.message ?: "Failed to load earthquakes",
            )
        }
        _earthquakesLoading.value = false
    }

    /**
     * Load severe weather context (tropical cyclones, tornado reports, local alerts).
     */
    fun ensureSevereWeather(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int? = null,
        historyDays: Int? = null,
        historyStartMs: Long? = null,
        historyEndMs: Long? = null,
    ) {
        val radius = focusRadiusMiles ?: mapFocusRadiusMiles.value
        val days = historyDays ?: hazardHistoryDays.value
        val current = _severeWeather.value
        val rangeMatches = if (historyStartMs != null && historyEndMs != null) {
            current != null &&
                current.historyStartMs == historyStartMs &&
                current.historyEndMs == historyEndMs
        } else {
            current != null &&
                current.historyDays == days &&
                System.currentTimeMillis() - current.historyEndMs < 10 * 60 * 1000L
        }
        if (current != null &&
            kotlin.math.abs(current.latitude - latitude) < 0.05 &&
            kotlin.math.abs(current.longitude - longitude) < 0.05 &&
            rangeMatches &&
            System.currentTimeMillis() - current.updatedAtEpochMs < 10 * 60 * 1000L &&
            current.querySummary.contains("${radius} mi")
        ) {
            return
        }
        severeWeatherJob?.cancel()
        severeWeatherJob = viewModelScope.launch {
            refreshSevereWeather(latitude, longitude, radius, days, historyStartMs, historyEndMs)
        }
    }

    private suspend fun refreshSevereWeather(
        latitude: Double,
        longitude: Double,
        focusRadiusMiles: Int = mapFocusRadiusMiles.value,
        historyDays: Int = hazardHistoryDays.value,
        historyStartMs: Long? = null,
        historyEndMs: Long? = null,
    ) {
        _severeWeatherLoading.value = true
        runCatching {
            _severeWeather.value = severeWeatherService.fetchAround(
                latitude,
                longitude,
                focusRadiusMiles = focusRadiusMiles,
                historyDays = historyDays,
                historyStartMs = historyStartMs,
                historyEndMs = historyEndMs,
            )
        }.onFailure {
            _severeWeather.value = SevereWeatherService.Snapshot(
                latitude = latitude,
                longitude = longitude,
                tropicalStorms = emptyList(),
                tornadoReports = emptyList(),
                localAlerts = emptyList(),
                historyDays = historyDays,
                historyStartMs = historyStartMs ?: 0L,
                historyEndMs = historyEndMs ?: 0L,
                updatedAtEpochMs = System.currentTimeMillis(),
                error = it.message ?: "Failed to load severe weather",
            )
        }
        _severeWeatherLoading.value = false
    }

    /**
     * Star on Forecast header: add active place to saved cities, or remove it.
     * Stays on the same weather point either way.
     */
    fun toggleActiveFavorite() {
        viewModelScope.launch {
            val snap = snapshot.value ?: return@launch
            val id = activeLocationId.value
                ?: UUID.nameUUIDFromBytes(
                    "${snap.latitude},${snap.longitude}".toByteArray(),
                ).toString()
            val already = favorites.value.any { it.id == id }
            if (already) {
                prefs.removeFavorite(id)
                prefs.setLastTransientLocation(
                    SavedLocation(
                        id = id,
                        name = snap.locationName,
                        latitude = snap.latitude,
                        longitude = snap.longitude,
                        isFavorite = false,
                    ),
                )
                prefs.setActiveLocationId(id)
            } else {
                var name = snap.locationName
                if (name.equals("Current Location", ignoreCase = true)) {
                    runCatching {
                        name = repo.reverseGeocode(snap.latitude, snap.longitude).name
                    }
                }
                val loc = SavedLocation(
                    id = id,
                    name = name.ifBlank { snap.locationName },
                    latitude = snap.latitude,
                    longitude = snap.longitude,
                    isFavorite = true,
                )
                prefs.upsertFavorite(loc)
                prefs.setActiveLocationId(id)
                // Update header name if we reverse-geocoded Current Location
                if (name != snap.locationName) {
                    repo.renameFavorite(id, name)
                }
            }
        }
    }

    fun selectFavorite(loc: SavedLocation) {
        viewModelScope.launch {
            runCatching { repo.selectLocation(loc, fetch = true) }
                .onSuccess { refreshSpaceWeather() }
        }
    }

    fun selectGeocode(result: GeocodeResult) {
        viewModelScope.launch {
            runCatching {
                repo.useCoordinates(result.latitude, result.longitude, result.name, saveFavorite = true)
                _searchResults.value = emptyList()
            }.onSuccess { refreshSpaceWeather() }
        }
    }

    /** Reverse-geocode only (map pin label). Does not load weather until confirm. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult =
        repo.reverseGeocode(latitude, longitude)

    fun confirmMapLocation(result: GeocodeResult) {
        viewModelScope.launch {
            runCatching {
                repo.useCoordinates(
                    result.latitude,
                    result.longitude,
                    result.name,
                    saveFavorite = true,
                )
            }.onSuccess { refreshSpaceWeather() }
        }
    }

    fun useDeviceLocation(location: Location, label: String = "Current Location") {
        viewModelScope.launch {
            runCatching {
                repo.useCoordinates(location.latitude, location.longitude, label, saveFavorite = false)
            }.onSuccess { refreshSpaceWeather() }
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch {
            runCatching {
                val remaining = favorites.value.filterNot { it.id == id }
                prefs.removeFavorite(id)
                if (activeLocationId.value == id) {
                    if (remaining.isNotEmpty()) {
                        repo.selectLocation(remaining.first(), fetch = true)
                    } else {
                        prefs.setActiveLocationId(null)
                    }
                }
            }
        }
    }

    fun renameFavorite(id: String, newName: String) {
        viewModelScope.launch {
            runCatching { repo.renameFavorite(id, newName) }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce rapid keystrokes
            delay(350)
            _searching.value = true
            try {
                _searchResults.value = repo.search(query)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _searching.value = false
            }
        }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoUpdateEnabled(enabled)
            WeatherUpdateScheduler.applyFromPrefs(getApplication())
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            prefs.setUpdateIntervalMinutes(minutes)
            WeatherUpdateScheduler.applyFromPrefs(getApplication())
        }
    }

    fun setTitleBarAtBottom(bottom: Boolean) {
        viewModelScope.launch {
            prefs.setTitleBarAtBottom(bottom)
        }
    }

    fun setWidgetShowHighLow(show: Boolean) {
        viewModelScope.launch {
            prefs.setWidgetShowHighLow(show)
            // Refresh widget layout immediately
            com.crome.forecastpoint.widget.WeatherWidgetUpdater.updateAll(getApplication())
        }
    }

    fun setMapSearchAtBottom(bottom: Boolean) {
        viewModelScope.launch {
            prefs.setMapSearchAtBottom(bottom)
        }
    }

    fun setExpandCurrentConditions(expand: Boolean) {
        viewModelScope.launch {
            prefs.setExpandCurrentConditions(expand)
        }
    }

    fun setExpandAdvisories(expand: Boolean) {
        viewModelScope.launch {
            prefs.setExpandAdvisories(expand)
        }
    }

    fun setShowTitleSearch(show: Boolean) {
        viewModelScope.launch { prefs.setShowTitleSearch(show) }
    }

    fun setShowTitleSunMoon(show: Boolean) {
        viewModelScope.launch { prefs.setShowTitleSunMoon(show) }
    }

    fun setHourlyTabConfig(config: List<HourlyTabConfigItem>) {
        viewModelScope.launch { prefs.setHourlyTabConfig(config) }
    }

    fun setDrawerNavOrder(order: List<String>) {
        viewModelScope.launch { prefs.setDrawerNavOrder(order) }
    }

    fun setDrawerNavConfig(config: List<DrawerNavConfigItem>) {
        viewModelScope.launch { prefs.setDrawerNavConfig(config) }
    }

    fun setMapFocusRadiusMiles(miles: Int) {
        viewModelScope.launch { prefs.setMapFocusRadiusMiles(miles) }
    }

    fun setHazardHistoryDays(days: Int) {
        viewModelScope.launch { prefs.setHazardHistoryDays(days) }
    }

    fun setSpaceWeatherWatchThreshold(level: Int) {
        viewModelScope.launch { prefs.setSpaceWeatherWatchThreshold(level) }
    }

    fun setSpaceWeatherActiveThreshold(level: Int) {
        viewModelScope.launch { prefs.setSpaceWeatherActiveThreshold(level) }
    }

    fun setSpaceWeatherForecastHorizonHours(hours: Int) {
        viewModelScope.launch { prefs.setSpaceWeatherForecastHorizonHours(hours) }
    }

    suspend fun searchPlaces(query: String): List<GeocodeResult> = repo.search(query)
}
