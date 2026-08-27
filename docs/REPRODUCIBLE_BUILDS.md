# Reproducible upstream-signed builds (F-Droid)

Point Forecast aims to use F-Droid’s **reproducible upstream-signed** flow
(same pattern as many modern F-Droid apps, e.g. Harbor):

1. You publish a signed APK on GitHub: `app-release-signed.apk` on tag `vX.Y.Z`.
2. F-Droid rebuilds that tag from source.
3. If the rebuild matches (except the signature), F-Droid publishes **your** APK.
4. `AllowedAPKSigningKeys` in fdroiddata pins your certificate fingerprint.

Official docs: [F-Droid Reproducible Builds](https://f-droid.org/en/docs/Reproducible_Builds/).

## Why

- Same signing identity on GitHub and F-Droid (in-place updates between sources).
- Less trust placed solely in F-Droid’s per-app signing key.
- Transparent proof that the published APK matches the tagged source.

## What is *not* required

Listing on F-Droid does **not** require this. The classic flow (F-Droid builds and
signs) remains valid. Upstream signing is an upgrade we chose to implement.

## One-time migration for existing F-Droid users

Installs that were signed by **F-Droid’s** key cannot update to an APK signed by
**our** release key. Those users must uninstall and reinstall once. After that,
GitHub and F-Droid builds share one signature.

Switching early (small install base) keeps that pain small.

## Release contract

| Item | Value |
|---|---|
| Git tag | `v<versionName>` |
| GitHub asset | `app-release-signed.apk` |
| `Binaries` | `https://github.com/crome1394/point-forecast/releases/download/v%v/app-release-signed.apk` |
| Signer tool | Android **Build Tools 34** `apksigner` (not 35+) |
| Gradle output | **Unsigned** `app-release-unsigned.apk` (do not Gradle-sign release) |

## Local tooling

| Script | Purpose |
|---|---|
| `scripts/create-release-keystore.sh` | Create keystore **outside** the repo |
| `scripts/verify-deterministic-build.sh` | Two clean builds; unsigned APK hashes must match |
| `scripts/sign-release.sh` | Sign with BT 34 `apksigner` |
| `scripts/verify-release-signing.sh` | Pin/check cert SHA-256 |
| `scripts/expected-release-cert.sha256` | Committed digest for `AllowedAPKSigningKeys` |

See [RELEASE.md](../RELEASE.md) for the full cut checklist.

## Secrets

Never commit:

- The `.jks` / `.keystore` file
- Passwords / `keystore.properties`

`.gitignore` already excludes common keystore paths. Back up the keystore and
passwords in a password manager; losing them permanently breaks update continuity
for the application ID `com.crome.forecastpoint`.
