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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "forecast_point_prefs")

/** One Hourly screen tab: stable [id] (enum name), [enabled], position = list order. */
@Serializable
data class HourlyTabConfigItem(
    val id: String,
    val enabled: Boolean = true,
)

/** One hamburger-menu row: stable [id], [enabled], position = list order. */
@Serializable
data class DrawerNavConfigItem(
    val id: String,
    val enabled: Boolean = true,
)

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
    /** When true (default), Current Conditions starts open if any watch/warning/advisory is active. */
    private val keyExpandAdvisories = booleanPreferencesKey("expand_advisories")
    private val keyShowTidesTab = booleanPreferencesKey("show_tides_tab")
    private val keyShowSpaceWeather = booleanPreferencesKey("show_space_weather")
    private val keyShowAirQuality = booleanPreferencesKey("show_air_quality")
    private val keyShowVisibility = booleanPreferencesKey("show_visibility")
    private val keyShowPressure = booleanPreferencesKey("show_pressure")
    private val keyShowUvIndex = booleanPreferencesKey("show_uv_index")
    private val keyShowTitleSearch = booleanPreferencesKey("show_title_search")
    private val keyShowTitleSunMoon = booleanPreferencesKey("show_title_sun_moon")
    /** JSON array of HourlyTab names controlling swipe order on the Hourly screen (legacy). */
    private val keyHourlyTabOrder = stringPreferencesKey("hourly_tab_order_json")
    /** JSON array of [HourlyTabConfigItem] — order + enable for every hourly tab. */
    private val keyHourlyTabConfig = stringPreferencesKey("hourly_tab_config_json")
    /** Min NOAA scale (1–5) for title-bar Watch (purple) cue. Default 1 = G1/R1/S1. */
    private val keySwWatchThreshold = intPreferencesKey("sw_watch_threshold")
    /** Min NOAA scale (1–5) for title-bar Active (orange) cue. Default 2 = G2/R2/S2. */
    private val keySwActiveThreshold = intPreferencesKey("sw_active_threshold")
    /** Hours ahead to consider predicted Kp/G for the title-bar cue. */
    private val keySwForecastHorizonHours = intPreferencesKey("sw_forecast_horizon_hours")
    /** Hazard map focus radius (miles) for earthquake / severe weather maps. */
    private val keyMapFocusRadiusMiles = intPreferencesKey("map_focus_radius_miles")
    /** Default look-back window (days) for earthquake / severe weather history lists. */
    private val keyHazardHistoryDays = intPreferencesKey("hazard_history_days")
    /** JSON array of drawer nav item ids (hamburger menu order) — legacy. */
    private val keyDrawerNavOrder = stringPreferencesKey("drawer_nav_order_json")
    /** JSON array of [DrawerNavConfigItem] — order + visibility for hamburger items. */
    private val keyDrawerNavConfig = stringPreferencesKey("drawer_nav_config_json")

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
     * When true, Current Conditions starts expanded on the main screen
     * even when there are no active advisories.
     */
    val expandCurrentConditions: Flow<Boolean> =
        context.dataStore.data.map { it[keyExpandCurrentConditions] ?: false }

    /**
     * When true (default), Current Conditions starts expanded if any
     * watch / warning / advisory is active. Turn off to keep the card
     * collapsed until you tap it (hazard banner still shows when collapsed).
     */
    val expandAdvisories: Flow<Boolean> =
        context.dataStore.data.map { it[keyExpandAdvisories] ?: true }

    /** Title bar: Add City / search icon on Forecast & Hourly. */
    val showTitleSearch: Flow<Boolean> =
        context.dataStore.data.map { it[keyShowTitleSearch] ?: true }

    /** Title bar: Sun/Moon menu icon on Forecast & Hourly. */
    val showTitleSunMoon: Flow<Boolean> =
        context.dataStore.data.map { it[keyShowTitleSunMoon] ?: true }

    /**
     * Default view radius (miles) for earthquake and severe weather summary maps
     * (zoom so about this distance is visible from the selected city).
     */
    val mapFocusRadiusMiles: Flow<Int> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[keyMapFocusRadiusMiles] ?: DEFAULT_MAP_FOCUS_RADIUS_MILES
            MAP_FOCUS_RADIUS_OPTIONS.minByOrNull { kotlin.math.abs(it - raw) }
                ?: DEFAULT_MAP_FOCUS_RADIUS_MILES
        }

    /**
     * Default history look-back (days) for recent earthquakes and tornado reports.
     * Hazard screens can temporarily use another window without changing this.
     */
    val hazardHistoryDays: Flow<Int> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[keyHazardHistoryDays] ?: DEFAULT_HAZARD_HISTORY_DAYS
            HAZARD_HISTORY_DAYS_OPTIONS.minByOrNull { kotlin.math.abs(it - raw) }
                ?: DEFAULT_HAZARD_HISTORY_DAYS
        }

    /**
     * Hamburger menu: order + which items are shown.
     * Migrates from legacy order-only pref when the config key is absent.
     */
    val drawerNavConfig: Flow<List<DrawerNavConfigItem>> =
        context.dataStore.data.map { prefs ->
            val rawConfig = prefs[keyDrawerNavConfig]
            if (!rawConfig.isNullOrBlank()) {
                val parsed = runCatching {
                    json.decodeFromString<List<DrawerNavConfigItem>>(rawConfig)
                }.getOrDefault(emptyList())
                return@map normalizeDrawerNavConfig(parsed)
            }
            val orderRaw = prefs[keyDrawerNavOrder]
            val order = if (orderRaw.isNullOrBlank()) {
                DEFAULT_DRAWER_NAV_ORDER
            } else {
                runCatching { json.decodeFromString<List<String>>(orderRaw) }
                    .getOrDefault(DEFAULT_DRAWER_NAV_ORDER)
            }
            normalizeDrawerNavConfig(
                normalizeDrawerNavOrder(order).map { id ->
                    DrawerNavConfigItem(id = id, enabled = true)
                },
            )
        }

    /**
     * Full Hourly tab configuration (order + which tabs are shown).
     * Migrates from legacy order + per-tab boolean prefs when the new key is absent.
     */
    val hourlyTabConfig: Flow<List<HourlyTabConfigItem>> =
        context.dataStore.data.map { prefs ->
            val rawConfig = prefs[keyHourlyTabConfig]
            if (!rawConfig.isNullOrBlank()) {
                val parsed = runCatching {
                    json.decodeFromString<List<HourlyTabConfigItem>>(rawConfig)
                }.getOrDefault(emptyList())
                return@map normalizeHourlyTabConfig(parsed)
            }
            // Legacy migration path
            val orderRaw = prefs[keyHourlyTabOrder]
            val order = if (orderRaw.isNullOrBlank()) {
                DEFAULT_HOURLY_TAB_ORDER
            } else {
                runCatching { json.decodeFromString<List<String>>(orderRaw) }
                    .getOrDefault(DEFAULT_HOURLY_TAB_ORDER)
            }
            val enabledById = mapOf(
                "Tides" to (prefs[keyShowTidesTab] ?: true),
                "SpaceWeather" to (prefs[keyShowSpaceWeather] ?: true),
                "AirQuality" to (prefs[keyShowAirQuality] ?: true),
                "Visibility" to (prefs[keyShowVisibility] ?: true),
                "Pressure" to (prefs[keyShowPressure] ?: true),
                "UvIndex" to (prefs[keyShowUvIndex] ?: true),
            )
            normalizeHourlyTabConfig(
                normalizeHourlyTabOrder(order).map { id ->
                    HourlyTabConfigItem(id = id, enabled = enabledById[id] ?: true)
                },
            )
        }

    /**
     * Title-bar space-weather Watch threshold (1–5). Default 1 (minor = G1/R1/S1).
     * Active threshold is always stored ≥ watch + 0, but UI keeps active > watch.
     */
    val spaceWeatherWatchThreshold: Flow<Int> =
        context.dataStore.data.map {
            (it[keySwWatchThreshold] ?: DEFAULT_SW_WATCH_THRESHOLD).coerceIn(1, 5)
        }

    /** Title-bar Active threshold (1–5). Default 2 (moderate). */
    val spaceWeatherActiveThreshold: Flow<Int> =
        context.dataStore.data.map {
            (it[keySwActiveThreshold] ?: DEFAULT_SW_ACTIVE_THRESHOLD).coerceIn(1, 5)
        }

    /** Forecast horizon in hours for predicted G in the title-bar cue. */
    val spaceWeatherForecastHorizonHours: Flow<Int> =
        context.dataStore.data.map {
            (it[keySwForecastHorizonHours] ?: DEFAULT_SW_HORIZON_HOURS)
                .let { h -> SW_HORIZON_OPTIONS.minByOrNull { opt -> kotlin.math.abs(opt - h) } ?: DEFAULT_SW_HORIZON_HOURS }
        }

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

    /** Whether an Hourly tab is enabled (drives optional network fetches). */
    suspend fun isHourlyTabEnabled(id: String): Boolean =
        hourlyTabConfig.first().firstOrNull { it.id == id }?.enabled ?: true

    suspend fun getHourlyTabConfigOnce(): List<HourlyTabConfigItem> =
        hourlyTabConfig.first()

    suspend fun setMapSearchAtBottom(bottom: Boolean) {
        context.dataStore.edit { it[keyMapSearchBottom] = bottom }
    }

    suspend fun setExpandCurrentConditions(expand: Boolean) {
        context.dataStore.edit { it[keyExpandCurrentConditions] = expand }
    }

    suspend fun setExpandAdvisories(expand: Boolean) {
        context.dataStore.edit { it[keyExpandAdvisories] = expand }
    }

    suspend fun setShowTitleSearch(show: Boolean) {
        context.dataStore.edit { it[keyShowTitleSearch] = show }
    }

    suspend fun setShowTitleSunMoon(show: Boolean) {
        context.dataStore.edit { it[keyShowTitleSunMoon] = show }
    }

    suspend fun setHourlyTabConfig(config: List<HourlyTabConfigItem>) {
        val normalized = normalizeHourlyTabConfig(config)
        context.dataStore.edit {
            it[keyHourlyTabConfig] = json.encodeToString(normalized)
            // Keep legacy order in sync for older builds / rollback safety
            it[keyHourlyTabOrder] = json.encodeToString(normalized.map { item -> item.id })
        }
    }

    suspend fun setMapFocusRadiusMiles(miles: Int) {
        val chosen = MAP_FOCUS_RADIUS_OPTIONS.minByOrNull { kotlin.math.abs(it - miles) }
            ?: DEFAULT_MAP_FOCUS_RADIUS_MILES
        context.dataStore.edit { it[keyMapFocusRadiusMiles] = chosen }
    }

    suspend fun setHazardHistoryDays(days: Int) {
        val chosen = HAZARD_HISTORY_DAYS_OPTIONS.minByOrNull { kotlin.math.abs(it - days) }
            ?: DEFAULT_HAZARD_HISTORY_DAYS
        context.dataStore.edit { it[keyHazardHistoryDays] = chosen }
    }

    suspend fun setDrawerNavConfig(config: List<DrawerNavConfigItem>) {
        val normalized = normalizeDrawerNavConfig(config)
        context.dataStore.edit {
            it[keyDrawerNavConfig] = json.encodeToString(normalized)
            it[keyDrawerNavOrder] = json.encodeToString(normalized.map { item -> item.id })
        }
    }

    /** Reorder only (preserves enabled flags). Used by the drawer drag gesture. */
    suspend fun setDrawerNavOrder(order: List<String>) {
        val current = drawerNavConfig.first()
        val enabledById = current.associate { it.id to it.enabled }
        val normalized = normalizeDrawerNavOrder(order).map { id ->
            DrawerNavConfigItem(id = id, enabled = enabledById[id] ?: true)
        }
        setDrawerNavConfig(normalized)
    }

    suspend fun setSpaceWeatherWatchThreshold(level: Int) {
        val watch = level.coerceIn(1, 5)
        context.dataStore.edit { prefs ->
            prefs[keySwWatchThreshold] = watch
            val active = (prefs[keySwActiveThreshold] ?: DEFAULT_SW_ACTIVE_THRESHOLD).coerceIn(1, 5)
            // Active must be at least one step above Watch when possible
            if (active <= watch) {
                prefs[keySwActiveThreshold] = (watch + 1).coerceAtMost(5)
            }
        }
    }

    suspend fun setSpaceWeatherActiveThreshold(level: Int) {
        val active = level.coerceIn(1, 5)
        context.dataStore.edit { prefs ->
            prefs[keySwActiveThreshold] = active
            val watch = (prefs[keySwWatchThreshold] ?: DEFAULT_SW_WATCH_THRESHOLD).coerceIn(1, 5)
            if (watch >= active) {
                prefs[keySwWatchThreshold] = (active - 1).coerceAtLeast(1)
            }
        }
    }

    suspend fun setSpaceWeatherForecastHorizonHours(hours: Int) {
        val chosen = SW_HORIZON_OPTIONS.minByOrNull { kotlin.math.abs(it - hours) }
            ?: DEFAULT_SW_HORIZON_HOURS
        context.dataStore.edit { it[keySwForecastHorizonHours] = chosen }
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

        const val DEFAULT_SW_WATCH_THRESHOLD = 1
        const val DEFAULT_SW_ACTIVE_THRESHOLD = 2
        const val DEFAULT_SW_HORIZON_HOURS = 48
        const val DEFAULT_MAP_FOCUS_RADIUS_MILES = 250
        /** Hazard map focus radius options (miles from selected city). */
        val MAP_FOCUS_RADIUS_OPTIONS = listOf(
            50, 100, 150, 250, 400, 500, 750, 1000, 1500, 2000, 3000, 4000,
        )
        /** Quick chips for ad-hoc explore radius on hazard screens (not Settings). */
        val MAP_FOCUS_RADIUS_CHIPS = listOf(100, 250, 500, 750, 1000, 2000, 4000)
        /** Default look-back for recent quakes / severe-weather reports. */
        const val DEFAULT_HAZARD_HISTORY_DAYS = 7
        /**
         * Stock history presets (days) for Settings + hazard-screen chips.
         * Longer ranges use the on-screen **Custom** date picker (keeps default
         * query sizes small).
         */
        val HAZARD_HISTORY_DAYS_OPTIONS = listOf(
            1,    // 1 day
            7,    // 7 days
            30,   // 30 days
            90,   // 3 months
            180,  // 6 months
        )
        /** Same stock chips on hazard screens (plus a separate Custom pill). */
        val HAZARD_HISTORY_DAYS_CHIPS = HAZARD_HISTORY_DAYS_OPTIONS

        /** Human label for a history-window day count. */
        fun historyDaysLabel(days: Int): String = when {
            days <= 1 -> "1 day"
            days == 7 -> "7 days"
            days == 30 -> "30 days"
            days in 85..95 -> "3 months"
            days in 170..190 -> "6 months"
            days < 180 -> "$days days"
            days % 30 == 0 -> "${days / 30} months"
            else -> "$days days"
        }

        fun historyDaysChipLabel(days: Int): String = when {
            days <= 1 -> "1d"
            days == 7 -> "7d"
            days == 30 -> "30d"
            days in 85..95 -> "3m"
            days in 170..190 -> "6m"
            days < 180 -> "${days}d"
            else -> "${days}d"
        }
        /** Look-ahead options for predicted geomagnetic activity. */
        val SW_HORIZON_OPTIONS = listOf(24, 48, 72)
        /** Selectable NOAA scale levels for Watch / Active cues. */
        val SW_SCALE_OPTIONS = listOf(1, 2, 3, 4, 5)

        /**
         * Default Hourly tab order (must match [com.crome.forecastpoint.ui.screens.HourlyTab] names).
         */
        /**
         * Default Hourly tab order, grouped by data source for Settings UX:
         * NWS → NOAA CO-OPS tides → Open-Meteo weather/AQ → SWPC.
         */
        val DEFAULT_HOURLY_TAB_ORDER = listOf(
            "Temperature",
            "Precipitation",
            "Wind",
            "Conditions",
            "Tides",
            "Visibility",
            "Pressure",
            "UvIndex",
            "AirQuality",
            "SpaceWeather",
        )

        fun normalizeHourlyTabOrder(order: List<String>): List<String> {
            val known = DEFAULT_HOURLY_TAB_ORDER.toSet()
            val seen = LinkedHashSet<String>()
            order.forEach { id -> if (id in known) seen.add(id) }
            DEFAULT_HOURLY_TAB_ORDER.forEach { id -> if (id !in seen) seen.add(id) }
            return seen.toList()
        }

        fun defaultHourlyTabConfig(): List<HourlyTabConfigItem> =
            DEFAULT_HOURLY_TAB_ORDER.map { HourlyTabConfigItem(id = it, enabled = true) }

        fun normalizeHourlyTabConfig(config: List<HourlyTabConfigItem>): List<HourlyTabConfigItem> {
            val known = DEFAULT_HOURLY_TAB_ORDER.toSet()
            val enabledById = LinkedHashMap<String, Boolean>()
            val order = ArrayList<String>()
            config.forEach { item ->
                if (item.id in known && item.id !in enabledById) {
                    enabledById[item.id] = item.enabled
                    order.add(item.id)
                }
            }
            DEFAULT_HOURLY_TAB_ORDER.forEach { id ->
                if (id !in enabledById) {
                    enabledById[id] = true
                    order.add(id)
                }
            }
            return order.map { id -> HourlyTabConfigItem(id = id, enabled = enabledById[id] == true) }
        }

        fun hourlyTabDisplayName(id: String): String = when (id) {
            "Temperature" -> "Temperature"
            "Precipitation" -> "Precipitation"
            "Wind" -> "Wind"
            "Tides" -> "Tides / water level"
            "Conditions" -> "Conditions"
            "AirQuality" -> "Air quality"
            "Visibility" -> "Visibility"
            "Pressure" -> "Pressure"
            "UvIndex" -> "UV index"
            "SpaceWeather" -> "Space weather"
            else -> id
        }

        /** Default hamburger menu order (Settings / About stay fixed at the bottom). */
        val DEFAULT_DRAWER_NAV_ORDER = listOf(
            "Forecast",
            "CurrentLocation",
            "Map",
            "Sun",
            "Moon",
            "SpaceWeather",
            "Earthquakes",
            "Storms",
            "AddCity",
        )

        fun normalizeDrawerNavOrder(order: List<String>): List<String> {
            val known = DEFAULT_DRAWER_NAV_ORDER.toSet()
            val seen = LinkedHashSet<String>()
            order.forEach { id -> if (id in known) seen.add(id) }
            DEFAULT_DRAWER_NAV_ORDER.forEach { id -> if (id !in seen) seen.add(id) }
            return seen.toList()
        }

        fun defaultDrawerNavConfig(): List<DrawerNavConfigItem> =
            DEFAULT_DRAWER_NAV_ORDER.map { DrawerNavConfigItem(id = it, enabled = true) }

        fun normalizeDrawerNavConfig(config: List<DrawerNavConfigItem>): List<DrawerNavConfigItem> {
            val known = DEFAULT_DRAWER_NAV_ORDER.toSet()
            val enabledById = LinkedHashMap<String, Boolean>()
            val order = ArrayList<String>()
            config.forEach { item ->
                if (item.id in known && item.id !in enabledById) {
                    enabledById[item.id] = item.enabled
                    order.add(item.id)
                }
            }
            DEFAULT_DRAWER_NAV_ORDER.forEach { id ->
                if (id !in enabledById) {
                    enabledById[id] = true
                    order.add(id)
                }
            }
            // Always keep Forecast available
            if (enabledById["Forecast"] == false) enabledById["Forecast"] = true
            // At least one item enabled
            if (enabledById.values.none { it }) {
                enabledById["Forecast"] = true
            }
            return order.map { id -> DrawerNavConfigItem(id = id, enabled = enabledById[id] == true) }
        }

        fun drawerNavDisplayName(id: String): String = when (id) {
            "Forecast" -> "Forecast"
            "CurrentLocation" -> "Current Location"
            "Map" -> "Map"
            "Sun" -> "Sun"
            "Moon" -> "Moon"
            "SpaceWeather" -> "Space Weather"
            "Earthquakes" -> "Earthquakes"
            "Storms" -> "Severe Weather"
            "AddCity" -> "Add City"
            else -> id
        }
    }
}
