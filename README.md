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

- **Current conditions** and multi-day NWS forecast  
- **Hourly** tables: temperature, precip, wind, conditions; optional tides, air quality, visibility, pressure, UV, space weather  
- **Alerts** when watches/warnings/advisories are active  
- **Map** pick (GPS pin + selection pin), city search (city + state)  
- **Radar** link (weather.gov) from the app bar  
- **Home widget** with bundled NWS icons (works on CalyxOS)  
- **Settings** for layout, widget mode, and which hourly tabs to show  

## Download

[**Releases**](https://github.com/crome1394/forecast-point/releases) — install the latest `PointForecast-*.apk`.

```bash
adb install -r PointForecast-1.0.3.apk
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

F-Droid and signed store builds use **release** variants built from a git tag. See [CHANGELOG.md](CHANGELOG.md).

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
| Tides | NOAA CO-OPS |
| Space weather (Kp / G-scale) | NOAA SWPC |
| Air quality, UV, pressure | [Open-Meteo](https://open-meteo.com/) (optional extras) |
| Search / map | OpenStreetMap Nominatim, osmdroid, CARTO |

Coverage is **U.S. NWS points** for core forecast. Use APIs politely (clear User-Agent, caching).

## Privacy

No accounts. No analytics backend in this project. Location is used only when you pick GPS, map, or search. Network traffic goes to the public services above.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Credits and legal notes: [NOTICE](NOTICE).

## License

[MIT](LICENSE). Third-party data and libraries keep their own terms ([NOTICE](NOTICE)).
