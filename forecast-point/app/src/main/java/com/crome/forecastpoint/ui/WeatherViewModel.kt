package com.crome.forecastpoint.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crome.forecastpoint.data.GeocodeResult
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.data.SavedLocation
import com.crome.forecastpoint.data.SpaceWeatherService
import com.crome.forecastpoint.data.WeatherRepository
import com.crome.forecastpoint.data.WeatherSnapshot
import com.crome.forecastpoint.worker.WeatherUpdateScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = WeatherRepository(app.applicationContext)
    private val prefs = repo.preferences
    private val spaceWeatherService = SpaceWeatherService()

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
    val showTidesTab = prefs.showTidesTab.stateIn(viewModelScope, share, true)
    val showSpaceWeather = prefs.showSpaceWeather.stateIn(viewModelScope, share, true)

    private val _spaceWeather = MutableStateFlow<SpaceWeatherService.Snapshot?>(null)
    val spaceWeather: StateFlow<SpaceWeatherService.Snapshot?> = _spaceWeather.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodeResult>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repo.bootstrap()
                if (prefs.getActiveLocationOnce() != null) {
                    repo.refreshActive(manual = false)
                }
                WeatherUpdateScheduler.applyFromPrefs(getApplication())
            }
        }
        // Load SWPC data when the user wants the Space Weather tab
        viewModelScope.launch {
            prefs.showSpaceWeather.collectLatest { enabled ->
                if (enabled) {
                    refreshSpaceWeather()
                } else {
                    _spaceWeather.value = null
                }
            }
        }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            runCatching { repo.refreshActive(manual = true) }
            if (showSpaceWeather.value) {
                refreshSpaceWeather()
            }
        }
    }

    private suspend fun refreshSpaceWeather() {
        runCatching {
            _spaceWeather.value = spaceWeatherService.fetch()
        }
    }

    fun selectFavorite(loc: SavedLocation) {
        viewModelScope.launch {
            runCatching { repo.selectLocation(loc, fetch = true) }
        }
    }

    fun selectGeocode(result: GeocodeResult) {
        viewModelScope.launch {
            runCatching {
                repo.useCoordinates(result.latitude, result.longitude, result.name, saveFavorite = true)
                _searchResults.value = emptyList()
            }
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
            }
        }
    }

    fun useDeviceLocation(location: Location, label: String = "Current Location") {
        viewModelScope.launch {
            runCatching {
                repo.useCoordinates(location.latitude, location.longitude, label, saveFavorite = false)
            }
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

    fun setShowTidesTab(show: Boolean) {
        viewModelScope.launch {
            prefs.setShowTidesTab(show)
        }
    }

    fun setShowSpaceWeather(show: Boolean) {
        viewModelScope.launch {
            prefs.setShowSpaceWeather(show)
        }
    }

    suspend fun searchPlaces(query: String): List<GeocodeResult> = repo.search(query)
}
