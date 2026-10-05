# ShowFavicon (Android)

A home screen widget that keeps one favicon per monitored website. Tap an icon
and the site opens in the browser. The icons are refreshed once an hour; a site
whose last fetch failed keeps its icon but shows it grayed out.

## Status

Milestones 1 to 4 of [`doc/plan-android.md`](doc/plan-android.md) are
implemented: widget, settings screen, hourly fetch with cache, grayed icons and
a multi-site list of up to four entries. The project has **not been compiled
yet** — no Android SDK is installed on the machine this was written on.

Known gaps against the plan:

- Only the first four sites get a widget slot (`MAX_SLOTS` in
  `FaviconWidgetProvider`), because `RemoteViews` cannot add views at runtime.
- Per widget instance configuration (`android:configure`) is not wired up yet.
- No notification / foreground service; the widget is the only surface.
- No launcher icon artwork yet, only a placeholder ring.

## Requirements

- JDK 21 (the Android Gradle Plugin 9.4 rejects newer JDKs in this setup)
- Android SDK with platform 36, build-tools 36.0.0 and platform-tools
- `adb`, and a device or an emulator (needs `/dev/kvm`)

[`scripts/install-android-toolchain.sh`](scripts/install-android-toolchain.sh)
installs all of that on CachyOS / Arch, idempotently. `--dry-run` prints the
steps without touching the system.

## Build

```fish
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew installDebug         # same, onto the connected device
```

The SDK location comes from `local.properties` (`sdk.dir=…`, not versioned) or
from `ANDROID_HOME`.

## How it works

| Piece | Responsibility |
| --- | --- |
| `MainActivity` | Settings screen: the list of sites, add and remove |
| `SiteStore` | The site list, in `SharedPreferences` as one newline separated value |
| `FaviconWorker` | Hourly `WorkManager` job: fetch, cache, redraw the widget |
| `FaviconFetcher` | Two small `HttpURLConnection` GETs: the page, then the icon |
| `FaviconResolver` | Finds `<link rel="…icon…">` candidates, skips SVG, falls back to `/favicon.ico` |
| `FaviconStore` | `filesDir/showfavicon/<host>.png`, atomic writes, failed-fetch flag, graying |
| `FaviconWidgetProvider` | Draws one slot per site, tap opens the site |

The cache is the only state: if a fetch fails, the previous PNG stays in place
and is drawn desaturated through a `ColorMatrix`.

## Vibecoded

This project was written by an AI assistant.

- **Model:** DeepSeek V4 Flash
- **Editor:** Visual Studio Code 1.140.0
- **Extension:** `vizards.deepseek-v4-for-copilot` 0.9.3
- **Build tooling:** Android Gradle Plugin 9.4.1, Gradle 9.8.0, Kotlin 2.4.20,
  compileSdk / targetSdk 36, minSdk 26
