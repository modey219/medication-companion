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
- **Profile**: Allergies and chronic conditions (with severity, reaction and since-year), basic information, medical history, preferences, export and delete data

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3, single activity, Navigation Compose
- **Architecture**: MVVM with unidirectional data flow
- **DI**: Hilt
- **Data**: Room (local), DataStore (preferences)
- **Reminders**: AlarmManager exact alarms
- **Assistant backend**: Cloudflare Worker + TypeScript (`backend/`, provider-agnostic)
- **Testing**: JUnit, MockK, Turbine, Room tests, Robolectric (JVM), Compose UI tests; Vitest for the backend
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

The assistant proxy lives in a separate Node project:

```
backend/
├── prompts/system-prompt.txt   # safety prompt (Appendix A), needs clinical review
├── src/                        # Worker: routes, validation, limits, safety, LLM adapters
└── tests/                      # Vitest
```

## Assistant backend

The Assistant tab never holds an LLM API key. It calls one endpoint on a small
Cloudflare Worker that holds the key, applies the safety system prompt,
rate-limits per install and stores no conversations. See
[`backend/README.md`](backend/README.md) for the endpoint contract, environment
variables, tests and deploy steps, and `docs/DECISIONS.md` D-018 for the rationale.

The app side of that conversation is the Assistant tab (T10, T13): a first-use
consent screen, the chat itself, an urgent-symptom check that runs before anything is sent,
and a "Report a problem" action. Since T13 the assistant has two explicit,
versioned consents - a profile consent (off by default) that attaches a small,
fixed `PatientContext` built on the device, and a media consent stored for T16.
Without the profile consent every question is answered in `GENERIC` mode and
nothing about the patient leaves the phone; with it on but the profile unreadable,
the question is not sent at all (see `docs/DECISIONS.md` D-023).

Since T14 the Worker checks every question twice: a strict JSON **review** pass
decides the urgency (`EMERGENCY`, `URGENT_TODAY`, `SEE_DOCTOR_SOON`,
`SELF_CARE`) and whether the request asks for a treatment change, and only then
an **answer** pass writes the reply, which a last-line guard checks before it is
returned. A review that cannot be parsed fails closed, and an emergency or a
forbidden request never reaches the answer pass (see `docs/DECISIONS.md` D-024
and the three prompt files under `backend/prompts/`).

The base URL is a Gradle property
(`-PassistantBaseUrl=...`), so a local `wrangler dev` or a deployed Worker needs no
code change; the checked-in default is a placeholder and must be replaced before
release (see `docs/DECISIONS.md` D-019).

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

Latest debug APK: app-debug.apk (15.23 MB)

## Links

- Repository: https://github.com/modey219/medication-companion

## Notes

- T1–T7 shipped and verified (foundation, data layer, medications, today, reminders, symptoms, history + PDF export)
- T8a shipped: profile (allergies/conditions), first-run consent + privacy placeholder, language choice, emergency number, export/delete all data
- T8b shipped: patient reminder preferences (grace period, snooze, repeat interval), low-stock level and pharmacy number
- T9 shipped: assistant backend proxy (`backend/`) — one `POST /v1/chat`, provider-agnostic, rate-limited, no logging of content, 153 Vitest tests
- T10 shipped: assistant chat UI — first-use consent with the (off-by-default) share-medication switch, in-memory conversation, client-side emergency card, retry/offline sentences, "Report a problem"
- T12 shipped: health profile — basic information (birth year, sex, pregnancy/breastfeeding, weight), medical history, detail fields on allergies/conditions (severity, reaction, since year), and a derived "how complete is this section" state (Room v3; JSON export `schemaVersion` 2)
- T13 shipped: patient context + two versioned consents — a device-built, capped `PatientContext` sent only with the profile consent, `GENERIC`/`PERSONALIZED` modes on the wire, and an all-or-nothing rule that never sends a personalized question without its context (legacy `shareMedications` still accepted by the Worker)
- T14 shipped: review-then-answer backend — a JSON review pass (urgency, forbidden request, considered/gaps/questions), gates that skip the answer pass for an emergency or a forbidden request, a server-rendered and fenced patient context, an answer guard that blocks medicine/dose instructions and "safe to combine" claims, and a fail-closed invalid review (see docs/DECISIONS.md D-024)
- T15–T18 planned: assistant response cards, media capture, vision path, evaluation harness (see AGENTS.md and docs/DECISIONS.md D-021)
- See docs/DECISIONS.md for key architectural decisions
- See AGENTS.md for full specifications