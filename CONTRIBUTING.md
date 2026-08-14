# Contributing to Point Forecast

Thanks for your interest in improving Point Forecast.

## Ground rules

1. **Be kind.** Assume good intent.
2. **Stay independent.** Do not copy proprietary code or assets from commercial weather apps.
3. **Respect data providers.** Use NWS/NOAA/OSM endpoints with a clear User-Agent and avoid abusive request rates.
4. **Credit sources.** Update [NOTICE](NOTICE) when you add libraries or data feeds.

## Development setup

1. Install **JDK 17** and the **Android SDK**.
2. Clone this repository.
3. Open `forecast-point/` in Android Studio, or build from the CLI:

```bash
cd forecast-point
./gradlew :app:assembleDebug
```

4. Install on a device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Making changes

1. Create a branch from `main` (or your default branch):

```bash
git checkout -b feature/short-description
```

2. Make focused commits with clear messages.
3. Update `CHANGELOG.md` under an `[Unreleased]` section if the change is user-visible.
4. Open a pull request (once the project is on GitHub) describing *what* and *why*.

## Coding notes

- Kotlin + Jetpack Compose for UI  
- Home widget uses `RemoteViews` (XML layouts under `app/src/main/res/layout/`)  
- Prefer public APIs and bundled icons over remote weather.gov icon fetches on the UI path  

## Reporting issues

Please include:

- Device / Android version (e.g. CalyxOS, Pixel)  
- Steps to reproduce  
- Location type if relevant (coastal tides, inland, etc.)  
- Logcat snippet if the app crashes  

## License

By contributing, you agree that your contributions are licensed under the MIT
License (same as this repository).
