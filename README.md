# Notifnix

**One App. Every Interaction.**

Notifnix is an offline-first Android simulator for creating and previewing clearly labelled test calls, SMS-style messages, and app-style notifications.

> **Safety by design:** Notifnix creates local simulations only. It does not place real calls, send SMS messages, impersonate a real service, or write fake messages into the device's system SMS inbox or call history. Every generated notification and saved scenario is labelled **SIMULATED**.

## Features

- **Call Studio** — build a caller preview and generate sample caller details.
- **Message Studio** — compose and preview SMS-style sample messages.
- **Notification Studio** — post a local Android test notification.
- **Scenario Library** — persist scenarios in a Room database; search, edit, favorite, delete, and filter them.
- **Scheduling** — choose a calendar date and time for a saved simulation notification using WorkManager; Android may defer execution under battery restrictions.
- **Template tools** — add a starter template pack and generate a random notification template.
- **Backup and restore** — export scenario data as JSON through Android's share sheet, then import a JSON backup to merge scenarios into the local library.
- **Dark appearance** — switch between light and dark color palettes.
- **App PIN gate** — optional 4–6 digit local PIN screen. This is a convenience gate, not hardware-backed encryption or a substitute for Android device security.
- **Legacy migration** — existing scenarios saved by the earlier SharedPreferences version are migrated into Room on first launch.
- **Android CI** — GitHub Actions builds a debug APK on pushes, pull requests, and manual dispatch.

## Build

- Kotlin + Jetpack Compose + Material 3
- Room 2.6.1 + KSP
- WorkManager 2.10.0
- Minimum Android: 8.0 (API 26)
- Compile/target SDK: 35
- JDK 17; Gradle 8.9

Open the project root in Android Studio and sync Gradle, or run:

```bash
gradle --no-daemon assembleDebug
```

The GitHub Actions workflow uploads `app-debug.apk` as the `notifnix-debug-apk` artifact when the build succeeds. The APK can be downloaded from the workflow run's **Artifacts** section.

## Release signing

The CI workflow always produces a debug APK. If all four protected GitHub Actions secrets are configured — `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` — it also builds and uploads a signed release APK. Store the Base64-encoded keystore and credentials as repository Actions secrets; never commit keystores or passwords. Without those secrets, the signed-release step is skipped.

## Privacy and platform limits

Notifnix intentionally avoids SMS, call-log, contacts, and default-dialer permissions. Call and message screens are previews inside Notifnix, not system-level incoming calls or real SMS. Scheduled notifications are local and depend on Android notification permission and background execution policy. Scenario data stays in the app's local Room database unless the user explicitly exports it.
