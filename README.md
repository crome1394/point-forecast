# Forecast Point

**Forecast Point** is an independent, open-source Android weather client for
**U.S. National Weather Service** point forecasts. It focuses on clear forecasts,
hourly tables, tides (where available), active alerts, an offline-friendly map
picker, and a resizable home-screen widget—with **bundled NWS forecast icons**
so they display correctly on de-Googled devices such as **CalyxOS**.

> **Not affiliated with NOAA, the National Weather Service, or Pandamonium Software.**  
> This is a community project that uses **public** weather data APIs.

| | |
|---|---|
| **App name** | Forecast Point |
| **Application ID** | `com.crome.forecastpoint` |
| **Min Android** | 8.0 (API 26) |
| **License** | MIT (see [LICENSE](LICENSE)) |
| **Android project** | [`forecast-point/`](forecast-point/) |

## Download (APK)

**Prefer a [GitHub Release](https://github.com/crome1394/forecast-point/releases)** over committing APKs into the repo. Releases keep `main` small, give you versioned history, and provide a stable download URL.

### Latest release

| | |
|---|---|
| **Releases page** | [github.com/crome1394/forecast-point/releases](https://github.com/crome1394/forecast-point/releases) |
| **Direct APK (v1.0.2)** | [ForecastPoint-1.0.2.apk](https://github.com/crome1394/forecast-point/releases/download/v1.0.2/ForecastPoint-1.0.2.apk) |

> The direct link works **after** you publish a release named `v1.0.2` and attach the file `ForecastPoint-1.0.2.apk` (see below).

### Install on Android

1. Download the APK on your phone (or copy it over USB).
2. Allow install from that source if prompted (unknown apps / Files / browser).
3. Open the APK and install.

Or from a computer:

```bash
adb install -r ForecastPoint-1.0.2.apk
```

### Does the file have to be named `app-debug.apk`?

**No.** That name is only Gradle’s default debug output path:

```text
forecast-point/app/build/outputs/apk/debug/app-debug.apk
```

For distribution, rename it to something clear, for example:

```text
ForecastPoint-1.0.2.apk
```

### Publish a release (recommended)

On your machine, after the project builds:

```bash
cd ~/src/noaa/forecast-point
./gradlew :app:assembleDebug

# Friendly name for the release asset
mkdir -p ../dist
cp app/build/outputs/apk/debug/app-debug.apk ../dist/ForecastPoint-1.0.2.apk
```

Then on GitHub:

1. Open **https://github.com/crome1394/forecast-point/releases/new**
2. **Choose a tag:** `v1.0.2` (create new tag on publish, target `main`)
3. **Release title:** `Forecast Point 1.0.2`
4. Paste notes from [CHANGELOG.md](CHANGELOG.md) (1.0.2 section)
5. **Attach** `dist/ForecastPoint-1.0.2.apk` (drag and drop)
6. Publish release

The direct download URL will be:

```text
https://github.com/crome1394/forecast-point/releases/download/v1.0.2/ForecastPoint-1.0.2.apk
```

## Features

- Current conditions (expandable details)
- Multi-day forecast cards
- Hourly forecast (temperature with heat-scale colors, precipitation, wind, tides, conditions)
- Swipe between hourly tabs
- Active watches / warnings / advisories when issued
- Home-screen widget (period temps or high/low; resizable)
- Add cities by search, map pin (confirm chip), or current GPS location
- Favorites with rename / remove
- Radar link centered on the active location (`radar.weather.gov`)
- Settings: auto-update interval, title bar position, widget mode, map search position, expand Current Conditions

## Inspiration

The overall UX was **inspired by** the commercial app
[NOAA Weather & Tides](https://play.google.com/store/apps/details?id=pandamonium.noaaweather)
by **Pandamonium Software**.

Forecast Point is a **separate reimplementation**:

- New Kotlin / Jetpack Compose codebase  
- Public NWS, NOAA CO-OPS, and OSM services  
- Bundled public-style NWS icons for reliability  
- **No** proprietary code or assets from that Play Store app  

See [NOTICE](NOTICE) for full attribution.

## Screenshots

Development screenshots (for reference) live under [`Screenshots/`](Screenshots/).

## Build from source

Requirements:

- JDK 17+
- Android SDK (platform 35 recommended)
- Network for dependencies and live weather data

```bash
cd forecast-point
./gradlew :app:assembleDebug
```

Gradle writes:

```text
forecast-point/app/build/outputs/apk/debug/app-debug.apk
```

Rename for sharing (optional but recommended):

```bash
cp app/build/outputs/apk/debug/app-debug.apk ../dist/ForecastPoint-1.0.2.apk
adb install -r ../dist/ForecastPoint-1.0.2.apk
```

> **Note:** This build is a *debug* APK (fine for personal/sideload use). A signed *release* build is better for wider distribution; that needs a keystore you keep private.

## Data sources

| Data | Source |
|------|--------|
| Forecast / observations | NWS MapClick + digitalJSON (`forecast.weather.gov`) |
| Grid extras (QPF, gusts, dewpoint) | `api.weather.gov` gridpoints |
| Alerts | `api.weather.gov/alerts/active` |
| Tides | NOAA CO-OPS datagetter + station catalog |
| Radar (browser) | `radar.weather.gov` location bookmark |
| Search / reverse geocode | OpenStreetMap Nominatim |
| Map | osmdroid + CARTO light tiles / OSM data |

Please use these services respectfully (identify your client, cache when possible).

## Project layout

```text
.
├── LICENSE                 MIT license
├── NOTICE                  Credits & disclaimers
├── README.md               This file
├── CHANGELOG.md            Release history
├── CONTRIBUTING.md         How to contribute
├── .gitignore
├── forecast-point/         Android Studio / Gradle app
│   └── app/
├── Screenshots/            Optional UI captures
└── icon-reference/         Sample NWS icon files (dev reference)
```

## Privacy

Forecast Point does not require a user account. Location is used only when you
choose GPS, map pick, or search. Weather requests go to public NWS/NOAA/OSM
endpoints; there is no separate Forecast Point analytics backend in this
project.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT — see [LICENSE](LICENSE).

Third-party data and libraries remain under their own terms (see [NOTICE](NOTICE)).
