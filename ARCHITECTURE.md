# Architecture

Point Forecast is a single-module Android app (`forecast-point/app`) written in Kotlin with Jetpack Compose. It has no backend: the device calls public government and open-data APIs over HTTPS and caches the results locally.

## Layout

All source is under `forecast-point/app/src/main/java/com/crome/forecastpoint/`.

| Path | Role |
|---|---|
| `MainActivity.kt` | Compose host, navigation, drawer, and app bar |
| `ForecastPointApp.kt` | Application class; holds the shared `WeatherRepository` |
| `ui/WeatherViewModel.kt` | Screen state; owns the space weather, earthquake, and severe weather services |
| `ui/screens/` | One file per screen (Forecast, Hourly, Map, Search, Settings, Sun & Moon, Space Weather, Earthquakes, Severe Weather, About) |
| `data/WeatherRepository.kt` | Single entry point for forecast refreshes. A mutex stops overlapping refreshes from the UI, widget, and worker |
| `data/NwsApi.kt` | Builds the forecast snapshot from NWS and merges in tides and Open-Meteo extras. Also handles Nominatim geocoding |
| `data/TideService.kt`, `data/UsgsWaterData.kt` | Water level: picks the nearest NOAA CO-OPS station or USGS stream gage |
| `data/OpenMeteoService.kt` | Optional UV, visibility, pressure, and air quality |
| `data/SpaceWeatherService.kt` | NOAA SWPC Kp forecast and R/S/G scales |
| `data/EarthquakeService.kt` | USGS FDSN event queries |
| `data/SevereWeatherService.kt` | SPC tornado reports, NHC storms, and local tropical and tornado CAP alerts |
| `data/PreferencesRepository.kt` | DataStore: settings, saved cities, and the last snapshot |
| `data/Models.kt` | `WeatherSnapshot` and the other data classes stored in DataStore |
| `worker/` | WorkManager periodic refresh, rescheduled after boot |
| `widget/` | Home screen widget. It reads the cached snapshot and refreshes through the repository |
| `util/` | Unit conversion, icon mapping, sun and moon math, time zones, radar URLs, map tiles |

## Forecast refresh

```
UI / widget / worker
        │
        ▼
WeatherRepository.refresh()  ── mutex, 2-minute freshness check
        │
        ▼
NwsApi.fetchWeather()  ── all requests run in parallel
   ├─ MapClick FcstType=json         REQUIRED: periods, text, current obs, hazards
   ├─ MapClick FcstType=digitalJSON  optional: hourly rows
   ├─ api.weather.gov points → gridpoints   optional: QPF, gusts, dew point, visibility
   ├─ api.weather.gov alerts/active  optional: CAP alerts (MapClick hazards if this fails)
   ├─ TideService                    optional: CO-OPS / USGS water level
   └─ OpenMeteoService               optional: UV, pressure, visibility, AQI
        │
        ▼
WeatherSnapshot  →  DataStore  →  StateFlow  →  Compose UI and widget
```

Only the MapClick forecast JSON is required. If any other source fails, the snapshot is built without that data. If MapClick fails, the whole refresh fails and the last cached snapshot stays on screen.

### Hourly timeline

`digitalJSON` gives hourly arrays in the correct order, but its `time` and `unixtime` labels are often wrong. `parseHourly` ignores those labels and stamps the rows as a continuous series that starts at the first MapClick period's `startValidTime`. That series matches `api.weather.gov/.../forecast/hourly`. Grid values (QPF, gust, dew point, visibility) are expanded from ISO-8601 intervals into hourly buckets and joined on the epoch hour.

### Units

MapClick `unit=1` returns forecast temperatures and text in metric. Current observations and digital wind stay in US units, so `NwsApi` converts them. Grid values are always SI. The snapshot records which unit system it was built in, and a unit change forces a refresh.

### Alerts

`api.weather.gov/alerts/active` is authoritative: an empty result means there are no alerts. MapClick hazards are used only when the CAP request itself fails. Routine products such as the Hazardous Weather Outlook are filtered out in `NwsApi` and again in `WeatherRepository` when the cache loads.

## Data sources

| Data | Endpoint | Used by | If it fails |
|---|---|---|---|
| Forecast, current obs | `forecast.weather.gov/MapClick.php?FcstType=json` | `NwsApi` | Refresh fails; cached data stays |
| Hourly | `forecast.weather.gov/MapClick.php?FcstType=digitalJSON` | `NwsApi` | Hourly screen is empty |
| Grid series | `api.weather.gov/points`, `/gridpoints` | `NwsApi` | No QPF; gust and dew point fall back to digital or computed values |
| Alerts | `api.weather.gov/alerts/active` | `NwsApi`, `SevereWeatherService` | MapClick hazard list |
| Tides | `api.tidesandcurrents.noaa.gov` (datagetter, mdapi) | `TideService` | Tide tab shows the reason |
| River stage | `api.waterdata.usgs.gov/ogcapi/v1` | `UsgsWaterData` | Tide tab shows the reason |
| UV, pressure, visibility | `api.open-meteo.com/v1/forecast` | `OpenMeteoService` | Columns blank (optional in Settings) |
| Air quality | `air-quality-api.open-meteo.com/v1/air-quality` | `OpenMeteoService` | Columns blank (optional in Settings) |
| Space weather | `services.swpc.noaa.gov/products/*.json` | `SpaceWeatherService` | Screen shows an error |
| Earthquakes | `earthquake.usgs.gov/fdsnws/event/1/query` | `EarthquakeService` | Screen shows an error |
| Tornado reports | `spc.noaa.gov/climo/reports/*.csv` (last 400 days), `spc.noaa.gov/wcm/data/*.csv` (older) | `SevereWeatherService` | Fewer or no reports |
| Tropical storms | `nhc.noaa.gov/CurrentStorms.json` | `SevereWeatherService` | No storms listed |
| Geocoding | `nominatim.openstreetmap.org` | `NwsApi` | Search returns nothing |
| Map tiles | `tile.openstreetmap.org` | `util/MapTiles` (osmdroid) | Blank map |
| Radar | `radar.weather.gov` (opened in the browser) | `util/RadarUrl` | n/a |

Weather icons are bundled in `assets/nws_icons/` and mapped by `IconMapper`, so the app never loads icon images from NWS servers.

## Networking

- OkHttp 4 with timeouts set per service. Every request sends a `PointForecast/<version>` User-Agent, as NWS, Nominatim, and the OSM tile policy ask.
- `res/xml/network_security_config.xml` blocks cleartext traffic and trusts only system CAs.
- There are no API keys. USGS and Open-Meteo limit anonymous calls by IP.
- The CO-OPS and USGS station lists are cached in app storage (`tide_stations_v3.json`).

## Background work

`WeatherUpdateScheduler` schedules `WeatherUpdateWorker` at the interval set in Settings, and `BootReceiver` re-applies the schedule after a reboot. The worker and the widget call the same `WeatherRepository.refreshActive()`, so the mutex and freshness check apply to them too.

## Build and release

Gradle Kotlin DSL; min SDK 26, target SDK 35. Release APKs are reproducible and signed by the developer. See [RELEASE.md](RELEASE.md), [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md), and `scripts/`.
