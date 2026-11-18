# Notifnix

**One App. Every Interaction.**

Notifnix is an offline-first Android simulator for creating and previewing clearly labelled test calls, SMS conversations, and app-style notifications. It is intended for UI testing, demos, training, and personal testing.

> **Safety by design:** Notifnix creates local simulations only. It does not place real calls, send SMS messages, impersonate a real service, or write fake messages into the device's system SMS inbox/call history. Every generated item is labelled **SIMULATED**.

## Planned modules

- **Call Studio** — create an incoming-call scenario with caller name, number, avatar initials, ringtone/vibration choice, and optional local schedule.
- **Message Studio** — build SMS-style conversations, choose sender and message text, and preview a realistic but clearly marked conversation.
- **Notification Studio** — create local test notifications with app label, title, body, icon initials, and channel.
- **Scenario Library** — save, edit, duplicate, and delete reusable scenarios locally.
- **History** — review and clear generated simulations.
- **Privacy** — offline-first storage; no account, analytics, or remote API required.

## Android implementation

The project is being built with Kotlin, Jetpack Compose, Material 3, and Android's local notification APIs. Local data is stored on-device. Notification permission is requested only when the user chooses to test a notification. Full-screen incoming-call style experiences are subject to Android version, notification, and full-screen-intent restrictions; the app must not bypass those platform controls.

## Build

Open the project in Android Studio (JDK 17 recommended), allow Gradle sync, then run the `app` configuration on an Android device or emulator. Minimum SDK: Android 8.0 (API 26). Target SDK is kept current with the Android Gradle Plugin configuration.

## Project status

Initial repository setup. Features will be delivered incrementally and verified with Android builds before release.