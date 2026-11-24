<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Point Forecast icon" width="120" />
</p>

<h1 align="center">Point Forecast</h1>

<p align="center">
  <strong>U.S. National Weather Service point forecasts</strong> on Android: clear, private, and fully open source.
</p>

<p align="center">
  <a href="https://f-droid.org/packages/com.crome.forecastpoint/"><img src="https://img.shields.io/f-droid/v/com.crome.forecastpoint?logo=f-droid&label=F-Droid&color=1976D2" alt="F-Droid" /></a>
  <a href="https://github.com/crome1394/point-forecast/releases/latest"><img src="https://img.shields.io/github/v/release/crome1394/point-forecast?logo=github&label=GitHub&color=FF8F00" alt="GitHub release" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-E65100?logo=open-source-initiative&logoColor=white" alt="MIT License" /></a>
  <a href="https://buymeacoffee.com/crome1394"><img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?logo=buymeacoffee&logoColor=000000" alt="Buy Me a Coffee" /></a>
</p>

<p align="center">
  <a href="https://f-droid.org/packages/com.crome.forecastpoint/"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="60" /></a>
</p>

Point Forecast shows the NWS forecast for any U.S. point, along with hourly tables, alerts, radar, tides, and a hub for sun, moon, space weather, earthquakes, and severe weather. It has no accounts, analytics, or ads, and it does not need Google Play Services. *Not affiliated with NOAA or the NWS.*

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="180" alt="Forecast" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="180" alt="Hourly" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="180" alt="Alerts" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="180" alt="Severe weather" />
</p>

## Features

| | |
|---|---|
| **Forecast** | Current conditions and the multi-day NWS forecast, with pull-to-refresh |
| **Hourly** | Tabs you can reorder: temperature, precipitation, wind, tides, air quality, visibility, pressure, UV, and space weather |
| **Tides** | Diagram of the nearest NOAA station for each city, with coordinates and station code; all saved cities are one tap away |
| **Today's tides** | Two lows and two highs in a compact table under Current Conditions, hidden for landlocked cities |
| **Alerts** | Active watches, warnings, and advisories from NWS CAP |
| **Map and radar** | Pick a location on OpenStreetMap or search by city; open weather.gov radar from the app bar |
| **Hazard hub** | Sun and moon, space weather, earthquakes, tornadoes, and tropical cyclones, with maps and history windows |
| **Widget** | Home screen widget that uses bundled NWS icons |
| **Units** | US customary or metric, including the NWS forecast text |

## Install

| Channel | Notes |
|---|---|
| [F-Droid](https://f-droid.org/packages/com.crome.forecastpoint/) | Recommended. Reproducible builds signed by the developer |
| [GitHub Releases](https://github.com/crome1394/point-forecast/releases) | `app-release-signed.apk`, signed with the same key as the F-Droid build |

If you installed an F-Droid build older than 1.1.9, uninstall it once and reinstall.

## Build

Requires JDK 17+ and the Android SDK.

```bash
cd forecast-point
./gradlew :app:assembleDebug     # or :app:assembleRelease
```

## Documentation

| Document | What's in it |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Code layout, data flow, and every data source with its fallback |
| [PRIVACY.md](PRIVACY.md) | What leaves the device and where it goes |
| [RELEASE.md](RELEASE.md) | Steps for cutting a signed release |
| [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md) | Verifying that an APK matches the source |
| [docs/F-DROID-SUBMISSION.md](docs/F-DROID-SUBMISSION.md) | F-Droid inclusion notes and the [metadata recipe](metadata/com.crome.forecastpoint.yml) |
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to send changes |
| [NOTICE](NOTICE) | Credits for third-party data and libraries |

## Support

If Point Forecast is useful to you, you can support future updates at [buymeacoffee.com/crome1394](https://buymeacoffee.com/crome1394).

## License

[MIT](LICENSE). Third-party data and libraries keep their own terms; see [NOTICE](NOTICE).
