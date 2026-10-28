# Trackermaster

Android life dashboard app — habits, mood, expenses, focus timer, and journal in one offline-first app.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Hilt, Room, DataStore
- Glance App Widgets
- minSdk 26, targetSdk 35

## Modules

- `app` — navigation, onboarding, biometric lock
- `core:domain` — business rules (streaks, schedules)
- `core:database` — Room entities
- `core:data` — repositories
- `core:ui` — theme (Light/Dark/OLED, 20 accents)
- `core:widgets` — Glance home screen widgets
- `feature:*` — habits, mood, expense, focus, journal, insights, settings, backup

## Build

```bash
./gradlew assembleDebug
```

Open in Android Studio Hedgehog+ with Android SDK 35.

## Features (V1)

- 5 trackers with unlimited entries (no paywall in v1)
- Habit progress types, schedules, streaks, heatmap, reminders
- Mood logging, calendar, habit correlation
- Multi-account expenses, budgets, reports
- Pomodoro focus timer with foreground notification
- Journal with HTML entries and local backup ZIP
- Google Drive backup scaffold (`BackupManager`)
- 6 Glance widgets (+ dashboard combo)
- Languages: English, Spanish, Chinese

## Privacy

Data stored locally in SQLite. Biometric lock optional. Backup and cloud sync are opt-in.

## PC dashboard and optional cloud sync

Android can publish records to Firebase Spark and a read-only React dashboard without connecting the phone to the PC. Follow [the setup guide](docs/cloud-sync/SETUP.md). Cloud sync is disabled until your Firebase project, owner account and private Android writer key are configured. Photos and file attachments remain local.

Run the dashboard from `web` with `npm ci` and `npm run dev`. Publish its static build with Firebase Hosting after configuring authentication, rules and indexes.
