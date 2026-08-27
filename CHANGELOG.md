# Changelog

All notable changes to **Point Forecast** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.9] — 2026-08-27

### Changed

- **F-Droid signing:** releases are now **developer-signed** (reproducible upstream builds). If you installed an earlier build from F-Droid, **uninstall once and reinstall** from F-Droid (or GitHub) — Android will not update across different signing keys. After that, F-Droid and GitHub APKs share the same signature.
- Release tooling for reproducible upstream-signed builds (Build Tools 34 `apksigner`, `app-release-signed.apk` contract)

## [1.1.8] — 2026-08-27

### Fixed

- Add City search honors **Search bar at bottom** (same Settings toggle as Map) and lifts above the keyboard
- Hazard loading banner uses two lines (title + muted note) for long history updates
- Clarify tropical cyclone distance is to the **current** NHC center (not closest historical approach)
- F-Droid rebuild of the full 1.1.7 tip (store icon + OSM map tiles); prior F-Droid 1.1.7 was an earlier prep commit

## [1.1.7] — 2026-08-27

### Fixed

- Hourly table uses **station / forecast-location** timezone (same as forecast card and Sun/Moon), not the device zone
- Historical severe-weather custom ranges use SPC WCM archive (not daily CSVs that only cover ~last month)
- Widget: do not allow resize below declared minimum size (avoids clipped sunset/period strip on tiny cells)
- Map tiles: switch from CARTO raster CDN (now requires API key / watermarks) to OpenStreetMap standard tiles — no key needed

### Added

- Severe Weather: one combined reports list (Alert / Tropical / Tornado) with clear type badges
- Reverse-geocode of WCM tornado coordinates to nearby place names (Nominatim, rate-limited)
- Hazard explore distance chips: **50m / 100m / 250m / 500m / Custom** (miles); custom field clears on focus
- Custom history date picker opens with a blank selection
- City dropdown on Severe Weather and Earthquakes settings (defaults to forecast city; picks from favorites)

### Changed

- History window header layout so long custom date badges do not crush help text

## [1.1.6] — 2026-08-16

### Fixed

- Space weather (SWPC) is not fetched on cold start even when a city is saved; only when Space Weather or Hourly is opened
- Optional Hourly tabs skip their network calls when disabled (tides/CO-OPS, Open-Meteo weather, air quality)
- Home screen widget: scaling icons, labels under icons, centered high/low with arrows after temps, even icon sizes

### Changed

- Settings Hourly tabs color-coded by data source (NWS / CO-OPS / Open-Meteo / SWPC) with legend
- Default Hourly tab order grouped by data source (NWS → tides → Open-Meteo → SWPC)
- About credit: Javier V.

## [1.1.5] — 2026-08-16

### Fixed

- Space weather (SWPC) is not fetched on cold start even when a city is saved; only when Space Weather or Hourly is opened (or after it was already loaded this session)
- Home screen widget: restore scaling period icons (1.1.4-style), high/low arrows after temperatures; About credit shortened to Javier V.

## [1.1.4] — 2026-08-15

### Fixed

- Sun/Moon times use the **forecast location** timezone (NWS zone), not the device timezone
- Clearer message when a point is outside NWS coverage (no raw HTTP error URL); drop previous city forecast for out-of-coverage picks
- Space weather (SWPC) is not fetched on empty cold start; loads after a location is set or when Space Weather / Hourly is opened

## [1.1.3] — 2026-08-15

### Added

- Hazard screens: collapsible **Settings** under the map (explore radius, history, filters) with **Reset** and **Collapse** footers
- Hazard **explore distance** (temporary; does not change Settings default) up to **4000 mi**
- Hazard **history window**: stock **1d / 7d / 30d / 3m / 6m** plus **Custom** calendar date-range picker
- Settings **map focus radius** options through **4000 miles**; **hazard history** default (1d–6m)
- Earthquake screen: **minimum magnitude** slider; single **Earthquake reports** list (newest first)
- Severe Weather screen: **tropical strength** and **tornado EF** sliders; **Severe weather reports** with tap-through to SPC daily pages
- Loading banners while hazard APIs refresh
- Classic **push-pin** map markers (red = city, green = reports, cyan = tropical storms)
- About screen thanks to **Javier V.** for UX ideas, troubleshooting, and QA
- Plain-language **About** legends on earthquake and severe weather screens (acronyms expanded)

### Changed

- Renamed **Tornado / Hurricane** to **Severe Weather** (drawer, title bar, menus)
- Long severe-weather history uses SPC **WCM 1950–present** archive (yearly files from 2008+) when the window exceeds 30 days
- History chips auto-scroll so the selected pill stays visible

### Fixed

- Long history windows no longer stuck on only the newest N events (time-spanning selection + adaptive catalog min magnitude for USGS)

## [1.1.2] — 2026-08-14

### Fixed

- Tornado / Hurricane **active tropical cyclones** list (and map markers) limited to **Map focus radius** (same setting as zoom / tornado reports; default 250 mi).
- Map single-finger pan no longer opens the hamburger drawer (drawer edge-swipe disabled off the main Forecast screen; map owns pan gestures).

### Added

- **Celestial / hazards hub** from the title-bar sun/moon control and the drawer:
  - **Sun** and **Moon** summaries (day strip, altitude chart with axis labels, rise/set, twilight / phase)
  - **Space weather** summary (NOAA SWPC G/R/S scales, Kp chart, about section)
  - **Earthquakes** summary (USGS FDSN; map + recent M1+/M2.5+ and historical M4+ tables)
  - **Tornado / Hurricane** summary (NHC active storms, SPC tornado reports, NWS local alerts)
- **Title-bar body tabs** when viewing celestial screens (switch Sun / Moon / Space Wx / Earthquakes / Storms without reopening the menu)
- **Space weather title-bar cue** (icon tint + bolt when conditions are elevated; not a system notification)
- Settings for space weather cue: Watch threshold, Active threshold, forecast look-ahead
- **Reorderable hamburger menu** (long-press drag); Settings to show/hide menu items
- **Hourly tab order + enable** unified in Settings (all tabs, including Temperature / Conditions)
- **Pull-to-refresh** on the main Forecast screen
- Favorite **star** toggles save/remove of the active location (including Current Location, with reverse-geocode name)
- Map **focus radius** setting (default 250 mi) for earthquake and tornado/hurricane maps, history lists, and nearby active tropical cyclones
- Full-screen expand control on hazard maps; one-finger pan in scrollable screens
- Light basemap on hazard maps for readability

### Changed

- **Tides / water levels** for more locations:
  - NOAA CO-OPS tide predictions (coastal)
  - NOAA subordinate/offset stations (e.g. Sacramento, Clarksburg) via high/low + interpolation or reference offsets
  - Great Lakes water levels (LWD / OFS)
  - USGS NWIS gage height for rivers and inland lakes
- Settings reorganized into categories (Title bar, Main screen, Map, Hamburger menu, Hourly tabs, Space weather cue, Widget, Updates)
- Advisories expand behavior is optional (Settings → Expand advisories & alerts)
- Title bar search and sun/moon icons can be hidden independently

### Fixed

- Subordinate NOAA tide stations returning no hourly series (now use hilo + offsets)
- Drawer drag-reorder cancelling mid-gesture
- Earthquake lists clearer vs other apps (M2.5+ vs M1+, distance order, focus radius on history)

## [1.0.3] — 2026-08-14

Publish-ready build: display name **Point Forecast**, hourly extras, map/search polish, and bug fixes since 1.0.2.

### Changed

- App display name: **Forecast Point** → **Point Forecast** (launcher, widget, About, docs). Package ID `com.crome.forecastpoint` unchanged (in-place upgrades).
- Map and city search prefer **city + state** (e.g. Columbus OH, DeKalb IL), not street or business results.
- Title bar setting label clarifies that menu, search, radar, and refresh move top ↔ bottom together.
- Documentation streamlined for GitHub / F-Droid publishing.

### Added

- App bar **radar** icon (next to Refresh) opens National Weather Radar for the active location.
- Settings → **Show tides in hourly** (default on).
- Settings → **Show space weather** (default on): Hourly **SPACE WX** tab — NOAA SWPC planetary Kp and G-scale (global).
- Hourly tabs with Settings toggles (default on): **Air quality** (US AQI / PM2.5), **Visibility**, **Pressure**, **UV index**.
- Map **blue GPS pin** for live position (updates as you move); **red pin** for the weather pick.
- Map bottom search lifts **above the keyboard** (IME padding); suggestions sit above the field.

### Fixed

- Hourly times misaligned with weather.gov digital table (bad digitalJSON labels/unix); hours anchored to MapClick `startValidTime`.
- Bottom map search field covered by the soft keyboard.

## [1.0.2] — 2026-08-09

### Fixed

- False hazard banner: MapClick **Hazardous Weather Outlook** treated as an active alert when CAP reported none. Prefer CAP active alerts; filter non-alert products; sanitize cached snapshots.

## [1.0.1] — 2026-08-07

### Fixed

- Crash when forecast starts with Overnight and same-day weekday (duplicate LazyColumn keys).
- Map search defaults to top; optional bottom placement in Settings.
- First-run empty state: Open Map shortcut.

### Added

- Settings → Expand Current Conditions on open.

## [1.0.0] — 2026-08-06

Initial public release (`com.crome.forecastpoint`).

### Added

- NWS point forecast and current conditions (dark theme)
- Expandable current conditions; hazards when active
- Multi-day and hourly tables; widget with bundled NWS icons
- City search, map picker, GPS, radar link, favorites
- Settings: auto-update, title bar, widget high/low, map search position

### Credits

- Weather: U.S. National Weather Service / NOAA  
- Map & geocoding: OpenStreetMap, Nominatim, CARTO, osmdroid  
- UX inspiration: commercial “NOAA Weather & Tides” (independent reimplementation)

### Notes

- Independent community app; not an official NOAA product.
