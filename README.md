# Medication Companion (رفيق الدواء)

Android application for medication management, reminders, and tracking symptoms. Built with Kotlin, Jetpack Compose, Material 3, and Room.

## Project Overview

Medication Companion is an Android app that helps patients take their medication correctly and keep a clear personal medication history. It is a companion, not a medical device. It never replaces a doctor or pharmacist.

## Features

- **Today**: View today's doses grouped by time with actions (Taken, Skipped, Snoozed)
- **My medications**: Manage active, completed, and stopped medications
- **Symptoms**: Quick log of symptoms with severity and notes
- **History**: Timeline of medications, doses, and symptoms with adherence summary; PDF export
- **Assistant**: AI chat about the patient's own treatment (backend-protected)
- **Profile**: Allergies, chronic conditions, preferences, export and delete data

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3, single activity, Navigation Compose
- **Architecture**: MVVM with unidirectional data flow
- **DI**: Hilt
- **Data**: Room (local), DataStore (preferences)
- **Reminders**: AlarmManager exact alarms
- **Testing**: JUnit, MockK, Turbine, Room tests, Robolectric (JVM), Compose UI tests
- **Lint**: ktlint, Android Lint

## Requirements

- **Min SDK**: 26
- **Target SDK**: 37
- **Compile SDK**: 37.1 (minor API level)
- **JDK**: 21 for tests/build
- **Gradle**: 9.6.0
- **AGP**: 9.4.1

## Build

### Local development

1. Ensure Android SDK is installed with platforms android-36, android-37.1, android-37.2
2. Set local.properties with sdk.dir=C\:/path/to/sdk
3. Build and test from a local (non-OneDrive) directory

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew ktlintCheck
```

### Verification (all checks)
```bash
./gradlew assembleDebug ktlintCheck testDebugUnitTest lintDebug
```

## Project Structure

```
app/src/main/kotlin/com/medcompanion/app/
├── core/           # DI, time, common utilities
├── data/           # Room entities, DAOs, mappers, repositories
├── domain/         # Models, repository interfaces
├── feature/        # Feature screens (today, medications, symptoms, history, assistant, profile)
├── ui/             # Theme, navigation, shared components
└── ...
```

## Privacy & Safety

## Privacy & Safety

- All health data stays on-device (except Assistant backend)
- Automatic cloud backup is disabled (`allowBackup="false"` + extraction rules); the patient can export instead
- Profile offers "Export my data" (JSON and PDF) and "Delete all my data"
- The database is not encrypted in v1 (see docs/DECISIONS.md D-016)
- No analytics, ads, or tracking
- Strong focus on medical safety: never diagnoses, never recommends changes
- Emergency keyword detection
- Arabic-first with full RTL support, with an in-app language choice (system / Arabic / English)

## Git

- Branches: master, feat/t1-foundation, feat/t2-data-layer, feat/t3-medications
- Conventional Commits used
- Auto-push via post-commit hook

## APK

Latest debug APK: app-debug.apk (14.42 MB)

## Links

- Repository: https://github.com/modey219/medication-companion

## Notes

- T1–T7 shipped and verified (foundation, data layer, medications, today, reminders, symptoms, history + PDF export)
- T8a in progress: profile (allergies/conditions), first-run consent + privacy placeholder, language choice, emergency number, export/delete all data
- T8b next: reminder preferences (grace period, snooze, repeat interval, low-stock, pharmacy number)
- See docs/DECISIONS.md for key architectural decisions
- See AGENTS.md for full specifications