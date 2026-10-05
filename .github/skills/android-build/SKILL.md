---
name: android-build
description: 'Build, lint and install the ShowFavicon Android app from the command line: JDK 21, the committed Gradle wrapper, AGP 9 built-in Kotlin, compileSdk minor versions, and the per-user SDK in ~/Android/Sdk. USE FOR: running ./gradlew assembleDebug, lintDebug, installDebug or bundleRelease; fixing "the org.jetbrains.kotlin.android plugin is no longer required since AGP 9.0"; fixing an AAR metadata error that demands a newer compileSdk; installing SDK packages when the package id carries a minor version (platforms;android-37.2); picking the JDK for the build in fish; finding the APK and the lint report. DO NOT USE FOR: writing or refactoring app code and UI, Play Console publishing, CI on other machines, or KDE Plasma work.'
argument-hint: 'What should the build do?'
---

# Building the ShowFavicon Android app

A single module Gradle project. Nothing about it is unusual except that it is
built headless from the terminal, with AGP 9 — which removed the Kotlin plugin
and made API versions fractional.

Provenance: measured on CachyOS, 2026-10-05. AGP 9.4.1, Gradle 9.8.0, JDK
21.0.12, Kotlin compiled by AGP, compileSdk 37.2, build-tools 36.0.0, minSdk 26,
SDK at `/home/martin/Android/Sdk`. Every error quoted below was produced by this
project and fixed here, not copied from a blog.

## When to use

- Building, linting or installing this app.
- A build fails with a message about the Kotlin plugin, the compile SDK level or
  a missing SDK package.
- Setting the project up on a machine where only `scripts/install-android-toolchain.sh`
  has run.

## The toolchain contract

| Piece | Value | Notes |
| --- | --- | --- |
| JDK | `/usr/lib/jvm/java-21-openjdk` | AGP 9.4 needs ≥ 17. JDK 25 is installed on this machine and is **not** supported by this AGP/Gradle pair — always pass the JDK explicitly. |
| Gradle | 9.8.0, via `./gradlew` | Wrapper is committed (`gradlew`, `gradle-wrapper.jar`). AGP 9.4 needs ≥ 9.6.0. |
| Kotlin | brought by AGP | Built-in Kotlin since AGP 9. No `org.jetbrains.kotlin.android` plugin, no KGP version in the catalog. |
| SDK | `sdk.dir` in `local.properties` | Gitignored. `ANDROID_HOME` works as well. `local.properties` wins. |
| compileSdk | 37.2 | `compileSdk = 37` **and** `compileSdkMinor = 2`. AndroidX decides this, see trap 2. |
| build-tools | 36.0.0 | The default for AGP 9.4. Do not pin `buildToolsVersion`. |

`local.properties` and the wrapper jar are the two files that make the build
reproducible; both are easy to lose in a fresh clone.

## Commands

fish cannot do `VAR=value cmd`, so wrap the two in `env`:

```fish
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew lintDebug
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew installDebug     # needs a device
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew bundleRelease    # AAB
```

Gradle output is long and the terminal panel wraps it, which makes error text
unreadable. Redirect to a file and read the tail:

```fish
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug --console=plain > /tmp/build.log 2>&1
echo "exit: $status"
tail -60 /tmp/build.log
```

In VS Code the task **Android: build debug APK** (`.vscode/tasks.json`, on
Ctrl+Shift+B) runs the same command and pins the same `JAVA_HOME`. The tasks
**Android: run on emulator** and **Android: stop emulator** call
`scripts/emulator-run.sh`.

### The emulator

```fish
./scripts/emulator-run.sh          # AVD anlegen, booten, installDebug, App starten
./scripts/emulator-run.sh --stop   # adb emu kill
```

The script starts the emulator **detached** (`setsid nohup … &`), so it survives
the script and its task shell — which also means closing the window is not the
only way to stop it, and not always a working one. `adb emu kill` is the
reliable shutdown; `adb devices` then has to become empty, and `pgrep -c qemu-system`
has to reach zero.

Screenshots of the *device*, unaffected by window scaling or the host desktop,
come from `adb exec-out screencap -p > /tmp/emu.png`. `adb shell wm size` reports
the pixel dimensions that `adb shell input tap X Y` expects.

## Where things land

| Artefact | Path |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release AAB | `app/build/outputs/bundle/release/` |
| Lint report | `app/build/reports/lint-results-debug.html` (and `.sarif`) |
| Problems report | `build/reports/problems/problems-report.html` |

## Traps

### 1. The Kotlin plugin is gone in AGP 9

```
Failed to apply plugin 'org.jetbrains.kotlin.android'.
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
```

Cause: AGP 9 enables built-in Kotlin by default and the `kotlin-android` plugin
is not compatible with its new DSL.

Fix: remove `alias(libs.plugins.kotlin.android)` from the module **and** the root
build file, and the plugin entry from `gradle/libs.versions.toml`. Nothing
replaces it: AGP compiles the Kotlin sources and adds the stdlib itself. The
`kotlin { compilerOptions { jvmTarget = ... } }` block goes away too, because
`jvmTarget` now defaults to `android.compileOptions.targetCompatibility` — set
`compileOptions` to Java 17 and leave Kotlin alone.

### 2. AndroidX demands a newer compileSdk

```
Dependency 'androidx.core:core-ktx:1.19.1' requires libraries and applications that
depend on it to compile against version 37 or later of the Android APIs.
:app is currently compiled against android-36.
```

Cause: raising the AndroidX versions raises the compile SDK they require. Lint
reports it during `checkDebugAarMetadata`, before anything is compiled, so a
"clean" Kotlin source tree still fails here.

Fix: compile against the level the newest dependency asks for:

```kotlin
android {
    compileSdk = 37
    compileSdkMinor = 2   // minor versions are a separate property
    defaultConfig { targetSdk = 37 }
}
```

Do **not** try to fix this by downgrading a single library: the next AndroidX
artefact fails the same check instead. Install the matching platform first, that
is trap 3.

### 3. Platform package ids carry a minor version

```
$ sdkmanager --install "platforms;android-37"
Package platforms/android-37 not found.
```

Cause: Android ships minor SDK releases and the package id includes them. The
listing hides this twice over: `--list` prints the new slash form
(`platforms/android-37.2`) while `--install` still wants the legacy semicolon id,
and the terminal wraps the long lines so the minor part is easy to miss.

Fix: read the exact ids from a saved listing, then install the full version:

```fish
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --list > /tmp/sdklist.log 2>&1
grep -oE "platforms/android-[0-9]+(\.[0-9]+)?" /tmp/sdklist.log | sort -uV
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --install "platforms;android-37.2"
```

Note: `sdkmanager` prints a deprecation warning and points at the `android`
binary in the same `bin/` directory (`android sdk` is the announced
replacement). It still works; prefer it until the replacement is documented
better.

### 4. The JDK, and fish

`./gradlew` without `JAVA_HOME` uses whatever `java` is first on `PATH`. After
`archlinux-java set java-21-openjdk` that happens to be right, but a machine with
only JDK 25 gives confusing failures inside AGP. Pass `env JAVA_HOME=...` every
time; it costs nothing and removes the variable.

In fish, `||` is not valid, `; or` is, and `VAR=x cmd` is not valid, `env VAR=x cmd`
is.

### 5. A renamed resource folder can come back from the build cache

```
ERROR: app/src/main/AndroidManifest.xml: AAPT: error: resource mipmap/ic_launcher not found.
```

Cause: `org.gradle.caching=true` is set, and after
`res/mipmap-anydpi-v26/` was moved to `res/mipmap-anydpi/` the resource merge was
served from the build cache — the file contents had not changed, so the cached
result was reused and the manifest pointed at a resource that was not in it.
`clean` does not help, because it does not empty the cache: the log showed
`19 from cache` and the link still failed.

Fix: run once with `--no-build-cache` and the same sources link fine. Distrust
"resource not found" right after moving or renaming `res/` folders, and do not
undo a correct folder layout because of it — here the bare `mipmap-anydpi`
folder was valid all along, and lint's `ObsoleteSdkInt` asks for exactly that
name once `minSdk` is 26.

## What green looks like

```
BUILD SUCCESSFUL
34 actionable tasks: 22 executed, 12 up-to-date
```

`compileDebugKotlin` must appear in the task list; if the build stops at
`checkDebugAarMetadata` or `processDebugManifest`, no Kotlin has been compiled
yet and a "the code compiles" conclusion would be wrong.

`lintDebug` currently finishes with **0 findings** and exit code 0. Its first run
here reported 18, all in this project's own code, before they were fixed:
`UseKtx`, `ContentDescription`, `UnusedAttribute`, `MonochromeLauncherIcon`,
`ObsoleteSdkInt`, `RedundantLabel`, `NotifyDataSetChanged`. A reappearance means
a regression, and none of them need a toolchain change to fix.

The `Deprecated Gradle features were used in this build` notice comes from AGP
calling `Configuration.setVisible` while configuring `:app`; `--warning-mode all`
prints that exact message. It is not caused by a line in this project's build
files, and nothing in the project can fix it.

## Files that control the build

| File | Change it for |
| --- | --- |
| `app/build.gradle.kts` | compileSdk / compileSdkMinor, minSdk, targetSdk, dependencies, Java level |
| `gradle/libs.versions.toml` | every library and plugin version, one place |
| `gradle.properties` | JVM args, an `org.gradle.java.home` pin |
| `local.properties` | the SDK path, machine specific, never committed |
| `gradle/wrapper/gradle-wrapper.properties` | the Gradle version AGP must satisfy |
| `scripts/install-android-toolchain.sh` | how a fresh machine gets the SDK and JDK |
| `.vscode/tasks.json` | the build task, and the `JAVA_HOME` it pins |

## Deliberately not bundled

No template copies of `build.gradle.kts` or the manifest live in this skill: those
files are the source of truth and a copy would drift within a release cycle. Read
them in place. There are also no device tests and no CI here — verification means
`assembleDebug` plus `lintDebug`.
