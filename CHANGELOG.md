# Changelog

All notable changes to **Point Forecast** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Title bar **sun/moon** control (icon follows day/night at the active location). Opens a pill menu for **Sun** or **Moon**, then a summary screen with multi-day strip, altitude path chart, rise/set times, civil twilight / solar noon (sun), phase & illumination (moon), and short explainers.

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
