# Submitting Point Forecast to F-Droid

## GitHub vs GitLab (you use both)

| | Role |
|---|------|
| **GitHub** (`crome1394/point-forecast`) | **Your app** — source code, tags (`v1.1.3`), releases, issue tracker. This stays your home. |
| **GitLab** (fdroid / fdroiddata) | **Only the packaging recipe** — a YAML file that tells F-Droid *how* to clone GitHub and build the APK. You open a merge request there once (and later for major recipe changes). |

F-Droid’s build servers **clone from GitHub**. They do **not** require moving the project off GitHub. GitLab is just where F-Droid’s community accepts metadata merge requests.

Your email domain (betacaeli.com) is optional in the YAML as `AuthorEmail`.

---

## One-time steps (GitLab account you just created)

1. Log in to [gitlab.com](https://gitlab.com).
2. Open **https://gitlab.com/fdroid/fdroiddata** and click **Fork** (fork into your user namespace).
3. Clone **your fork** (not the app repo):

   ```bash
   git clone https://gitlab.com/<YOUR_GITLAB_USERNAME>/fdroiddata.git
   cd fdroiddata
   git checkout -b new-app-point-forecast
   ```

4. Copy the recipe from this project:

   ```bash
   # From your point-forecast repo:
   cp /home/crome/src/noaa/metadata/com.crome.forecastpoint.yml \
      metadata/com.crome.forecastpoint.yml
   ```

5. Commit and push the fork branch:

   ```bash
   git add metadata/com.crome.forecastpoint.yml
   git commit -m "New app: Point Forecast (com.crome.forecastpoint)"
   git push -u origin new-app-point-forecast
   ```

6. On GitLab, open a **Merge Request** from your branch → `fdroid/fdroiddata` `master`.
7. Paste the **merge request description** from the section below.
8. Wait for a maintainer. They may edit the YAML slightly; that is normal.

Optional later: add Fastlane text/screenshots under  
`fastlane/metadata/android/en-US/` in the **GitHub** app repo so F-Droid can pick up store graphics automatically.

---

## Merge request title

```text
New app: Point Forecast (com.crome.forecastpoint)
```

## Merge request description (paste into GitLab)

```markdown
## New application

| | |
|---|---|
| **Name** | Point Forecast |
| **Application ID** | `com.crome.forecastpoint` |
| **License** | MIT |
| **Source** | https://github.com/crome1394/point-forecast |
| **Issues** | https://github.com/crome1394/point-forecast/issues |
| **Latest tag** | `v1.1.3` (`versionCode` 6) |
| **Gradle subdir** | `forecast-point` |

### Summary

U.S. NWS point forecasts for Android — free software, no Google Play Services, no ads or analytics.

### Why F-Droid

- MIT licensed source on GitHub
- FOSS dependencies only (AndroidX, OkHttp, Coil, osmdroid, etc.)
- No proprietary SDKs, no tracking
- Optional location only when the user chooses GPS/map
- HTTPS to public weather/map APIs (NWS/NOAA, USGS, OSM, Open-Meteo)

### Build notes

- Repo root is **not** the Android project root; metadata uses `subdir: forecast-point`.
- First build block targets tag **`v1.1.3`**.
- Suggested categories: Internet, Science & Education.

### Privacy

https://github.com/crome1394/point-forecast/blob/main/PRIVACY.md

### Screenshots

Current UI captures:  
https://github.com/crome1394/point-forecast/tree/main/Screenshots  

(Happy to add Fastlane metadata in a follow-up if preferred.)

### Maintainer notes

I am the upstream author. This is the first F-Droid submission; please suggest any YAML adjustments needed for the buildserver.
```

---

## What you do **not** need to do

- Move the app from GitHub to GitLab  
- Host a website on betacaeli.com  
- Set up reproducible upstream signing for the first MR  
- Upload an APK to F-Droid yourself (they build from source)
