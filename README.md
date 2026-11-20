# Task Manager for Android

A task manager for Android phones, modeled on the desktop task manager layout. Written in Kotlin with Jetpack Compose.

## Features

| Tab | What it shows |
| --- | --- |
| **Processes** | Every installed app, grouped as *Apps*, *Background processes* and *System processes*, with live status (Running, Running in background, Recently used, Idle, Stopped), screen time today, data used today and storage size. Sortable columns, heat-map cells, search. Tap an app to **End task**, open it, open App info or uninstall it. |
| **Performance** | Live 60-second graphs for **CPU** (overall and per core, with clock speeds), **Memory** (usage, composition, swap), **Storage**, **Network** (receive/send throughput), and **Battery** (current draw, temperature, voltage, health). |
| **History** | Screen time, mobile data and Wi-Fi data per app for today, 7 days or 30 days. |
| **Startup** | Apps that launch when the phone boots, and whether they will. |
| **Details** | Device, software, processor, memory and display details, plus live up time. |

Use the ⋮ menu to change the real-time update speed (High / Normal / Low / Paused) or show system apps.

## Install

Download the latest APK from the [Releases](../../releases) page and open it on your phone. Requires Android 8.0 (API 26) or newer.

On first launch, tap **Grant usage access** and turn it on for Task Manager. Android only shares running-app state, screen time, data usage and app sizes with apps the user has explicitly allowed.

## What Android allows (and doesn't)

Android sandboxes apps far more tightly than a desktop OS, so a few things work differently:

- **CPU load.** Android 8+ blocks apps from reading `/proc/stat`. Where it is blocked, CPU load is estimated from each core's current clock speed, and the app labels it as an estimate.
- **End task.** On Android 13 and older, End task kills the app's background processes. On Android 14+, the system only allows apps to kill their own processes, so End task opens the app's system page where you can tap **Force stop**.
- **Per-app memory and CPU.** These are not available to normal apps on modern Android, so the Processes tab shows screen time, data and storage instead.
- **Startup apps.** Apps can't disable each other's boot receivers without root. The Startup tab shows which apps are registered and whether they will start. A force-stopped app won't auto-start until you open it again.

## Build

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

GitHub Actions builds an APK on every push (see the workflow run's artifacts). A GitHub release is published when you:

- push a tag like `v1.2.0`, or
- push a commit whose message contains `[release]` (uses the version in `VERSION`), or
- run the **Build APK** workflow manually.

### Signing

Release builds are signed with `app/signing/taskmanager.jks`. This key is deliberately public, so any build can be installed as an update over the previous one. Before publishing to an app store, replace it with a private key stored in CI secrets.
