# Releasing Point Forecast

How to cut a public release from this repository (GitHub APK and/or F-Droid).

## Prerequisites

- Clean working tree on the branch you intend to ship (usually `main` after merge)
- JDK 17+, Android SDK
- For a **signed** APK/AAB: a keystore and `keystore.properties` (never commit these)

Example `forecast-point/keystore.properties` (local only):

```properties
storeFile=/path/to/your.keystore
storePassword=***
keyAlias=***
keyPassword=***
```

Wire signing in `app/build.gradle.kts` if not already configured for release.

## Version numbers

1. Choose the next version (semver). Feature-heavy branch → **1.1.0** is appropriate after 1.0.3.
2. Update `forecast-point/app/build.gradle.kts`:
   - `versionName = "1.1.0"`
   - `versionCode` must increase (e.g. `5` if 1.0.3 was `4`)
3. Move `[Unreleased]` notes in [CHANGELOG.md](CHANGELOG.md) under `## [1.1.0] — YYYY-MM-DD`
4. Leave a fresh empty `## [Unreleased]` section at the top
5. Update any version examples in [README.md](README.md) if they name a specific APK

## Build

```bash
cd forecast-point
./gradlew clean :app:assembleRelease
```

Unsigned output (if signing is not configured):

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Signed output (if signing is configured):

```text
app/build/outputs/apk/release/app-release.apk
```

Optional copy for distribution:

```bash
mkdir -p ../dist
cp app/build/outputs/apk/release/app-release*.apk \
  ../dist/PointForecast-1.1.0.apk
# or rename after signing:
# apksigner sign --ks your.keystore --out ../dist/PointForecast-1.1.0.apk app-release-unsigned.apk
```

Install smoke test:

```bash
adb install -r ../dist/PointForecast-1.1.0.apk
```

## Git tag and GitHub release

```bash
# From repo root, on main after merge:
git status   # should be clean
git tag -a v1.1.0 -m "Point Forecast 1.1.0"
git push origin main
git push origin v1.1.0
```

Create a GitHub Release for tag `v1.1.0`:

1. Title: `Point Forecast 1.1.0`
2. Body: paste the `## [1.1.0]` section from CHANGELOG (user-facing bullets)
3. Attach `PointForecast-1.1.0.apk` (signed preferred)

```bash
# Optional CLI:
gh release create v1.1.0 dist/PointForecast-1.1.0.apk \
  --title "Point Forecast 1.1.0" \
  --notes-file -   # paste changelog section, Ctrl-D
```

## Closing the feature branch

After the work is merged to `main`:

```bash
git checkout main
git pull origin main
git branch -d feature/sun-moon-screen
git push origin --delete feature/sun-moon-screen   # if remote branch exists
```

Or merge via GitHub PR, then delete the branch in the UI.

## F-Droid (if applicable)

- Point F-Droid metadata at the **git tag** (`v1.1.0`)
- Build type: release; `versionCode` / `versionName` must match the tag’s `build.gradle.kts`
- Ensure reproducible or documented signing policy matches your F-Droid recipe
- Update package description if new data sources appear (earthquakes, NHC/SPC, USGS stage)

## Checklist

- [ ] Version name/code bumped  
- [ ] CHANGELOG `[Unreleased]` → dated version  
- [ ] `./gradlew :app:assembleRelease` succeeds  
- [ ] Smoke test: forecast, hourly, map, sun/moon hub, tides, settings  
- [ ] Tag `vX.Y.Z` pushed  
- [ ] GitHub Release + APK attached  
- [ ] Feature branch deleted after merge  
