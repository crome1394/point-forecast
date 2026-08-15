# Changelog

All notable changes to **Point Forecast** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- Map single-finger pan no longer opens the hamburger drawer (drawer edge-swipe disabled off the main Forecast screen; map owns pan gestures).
- Tornado / Hurricane **active tropical cyclones** list (and map markers) limited to **Map focus radius** (same setting as zoom / tornado reports; default 250 mi).

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
