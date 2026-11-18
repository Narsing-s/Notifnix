# Notifnix

**One App. Every Interaction.**

Notifnix is an offline-first Android simulator for creating and previewing clearly labelled test calls, SMS-style messages, and app-style notifications.

> **Safety by design:** Notifnix creates local simulations only. It does not place real calls, send SMS messages, impersonate a real service, or write fake messages into the device's system SMS inbox or call history. Every generated notification and saved scenario is labelled **SIMULATED**.

## Features

- **Call Studio** — build a caller preview and generate sample caller details; the separate dialer handoff accepts international E.164 numbers (`+` + country calling code + subscriber number).
- **Message Studio** — compose and preview SMS-style sample messages; the separate SMS-app handoff accepts international E.164 destinations.
- **Notification Studio** — post a local Android test notification.
- **Scenario Library** — persist scenarios in a Room database; search, edit, favorite, delete, and filter them.
- **Scheduling** — choose a calendar date and time for a saved simulation notification using WorkManager; Android may defer execution under battery restrictions.
- **Schedule manager** — view pending and completed scheduled simulations, cancel pending work, or reschedule from the original saved scenario. Scheduling a scenario again replaces its previous pending schedule.
- **Insights dashboard** — local counts for saved scenarios, simulated SMS folders, scheduled work states, scenario-type breakdowns, and recent activity. All metrics are calculated on-device and are never uploaded.
- **Template tools** — add a starter template pack and generate a random notification template.
- **Backup and restore** — export scenario data as JSON through Android's share sheet, then import a JSON backup to merge scenarios into the local library.
- **Dark appearance** — switch between light and dark color palettes.
- **App PIN gate** — optional 4–6 digit PIN stored as a salted PBKDF2 hash, with automatic lock when the app leaves the foreground. This is a convenience gate, not hardware-backed encryption or a substitute for Android device security.
- **Biometric unlock** — optional strong biometric authentication where supported by the device, with PIN fallback. Enable it in Settings after configuring a PIN and enrolling a supported biometric in Android settings.
- **Privacy hardening** — Android automatic backup is disabled; users control when scenario data leaves the device through explicit JSON export.
- **Unit tests** — CI verifies PIN hashing, verification, unique salts, and malformed-record rejection before building the APK.
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

Notifnix intentionally avoids SMS, call-log, contacts, and default-dialer permissions. Call and message screens remain local previews. The optional **Open dialer** and **Open SMS app to send** actions hand an E.164 international destination (`+` followed by country calling code and number) to the user's installed phone app; the user must confirm the call or send. Actual reachability, international access, sender identity, and charges are controlled by the user's carrier and plan. This is not a cloud/global telecom gateway and does not spoof sender IDs. Scheduled notifications are local and depend on Android notification permission and background execution policy. Scenario data stays in the app's local Room database unless the user explicitly exports it.


## Optional global SMS and voice backend (Twilio)

Notifnix includes optional Vercel Node.js endpoints for provider-backed international SMS and outbound voice calls. Deploy the repository to Vercel with the project root set to the repository root. Add these environment variables in Vercel Project Settings → Environment Variables (never commit real values):

- `NOTIFNIX_API_TOKEN`: a long random private token that you generate and enter in Notifnix Settings.
- `TWILIO_ACCOUNT_SID`: your Twilio Account SID.
- `TWILIO_AUTH_TOKEN`: your Twilio Auth Token.
- `TWILIO_FROM_NUMBER`: a Twilio phone number or verified outgoing caller ID in E.164 format. Required for voice and for SMS if no Messaging Service is configured.
- `TWILIO_MESSAGING_SERVICE_SID`: optional Messaging Service SID (starts with `MG`); if set, it is used for SMS sender selection instead of `TWILIO_FROM_NUMBER`.

Redeploy after setting the variables. Open `https://YOUR-VERCEL-DOMAIN/api/health` to check configuration, then in Notifnix → Settings → Global communications enter the HTTPS backend URL, paste the API token, and test the backend. The token is held in memory by the app and must be entered again after restarting it.

The app sends explicit consent flags with every provider request. Use the cloud-call button only for recipients you are authorized to call, and the cloud-SMS button only when the recipient has agreed to receive the message. Twilio may require verified destination numbers on trial accounts, enabled destination-country geo permissions, approved sender registration, and sufficient account balance. International rates and country-specific restrictions apply. Twilio's API acceptance status is not proof that a call connected or an SMS was delivered; check the provider console/status callbacks for final outcomes. Do not use this feature for unsolicited or bulk messaging.

This integration does not spoof caller ID. The voice caller ID must be a Twilio number or an outgoing caller ID verified on your Twilio account. It does not include a free global telecom service; provider usage can be charged.


### Provider status checks

After a cloud SMS or call is accepted, the matching Studio screen shows a status refresh action. It queries Twilio using the returned provider SID. SMS states such as queued, sent, delivered, undelivered, or failed and call states such as queued, ringing, in-progress, completed, busy, or no-answer are provider-reported; they may change as carrier routing progresses. A completed call means the call ended, not necessarily that a person answered. Status availability and final delivery depend on Twilio and destination carriers.
