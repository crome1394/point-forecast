package com.crome.forecastpoint.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "forecast_point_prefs")

class PreferencesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val keyAutoUpdate = booleanPreferencesKey("auto_update_enabled")
    private val keyIntervalMin = intPreferencesKey("update_interval_minutes")
    private val keyActiveId = stringPreferencesKey("active_location_id")
    private val keyFavorites = stringPreferencesKey("favorites_json")
    private val keyLastSnapshot = stringPreferencesKey("last_snapshot_json")
    private val keyUseFahrenheit = booleanPreferencesKey("use_fahrenheit")
    private val keyTransientLoc = stringPreferencesKey("transient_location_json")
    private val keyTitleBarBottom = booleanPreferencesKey("title_bar_bottom")
    private val keyWidgetShowHighLow = booleanPreferencesKey("widget_show_high_low")
    private val keyMapSearchBottom = booleanPreferencesKey("map_search_at_bottom")
    private val keyExpandCurrentConditions = booleanPreferencesKey("expand_current_conditions")
    private val keyShowTidesTab = booleanPreferencesKey("show_tides_tab")

    val autoUpdateEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[keyAutoUpdate] ?: true }

    val updateIntervalMinutes: Flow<Int> =
        context.dataStore.data.map { it[keyIntervalMin] ?: DEFAULT_INTERVAL_MIN }

    val useFahrenheit: Flow<Boolean> =
        context.dataStore.data.map { it[keyUseFahrenheit] ?: true }

    /** When true, main title/actions bar is docked at the bottom of the screen. */
    val titleBarAtBottom: Flow<Boolean> =
        context.dataStore.data.map { it[keyTitleBarBottom] ?: false }

    /**
     * Widget forecast cells: true = day high/low (↑/↓), false = single period temp
     * (matches classic NWS period cards: "Tue AM 83°").
     */
    val widgetShowHighLow: Flow<Boolean> =
        context.dataStore.data.map { it[keyWidgetShowHighLow] ?: false }

    /** When true, map city search field is docked at the bottom. */
    val mapSearchAtBottom: Flow<Boolean> =
        context.dataStore.data.map { it[keyMapSearchBottom] ?: false }

    /**
     * When true, Current Conditions starts expanded on the main screen.
     * Active hazards still force-expand regardless of this setting.
     */
    val expandCurrentConditions: Flow<Boolean> =
        context.dataStore.data.map { it[keyExpandCurrentConditions] ?: false }

    /**
     * When true (default), the Hourly screen includes the Tides tab.
     * Inland users can turn this off.
     */
    val showTidesTab: Flow<Boolean> =
        context.dataStore.data.map { it[keyShowTidesTab] ?: true }

    val favorites: Flow<List<SavedLocation>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[keyFavorites] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<SavedLocation>>(raw) }.getOrDefault(emptyList())
        }

    val activeLocationId: Flow<String?> =
        context.dataStore.data.map { it[keyActiveId] }

    val lastSnapshot: Flow<WeatherSnapshot?> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[keyLastSnapshot] ?: return@map null
            runCatching { json.decodeFromString<WeatherSnapshot>(raw) }.getOrNull()
        }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyAutoUpdate] = enabled }
    }

    suspend fun setUpdateIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[keyIntervalMin] = minutes.coerceIn(15, 24 * 60) }
    }

    suspend fun setUseFahrenheit(use: Boolean) {
        context.dataStore.edit { it[keyUseFahrenheit] = use }
    }

    suspend fun setTitleBarAtBottom(bottom: Boolean) {
        context.dataStore.edit { it[keyTitleBarBottom] = bottom }
    }

    suspend fun setWidgetShowHighLow(show: Boolean) {
        context.dataStore.edit { it[keyWidgetShowHighLow] = show }
    }

    suspend fun getWidgetShowHighLowOnce(): Boolean =
        widgetShowHighLow.first()

    suspend fun setMapSearchAtBottom(bottom: Boolean) {
        context.dataStore.edit { it[keyMapSearchBottom] = bottom }
    }

    suspend fun setExpandCurrentConditions(expand: Boolean) {
        context.dataStore.edit { it[keyExpandCurrentConditions] = expand }
    }

    suspend fun setShowTidesTab(show: Boolean) {
        context.dataStore.edit { it[keyShowTidesTab] = show }
    }

    suspend fun setActiveLocationId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(keyActiveId) else it[keyActiveId] = id
        }
    }

    suspend fun saveFavorites(list: List<SavedLocation>) {
        context.dataStore.edit { it[keyFavorites] = json.encodeToString(list) }
    }

    suspend fun upsertFavorite(loc: SavedLocation) {
        val current = favorites.first().toMutableList()
        val idx = current.indexOfFirst { it.id == loc.id }
        if (idx >= 0) current[idx] = loc else current.add(loc)
        saveFavorites(current)
    }

    suspend fun removeFavorite(id: String) {
        saveFavorites(favorites.first().filterNot { it.id == id })
    }

    /** Rename a saved city; returns the updated location or null if not found. */
    suspend fun renameFavorite(id: String, newName: String): SavedLocation? {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return null
        val current = favorites.first().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = current[idx].copy(name = trimmed)
        current[idx] = updated
        saveFavorites(current)
        // Keep transient copy in sync if it was the active non-favorite view of same id
        getTransientLocation()?.takeIf { it.id == id }?.let {
            setLastTransientLocation(updated)
        }
        return updated
    }

    suspend fun setLastTransientLocation(loc: SavedLocation) {
        context.dataStore.edit { it[keyTransientLoc] = json.encodeToString(loc) }
    }

    suspend fun getTransientLocation(): SavedLocation? {
        val raw = context.dataStore.data.first()[keyTransientLoc] ?: return null
        return runCatching { json.decodeFromString<SavedLocation>(raw) }.getOrNull()
    }

    suspend fun saveSnapshot(snapshot: WeatherSnapshot) {
        context.dataStore.edit { it[keyLastSnapshot] = json.encodeToString(snapshot) }
    }

    suspend fun getSnapshotOnce(): WeatherSnapshot? = lastSnapshot.first()

    suspend fun getAutoUpdateOnce(): Boolean = autoUpdateEnabled.first()

    suspend fun getIntervalOnce(): Int = updateIntervalMinutes.first()

    suspend fun getActiveLocationOnce(): SavedLocation? {
        val id = activeLocationId.first()
        val favs = favorites.first()
        favs.firstOrNull { it.id == id }?.let { return it }
        getTransientLocation()?.takeIf { it.id == id }?.let { return it }
        return favs.firstOrNull() ?: getTransientLocation()
    }

    companion object {
        const val DEFAULT_INTERVAL_MIN = 30
        val INTERVAL_OPTIONS = listOf(15, 30, 60, 120, 180, 360, 720)
    }
}
