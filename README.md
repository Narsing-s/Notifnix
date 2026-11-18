# Notifnix

**One App. Every Interaction.**

Notifnix is an offline-first Android simulator for creating and previewing clearly labelled test calls, SMS-style messages, and app-style notifications.

> **Safety by design:** Notifnix creates local simulations only. It does not place real calls, send SMS messages, impersonate a real service, or write fake messages into the device's system SMS inbox or call history. Every saved scenario is labelled **SIMULATED**.

## Current MVP

- **Call Studio** — enter a caller name and number, then preview a simulated incoming-call screen inside the app.
- **Message Studio** — compose and preview an SMS-style sample message.
- **Notification Studio** — create a local Android test notification.
- **Scenario Library** — save and delete scenarios locally on the device.
- **Permission-aware notifications** — requests Android 13+ notification permission when a test notification is requested.
- **Offline-first** — no account, analytics, backend, or API key required.

## Technology

- Kotlin
- Jetpack Compose + Material 3
- Android notification channels and AndroidX notification compatibility
- SharedPreferences for local scenario storage
- Minimum Android version: 8.0 (API 26)
- Compile/target SDK: 35
- JDK 17 recommended

## Open and run

1. Clone this repository.
2. Open the project root in Android Studio.
3. Allow Gradle sync to complete.
4. Run the app configuration on an Android device or emulator.
5. To test notifications on Android 13+, grant the notification permission when prompted.

The repository currently contains the Android source and Gradle project configuration. A generated Gradle wrapper and a verified APK/release build have not yet been added.

## Planned next

- Scheduled scenarios and a scenario editor
- More caller/message/notification templates
- Configurable notification app labels and categories
- Optional ringtone and vibration preview controls
- Export/import of scenario presets

## Privacy and platform limits

Notifnix intentionally avoids SMS, call-log, contacts, and default-dialer permissions. Android restricts full-screen intents and telecom integration; this MVP previews calls inside the app instead of pretending to place a real phone call or bypassing system restrictions.
