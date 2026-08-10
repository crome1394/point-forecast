# Changelog

All notable changes to **Forecast Point** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.2] — 2026-08-09

### Fixed

- False hazard banner: MapClick’s routine **Hazardous Weather Outlook** was shown as an active alert when `api.weather.gov` correctly reported none. Prefer CAP active alerts; only fall back to MapClick on API failure, and filter non-alert products (HWO, hydrologic outlook, etc.). Cached snapshots are sanitized on startup so a stale HWO banner does not linger.

## [1.0.1] — 2026-08-07

### Fixed

- Crash when loading a city after midnight: NWS returns both **Overnight** and the weekday (e.g. Friday) for the same date; duplicate list keys crashed Compose. Overnight/Tonight stay distinct labels and day cards use unique keys.
- Map search (spotlight FAB): search bar now reliably appears and defaults to the **top** of the map; use Settings → “Map search at bottom” only if you prefer bottom placement
- First-run empty state: **Open Map** shortcut when no city is selected yet

### Added

- Settings → **Expand Current Conditions**: choose whether Current Conditions details start expanded or collapsed when you open the app (active hazards still force-expand)

### Documentation

- README: GitHub Release download section and APK naming guidance

## [1.0.0] — 2026-08-06

Initial public release under the name **Forecast Point**
(`com.crome.forecastpoint`).

### Added

- NWS point forecast and current conditions UI (dark theme)
- Expandable current conditions; hazards/alerts when active
- Multi-day forecast cards and hourly tables (temperature color scale, precip, wind, tides, conditions)
- Horizontal swipe between hourly tabs
- Home-screen widget with NWS picture icons, resizable layout, period temp or high/low mode
- City search (Nominatim) with proper place naming
- Map location picker (osmdroid, light basemap), search FAB, pin + confirm chip
- GPS center on map open (with permission)
- Radar deep-link centered on active location
- Favorites: add, rename, remove
- Settings: auto-update, intervals, title bar position, widget high/low, map search position
- System back gesture returns from nested screens to main forecast
- Bundled NWS forecast icons for CalyxOS / reliable rendering

### Credits

- Weather data: U.S. National Weather Service / NOAA  
- Map & geocoding: OpenStreetMap, Nominatim, CARTO, osmdroid  
- UX inspiration: commercial “NOAA Weather & Tides” (Pandamonium Software)—independent reimplementation  

### Notes

- Renamed from internal working title “NOAA Forecast” to **Forecast Point** to
  distinguish this community project from official NOAA products and the
  commercial Play Store app.
