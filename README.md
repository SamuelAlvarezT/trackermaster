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

Data stored locally in SQLite. Biometric lock optional. Backup is opt-in.
