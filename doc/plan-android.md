# Plan 3 — Android

## Goal

A persistent presence per site. The main surface is a home-screen **App Widget**:
one icon per site, tap → open browser, hourly refresh, grayed icon when offline.
A persistent notification can carry the same presence into the status bar later.
Configuration is **not** drag-and-drop — a settings screen with a list of URLs is
enough.

## Progress

Implemented, but **not compiled yet**: no Android SDK on the machine this was
written on, so `./gradlew assembleDebug` is the first real check. The XML
resources, the resource references and the version catalog are verified
statically.

- [x] **Milestone 0 — toolchain.** `scripts/install-android-toolchain.sh`, with
      the SDK package names verified against Google's repository XML.
- [x] **Milestone 1 — widget and settings.** `FaviconWidgetProvider`,
      `MainActivity`, `SiteAdapter`, layouts, adaptive launcher icon.
- [x] **Milestone 2 — fetch and cache.** `FaviconWorker` (hourly WorkManager
      job), `FaviconFetcher`, `FaviconResolver`, `FaviconStore`.
- [x] **Milestone 3 — grayed icon.** Desaturation through a `ColorMatrix`,
      driven by the failed-fetch flag in the store.
- [ ] **Milestone 4 — multi-site.** A single widget shows one icon per site, up
      to four slots (`RemoteViews` cannot add views at runtime). The settings
      list feeds it. Still open: `android:configure` for per-instance sites.
- [ ] **Milestone 5 — notification.** Optional, and not started.
- [ ] **Milestone 6 — share intent.** Not started.
- [ ] **Milestone 7 — AAB release.** Not started.

## Toolchain (Linux / CachyOS)

Target workstation: Arch-based, `pacman` plus an AUR helper (`paru`/`yay`).

What is needed:

- **JDK 21 (LTS)** — `jdk21-openjdk`. Selection with
  `sudo archlinux-java set java-21-openjdk`.
- **adb / fastboot** — `android-tools`.
- **udev rules** — `android-udev`, so device access works without root.
- **Android SDK** — either through Android Studio's SDK Manager, or headless
  through the `cmdline-tools`.
- **Kotlin and Gradle are not installed separately**: Kotlin arrives as the
  Gradle plugin, Gradle through the wrapper (`gradlew`). A system `gradle` is
  only useful once, to bootstrap the wrapper.
- Runtime dependencies (WorkManager, AndroidX, foreground-service APIs) are
  Gradle libraries, not installs.

**Caveat:** the machine already has **JDK 25** (`jdk25-openjdk`). That is too new
for the Android Gradle Plugin / Gradle combination — pin the build to JDK 21
(via `archlinux-java` and/or `org.gradle.java.home`), or use Android Studio,
whose bundled JBR is JDK 21.

Option A — Android Studio (recommended):

```fish
sudo pacman -S jdk21-openjdk android-tools android-udev
sudo archlinux-java set java-21-openjdk
paru -S android-studio
```

`android-studio` is AUR-only in this setup; JetBrains Toolbox is the equivalent
alternative for self-updating installs. SDK Manager packages:
`platforms;android-36`, `build-tools;36.0.0`, `platform-tools`, `emulator`,
`cmdline-tools;latest`, `system-images;android-36;google_apis;x86_64`.

Option B — headless, driven from VS Code:

```fish
sudo pacman -S jdk21-openjdk android-tools android-udev curl unzip
sudo archlinux-java set java-21-openjdk
```

Then the SDK itself, per user and without root: unpack the official
`commandlinetools-linux-*_latest.zip` into
`$ANDROID_HOME/cmdline-tools/latest`, accept the licenses, install the packages.

```fish
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" \
  "emulator" "system-images;android-36;google_apis;x86_64"
```

`scripts/install-android-toolchain.sh` performs exactly that flow and is
idempotent: repo packages, JDK 21 as default, udev rules plus group membership,
command line tools, licenses, SDK packages, emulator, and `ANDROID_HOME` in
`~/.config/environment.d/`. `--with-studio` adds option A, `--dry-run` only
prints the steps.

VS Code extensions: `vscjava.vscode-java-pack` (language support, debugger),
`vscjava.vscode-gradle` (Gradle tasks and sync), `fwcd.kotlin` (Kotlin support),
optionally `adelphes.android-dev-ext` (logcat and debugging).

Emulator prerequisites: `/dev/kvm` present (VT-x/AMD-V enabled in firmware,
`kvm_amd`/`kvm_intel` loaded) and the user in the `kvm` group. Without it, use a
physical device over USB debugging.

Release signing: `keytool` ships with the JDK — no extra tool for the AAB
release milestone.

## Architecture

- **Kotlin + Jetpack**, min SDK 26+.
- **App widget (primary surface)** — an `AppWidgetProvider` with `RemoteViews`
  (or Jetpack **Glance**), one icon per configured site. Each icon is a
  `RemoteViews` child with its own `setOnClickPendingIntent()` to an
  `ACTION_VIEW` intent; updates go through
  `AppWidgetManager.updateAppWidget()`.
- **Widget configuration** — `android:configure` on the widget points at the
  settings `Activity`, so placing the widget on the home screen opens the URL
  list directly. The configure path returns `RESULT_OK` with the widget id as an
  extra, which also allows per-instance site lists.
- Fetching: **WorkManager periodic work** (hourly; its 15-minute minimum is no
  issue) — it survives reboots and triggers the widget refresh. No long-running
  process needed.
- Cache: app internal storage `filesDir/showfavicon/<host>.png`.
- Graying: render a `Bitmap` through a `ColorMatrix` (saturation 0 plus reduced
  alpha) when the last fetch failed.
- **Optional: foreground service with a persistent notification** — carries the
  status bar presence. Not needed for the widget, and the only part that
  requires `POST_NOTIFICATIONS` and the `FOREGROUND_SERVICE_*` permissions.

## Multi-site

Two widget shapes, fed by the same settings list:

- **One widget with N icons** — a row/grid of `RemoteViews` children, one per
  configured site (recommended: one list drives everything).
- **One widget instance per site** — the instance shows a single icon; the
  configure activity stores which URL that widget id shows.

Layout caveat: keep `minWidth` / `minHeight` small enough that a single 1×1
cell is a legal widget size for the one-icon case.

## Configuration

- A plain settings **Activity**: a list of URLs with add / edit / remove and
  validation, persisted in `SharedPreferences` or DataStore. **No drag-and-drop
  required.**
- Reached twice: as the app's main screen, and as the widget's
  `android:configure` activity when the widget is placed. Reuse the same screen
  for both — the configure path additionally returns the widget id to
  `AppWidgetManager`.
- Optional convenience: a "share from browser → ShowFavicon" intent to append a
  URL without opening the app.
- Permissions: `INTERNET`. Only the optional notification path adds
  `POST_NOTIFICATIONS` runtime permission (Android 13+) and
  `FOREGROUND_SERVICE` plus `FOREGROUND_SERVICE_SPECIAL_USE`/`DATA_SYNC`.
- Note: modern Android renders status-bar small icons as **monochrome** masks —
  the full-color favicon appears in the notification's large icon / expanded
  view. On the home-screen widget full colour works, so the widget is the better
  place for the real favicon.

## Milestones

0. Toolchain installed, empty project builds and runs on device/emulator.
1. Widget skeleton + settings activity: one URL, tap-to-open, icon rendered.
2. WorkManager hourly fetch + PNG cache + widget refresh.
3. Offline grayed icon.
4. Multiple URLs in the settings list, widget with one icon per site, widget
   configure path for per-instance sites.
5. Optional: foreground service + persistent notification.
6. Share-intent add.
7. AAB release build.
