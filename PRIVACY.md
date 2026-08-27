# Privacy policy — Point Forecast

**Last updated:** 2026-08-15

Point Forecast is free and open-source software. It is designed for **local-first** weather display with **no accounts** and **no analytics**.

## What we collect

**We do not operate a backend that collects your data.** The app authors do not receive:

- analytics or usage telemetry  
- crash reports (no Firebase, Sentry, Crashlytics, etc.)  
- advertising identifiers  
- account information (there are no accounts)

## Data stored on your device

Preferences and the last weather snapshot are stored **only on your device** (Android DataStore / app storage), including:

- saved cities and last selected location  
- settings (units, tabs, map radius, etc.)  
- cached forecast JSON for offline display  

Uninstalling the app removes this data (subject to Android backup if you enable system backups).

## Network requests

When you use the app (or enable auto-refresh), it contacts **public data services** to fetch weather and maps. Typical hosts:

| Purpose | Hosts (HTTPS only) |
|---------|-------------------|
| Forecasts & alerts | `api.weather.gov`, `forecast.weather.gov` |
| Radar link (browser) | `radar.weather.gov` |
| Tides / water levels | `api.tidesandcurrents.noaa.gov` |
| Space weather | `services.swpc.noaa.gov` |
| Earthquakes | `earthquake.usgs.gov` |
| River/lake stage | `waterservices.usgs.gov` |
| Tropical storms | `www.nhc.noaa.gov` |
| Tornado reports | `www.spc.noaa.gov` |
| Optional hourly extras | `api.open-meteo.com`, `air-quality-api.open-meteo.com` |
| Map tiles | `tile.openstreetmap.org` (OpenStreetMap) |
| Geocoding | `nominatim.openstreetmap.org` |

Those operators may log standard HTTP access (IP, User-Agent, path) under **their** policies. Point Forecast sends a clear open-source User-Agent identifying the app.

**No cleartext (HTTP) traffic** is permitted by the app’s network security config.

## Location

- Location permission is used **only** when you choose **Current Location / GPS** or place a point on the map.  
- The app does **not** continuously track you in the background for advertising or profiling.  
- Optional background work (WorkManager) only refreshes the **forecast for your saved point** if you enable auto-update; it does not stream live GPS.

## Permissions

| Permission | Why |
|------------|-----|
| Internet / network state | Fetch public weather & map data |
| Fine / coarse location | Optional GPS “Current Location” |
| Receive boot completed | Reschedule optional auto-refresh after reboot |

The app does **not** request contacts, microphone, camera, SMS, or notification posting for ads.

## Third-party code

Dependencies are open-source (Kotlin, AndroidX, OkHttp, Coil, osmdroid, etc.). See [NOTICE](NOTICE) and the project license [LICENSE](LICENSE).

## Contact

Issues and privacy questions: [GitHub repository](https://github.com/crome1394/point-forecast).
