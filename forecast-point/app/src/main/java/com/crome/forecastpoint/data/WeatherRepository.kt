package com.crome.forecastpoint.data

import android.content.Context
import com.crome.forecastpoint.widget.WeatherWidgetUpdater
import com.crome.forecastpoint.worker.WeatherUpdateScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class WeatherRepository(
    private val context: Context,
    private val api: NwsApi = NwsApi(
        tideService = TideService(context.applicationContext),
    ),
    private val prefs: PreferencesRepository = PreferencesRepository(context),
) {
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Prevents overlapping network refreshes (widget + UI + worker). */
    private val refreshMutex = Mutex()

    val preferences get() = prefs

    suspend fun bootstrap() {
        // Drop stale non-alert products (e.g. Hazardous Weather Outlook) from cache
        // so a false banner does not linger until the next network refresh.
        _snapshot.value = prefs.getSnapshotOnce()?.let { sanitizeHazards(it) }
        WeatherUpdateScheduler.reconcile(context)
    }

    /** Remove routine outlook products that must not be shown as active hazards. */
    private fun sanitizeHazards(snap: WeatherSnapshot): WeatherSnapshot {
        if (snap.hazards.isEmpty()) return snap
        val cleaned = snap.hazards.filterNot { isNonAlertProduct(it.event) }
        if (cleaned.size == snap.hazards.size) return snap
        return snap.copy(hazards = cleaned)
    }

    private fun isNonAlertProduct(event: String): Boolean {
        val e = event.trim().lowercase()
        if (e.isEmpty() || e == "null" || e == "none") return true
        return e.contains("hazardous weather outlook") ||
            e.contains("hydrologic outlook") ||
            e.contains("weather outlook") ||
            e == "hwo" ||
            e.contains("area forecast discussion") ||
            e.contains("public information statement")
    }

    suspend fun refreshActive(manual: Boolean = false): WeatherSnapshot? {
        val loc = prefs.getActiveLocationOnce()
        if (loc == null) {
            _error.value = "No location selected. Search for a city or use Current Location."
            return null
        }
        return refresh(loc.latitude, loc.longitude, loc.name, manual)
    }

    suspend fun refresh(
        latitude: Double,
        longitude: Double,
        name: String?,
        manual: Boolean = false,
    ): WeatherSnapshot? = refreshMutex.withLock {
        // Skip redundant background refresh if data is still fresh (< 2 min)
        if (!manual) {
            val current = _snapshot.value
            if (current != null &&
                current.latitude == latitude &&
                current.longitude == longitude &&
                System.currentTimeMillis() - current.updatedAtEpochMs < STALE_MS
            ) {
                return current
            }
        }

        _loading.value = true
        _error.value = null
        return try {
            val snap = api.fetchWeather(latitude, longitude, name)
            _snapshot.value = snap
            prefs.saveSnapshot(snap)
            WeatherWidgetUpdater.updateAll(context, snap)
            snap
        } catch (e: Exception) {
            val friendly = friendlyWeatherError(e)
            _error.value = friendly
            // Out-of-coverage for a *new* point: do not keep showing the previous city.
            val previous = _snapshot.value
            val differentPlace = previous == null ||
                previous.latitude != latitude ||
                previous.longitude != longitude
            if (differentPlace && isOutOfCoverageError(e)) {
                _snapshot.value = null
            }
            // Transient failures: keep last good snapshot when same place (or network blip).
            null
        } finally {
            _loading.value = false
        }
    }

    private fun isOutOfCoverageError(e: Throwable): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("HTTP 400") || msg.contains("HTTP 404")
    }

    /** User-facing text instead of raw "HTTP 400 for https://…" URLs. */
    private fun friendlyWeatherError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("HTTP 400") || msg.contains("HTTP 404") ->
                "This location is outside the U.S. National Weather Service coverage area. " +
                    "Choose a place in the United States or territories for a forecast."
            msg.contains("HTTP 403") ->
                "The weather service temporarily refused the request. Try again in a moment."
            msg.contains("HTTP 5") ->
                "The weather service is temporarily unavailable. Try again later."
            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Unable to resolve", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("UnknownHost", ignoreCase = true) ->
                "Could not reach weather services. Check your connection and try again."
            msg.startsWith("HTTP ") ->
                "Could not load a forecast for this location."
            msg.isBlank() -> "Failed to fetch weather"
            else -> msg
        }
    }

    suspend fun selectLocation(loc: SavedLocation, fetch: Boolean = true, saveFavorite: Boolean = true) {
        if (saveFavorite) {
            prefs.upsertFavorite(loc.copy(isFavorite = true))
        }
        prefs.setActiveLocationId(loc.id)
        if (!saveFavorite) {
            prefs.setLastTransientLocation(loc)
        }
        if (fetch) refresh(loc.latitude, loc.longitude, loc.name, manual = true)
    }

    suspend fun useCoordinates(
        latitude: Double,
        longitude: Double,
        name: String,
        saveFavorite: Boolean = true,
    ) {
        val loc = SavedLocation(
            id = UUID.nameUUIDFromBytes("$latitude,$longitude".toByteArray()).toString(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            isFavorite = saveFavorite,
        )
        selectLocation(loc, fetch = true, saveFavorite = saveFavorite)
    }

    suspend fun search(query: String): List<GeocodeResult> = api.geocode(query)

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodeResult =
        api.reverseGeocode(latitude, longitude)

    /**
     * Rename a saved city. If it is the active location, update the visible snapshot
     * name without re-fetching weather.
     */
    suspend fun renameFavorite(id: String, newName: String): Boolean {
        val updated = prefs.renameFavorite(id, newName) ?: return false
        val snap = _snapshot.value
        if (snap != null) {
            val activeId = prefs.getActiveLocationOnce()?.id
            val samePlace = snap.latitude == updated.latitude &&
                snap.longitude == updated.longitude
            if (activeId == id || samePlace) {
                val renamed = snap.copy(locationName = updated.name)
                _snapshot.value = renamed
                prefs.saveSnapshot(renamed)
                WeatherWidgetUpdater.updateAll(context, renamed)
            }
        }
        return true
    }

    companion object {
        private const val STALE_MS = 2 * 60 * 1000L
    }
}
