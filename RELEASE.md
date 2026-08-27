# Releasing Point Forecast

How to cut a public release (GitHub + F-Droid upstream-signed reproducible flow).

Background: [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md).

## Prerequisites

- Clean working tree on `main` (after merge)
- JDK 17+, Android SDK with **Build Tools 34.0.0** (`apksigner`)
- Release keystore **outside** the repo (see `scripts/create-release-keystore.sh`)
- Env vars for signing (never commit these):

```bash
export PF_RELEASE_KEYSTORE="$HOME/.local/share/point-forecast/point-forecast-release.jks"
export PF_RELEASE_KEY_ALIAS=pointforecast
export PF_KEYSTORE_PASSWORD='…'
export PF_KEY_PASSWORD='…'
```

## Version numbers

1. Choose the next semver (`versionName`) and bump `versionCode` by 1.
2. Update `forecast-point/app/build.gradle.kts`.
3. Move `[Unreleased]` notes in [CHANGELOG.md](CHANGELOG.md) under `## [X.Y.Z] — YYYY-MM-DD`.
4. Leave a fresh empty `## [Unreleased]` section at the top.

## Build (unsigned, deterministic)

Prefer a **fresh clone** of the exact commit/tag you will publish (matches F-Droid).

```bash
cd forecast-point
./gradlew --no-daemon --no-parallel --no-configuration-cache clean :app:assembleRelease
```

Unsigned output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Same-machine determinism smoke test (from repo root):

```bash
sh scripts/verify-deterministic-build.sh
```

## Sign (Build Tools 34 only)

```bash
# From repo root, with PF_* env set:
mkdir -p dist
sh scripts/sign-release.sh \
  forecast-point/app/build/outputs/apk/release/app-release-unsigned.apk \
  dist/app-release-signed.apk
```

The script verifies the certificate against `scripts/expected-release-cert.sha256`.

**Do not** sign with Build Tools 35+ `apksigner` for F-Droid upstream-signed releases.

## Install smoke test

```bash
adb install -r dist/app-release-signed.apk
# If INSTALL_FAILED_UPDATE_INCOMPATIBLE: uninstall the debug/F-Droid build first.
```

## Git tag and GitHub release

Attach the asset with this **exact** name (F-Droid `Binaries` contract):

```text
app-release-signed.apk
```

```bash
# From repo root, on main after version bump is pushed:
git status   # should be clean
git tag -a vX.Y.Z -m "Point Forecast X.Y.Z"
git push origin main
git push origin vX.Y.Z

gh release create vX.Y.Z dist/app-release-signed.apk \
  --title "Point Forecast X.Y.Z" \
  --notes-file -   # paste ## [X.Y.Z] from CHANGELOG
```

Push the tag only when the signed asset is ready to attach (or attach in the same `gh release create`).

## F-Droid

With `Binaries` + `AllowedAPKSigningKeys` in fdroiddata:

- `UpdateCheckMode: Tags` picks up `vX.Y.Z`
- F-Droid rebuilds from the tag and compares to your GitHub APK
- On match, they publish **your** signature
- On mismatch, that version is skipped (they will not fall back to F-Droid signing for this mode)

Watch:

- https://f-droid.org/packages/com.crome.forecastpoint/
- `https://f-droid.org/repo/com.crome.forecastpoint_<versionCode>.log.gz`

### Existing F-Droid users (one-time)

Users on older **F-Droid-signed** builds must uninstall/reinstall once to move to the developer key.

## Checklist

- [ ] Version name/code bumped  
- [ ] CHANGELOG `[Unreleased]` → dated version  
- [ ] `verify-deterministic-build.sh` passes  
- [ ] `sign-release.sh` → `dist/app-release-signed.apk`  
- [ ] `verify-release-signing.sh` passes  
- [ ] Device smoke test  
- [ ] Tag `vX.Y.Z` pushed  
- [ ] GitHub Release with asset **`app-release-signed.apk`**  
- [ ] (First RB release) fdroiddata MR includes `Binaries` + `AllowedAPKSigningKeys`  
