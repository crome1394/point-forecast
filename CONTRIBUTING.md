# Contributing to Point Forecast

## Rules

1. Be kind.
2. Do not copy proprietary code or assets from commercial weather apps.
3. Respect NWS/NOAA/OSM usage (clear User-Agent, no abusive rates).
4. Update [NOTICE](NOTICE) when adding libraries or data feeds.

## Setup

JDK 17+, Android SDK.

```bash
cd forecast-point
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Changes

1. Branch from `main`: `git checkout -b feature/short-name`
2. Focused commits; user-visible changes under `CHANGELOG.md` → `[Unreleased]`
3. Open a pull request with what and why

## Issues

Include device/OS, steps to reproduce, and logcat if it crashes.

## License

Contributions are MIT, same as this repository.
