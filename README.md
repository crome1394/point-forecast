# Point Forecast

U.S. National Weather Service **point forecasts** on Android—clear, offline-friendly, and FOSS.

**Not affiliated with NOAA, NWS, or any commercial weather app.** Uses public APIs only.

| | |
|---|---|
| **App** | Point Forecast |
| **ID** | `com.crome.forecastpoint` |
| **Android** | 8.0+ (API 26) |
| **License** | [MIT](LICENSE) |
| **Code** | [`forecast-point/`](forecast-point/) |

## What it does

- **Current conditions** and multi-day NWS forecast (pull-to-refresh on the main screen)
- **Hourly** tables with configurable order and visibility: temperature, precip, wind, conditions, tides / water level, air quality, visibility, pressure, UV, space weather
- **Alerts** when watches/warnings/advisories are active (optional auto-expand)
- **Map** pick (GPS pin + selection pin), city search (city + state)
- **Radar** link (weather.gov) from the app bar
- **Sun / moon / space weather / earthquakes / severe weather** summaries (title-bar menu, drawer, body switcher tabs)
  - Hazard maps with **push-pin** markers; explore radius and history (1d–6m or custom date range)
  - Earthquake magnitude filter; severe weather tropical strength / EF filters
- **Home widget** with bundled NWS icons (works on CalyxOS)
- **Settings** for layout, widget mode, hourly tabs, hamburger menu, map focus radius (up to 4000 mi), hazard history default, space weather cue thresholds

## Download

[**Releases**](https://github.com/crome1394/forecast-point/releases) — install the latest `PointForecast-*.apk`.

```bash
adb install -r PointForecast-1.1.3.apk
```

## Build

**Needs:** JDK 17+, Android SDK.

```bash
cd forecast-point
./gradlew :app:assembleRelease   # or :app:assembleDebug
```

Output: `app/build/outputs/apk/…/app-*.apk`

```bash
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
# or debug:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Signed store / F-Droid builds: see [RELEASE.md](RELEASE.md) and [CHANGELOG.md](CHANGELOG.md).

## Specs

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Min / target SDK | 26 / 35 |
| Maps | osmdroid + OSM / CARTO (no Google Play Services) |

## Data sources

| Data | Source |
|------|--------|
| Forecast, observations, alerts | NWS (`weather.gov`, `api.weather.gov`) |
| Tides / water levels | NOAA CO-OPS (tides, subordinate offsets, Great Lakes); USGS NWIS stage (rivers/inland) |
| Space weather (Kp / G/R/S) | NOAA SWPC |
| Earthquakes | USGS Earthquake Hazards Program (FDSN) |
| Severe weather (tornadoes / tropical) | NOAA SPC storm reports + WCM archive; NHC CurrentStorms; NWS CAP alerts |
| Air quality, UV, pressure | [Open-Meteo](https://open-meteo.com/) (optional extras) |
| Search / map | OpenStreetMap Nominatim, osmdroid, CARTO |

Coverage for core forecast is **U.S. NWS points**. Use APIs politely (clear User-Agent, caching).

## Privacy

No accounts. No analytics backend in this project. Location is used only when you pick GPS, map, or search. Network traffic goes to the public services above.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Release steps: [RELEASE.md](RELEASE.md). Credits and legal notes: [NOTICE](NOTICE).

## License

[MIT](LICENSE). Third-party data and libraries keep their own terms ([NOTICE](NOTICE)).
