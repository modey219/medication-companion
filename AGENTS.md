# AGENTS.md - ???? ?????? (Medication Companion) Patient Medication Companion

## Project Overview

???? ?????? (Medication Companion) is an Android app that helps patients take their medication correctly and keep a clear personal medication history. Users are mostly Arabic-speaking adults, including elderly and non-technical people. It is a companion, not a medical device: it organizes what the patient enters, reminds, records, and explains in plain language. It never replaces a doctor or pharmacist.

## Version 1 Scope

Five bottom tabs, plus a profile icon in the top bar:

1. **Today**: today's doses grouped by time; each dose can be marked Taken, Skipped, or Snoozed; overdue doses are highlighted.
2. **My medications**: active, completed, and stopped medications; add, edit, stop.
3. **Symptoms**: quick log with symptom name, severity 1 to 5, optional note, and time.
4. **History**: timeline of medications, doses, and symptoms; adherence summary; export a PDF summary to show a doctor or pharmacist.
5. **Assistant**: AI chat about the patient’s own treatment (see “AI assistant”).

Top bar Profile: allergies and chronic conditions entered by the user, language, reminder preferences, export and delete data.

## Non-goals for v1 (do not build)

- A doctor or pharmacist app, accounts or login, cloud sync, multi-user profiles.
- A drug interaction checker, dose calculator, diagnosis, or any treatment recommendation.
- Ads, analytics or tracking SDKs, in-app purchases.
- iOS, Wear OS, tablet-specific layouts.

## Tech Stack (fixed unless the plan finds a real problem)

- Kotlin, Jetpack Compose with Material 3, single activity, Navigation Compose.
- MVVM with unidirectional data flow; layers ui, domain, data; Hilt for dependency injection; Coroutines and Flow.
- Room for all data (explicit migrations, exported schemas); DataStore for settings.
- Dose reminders use AlarmManager exact alarms. Do not use WorkManager for dose times, because it cannot guarantee timing; use it only for non-time-critical maintenance. Evaluate setAlarmClock versus setExactAndAllowWhileIdle and justify the choice in the plan.
- Gradle Kotlin DSL with a version catalog; latest stable Android Gradle Plugin and Kotlin; minSdk 26; targetSdk the latest stable.
- Tests: JUnit, MockK, Turbine, Room tests, Compose UI tests. Lint: ktlint and Android Lint. CI: GitHub Actions running build, lint, and unit tests on every push.
- Android and Google Play rules change often. Check the current official documentation for permissions and policies instead of relying on memory.

## Data Model (Room)

- **Medication**: id, name, form (tablet, capsule, syrup, injection, drops, cream, other), strength (text), instructions (text), source (SELF, DOCTOR, PHARMACY), startDate, endDate (nullable), status (ACTIVE, COMPLETED, STOPPED), stopReason (nullable text), createdAt.
- **Schedule**: id, medicationId, type (DAILY_TIMES, EVERY_N_HOURS, WEEKDAYS, AS_NEEDED), times, intervalHours, weekdays, doseAmount (text such as “1 tablet”), foodRelation (ANY, BEFORE, WITH, AFTER).
- **DoseEvent**: id, medicationId, scheduledAt, status (PENDING, TAKEN, MISSED, SKIPPED, SNOOZED), actedAt (nullable), note (nullable).
- **SymptomLog**: id, loggedAt, name, severity (1 to 5), note (nullable).
- **ProfileEntry**: id, type (ALLERGY, CONDITION), label, note (nullable).
- Keep source on every medication so a later version can tell patient-entered data from clinician-provided data.
- Generate dose events on a rolling window (for example the next 7 days). Generation must be idempotent: never create duplicates.

## Reminders (the most important feature, build it carefully)

- One exact alarm per upcoming dose. Reschedule after device reboot, app update, timezone or clock change, and any schedule edit.
- Notification actions Taken, Snooze (default 10 minutes, configurable), and Skip must work without opening the app.
- If a dose has no action within a configurable grace period (default 60 minutes), mark it MISSED and send at most one gentle follow-up.
- Permission flows with plain-language explanations: notifications (Android 13 and later), exact alarm access, and battery-optimization exemption. Include a guidance screen explaining how to allow background activity on common manufacturers (Xiaomi, Samsung, Huawei, Oppo, Vivo).
- All time logic goes through an injectable Clock. Tests must cover timezone changes, reboot rescheduling, edits to an active schedule, and doses around midnight.

## Localization and UX

- Arabic is the default with full RTL support; English is secondary. Put Arabic in the default values/ resources and English in values-en/. No hardcoded strings.
- Large, readable type; support system font scaling up to 200%; strong contrast; touch targets of at least 48dp; short plain sentences; TalkBack content descriptions; never rely on color alone.
- Follow the device 12/24-hour setting and digit style; do not force a digit system.
- Tone: calm, respectful, never alarming or judgmental about missed doses.

## Privacy and Security

- All health data stays on the device in v1. The only network traffic is the Assistant tab.
- Turn off automatic cloud backup for app data (allowBackup="false" plus data extraction rules). Offer an explicit export instead.
- Evaluate encrypting the database (for example SQLCipher with a key held in the Android Keystore) and decide in the plan.
- Never log medication names, symptoms, or any personal data. No crash-reporting SDK in v1 unless approved.
- Profile has “Export my data” (JSON and PDF) and “Delete all my data”.
- Required screens: first-run consent, privacy policy (placeholder text tagged TODO(legal)), and a clear “not a substitute for professional medical advice” notice.
- Never commit secrets. API keys live only on the backend. Keep local config files in .gitignore.

## Medical Safety Rules (hard rules for every string, screen, and AI response)

- The app never diagnoses, never recommends starting, stopping, skipping, or changing a medication or dose, and never states that a combination of medicines is safe.
- Do not invent drug facts. If drug information is needed, ask the team for a source file.
- Mark every medical string, explanation, and default value with TODO(clinical-review) and list it in docs/CLINICAL_REVIEW.md.
- For urgent symptoms (chest pain, difficulty breathing, severe allergic reaction, fainting, signs of a stroke, serious bleeding, thoughts of self-harm) show an emergency message with the configurable local emergency number and urge contacting emergency services immediately.
- When unsure, say so and direct the user to a pharmacist or doctor.

## AI Assistant (Assistant tab)

- The Android app must never contain an LLM API key. It calls a small backend proxy in /backend that holds the key, applies the safety system prompt (Appendix A), rate-limits per install, and neither stores conversations nor logs message content.
- Backend: the smallest viable stack (propose options in the plan, for example a serverless function). Provider-agnostic through environment variables (LLM_PROVIDER, LLM_MODEL, LLM_API_KEY). One POST /v1/chat endpoint with input validation, size limits, timeouts, and structured errors; streaming is optional.
- Abuse and cost protection: per-install rate limits and daily caps; verify that requests come from the genuine app (for example with Play Integrity) and plan how.
- Client: chat UI with message bubbles, typing indicator, retry, clear conversation, and a persistent one-line disclaimer. A first-use consent screen states exactly what is sent: the typed messages, plus medication names and schedules only if the user turns on “Share my medication list with the assistant” (off by default).
- A client-side emergency keyword check (Arabic and English) runs before sending and shows the emergency card immediately, in addition to the server-side rules.
- Every assistant message has a “Report a problem” action that opens the share sheet with a prefilled template; no message content leaves the device without the user’s tap.
- Clear offline state; never crash on network errors.

## Code Conventions

- Small commits (Conventional Commits), one feature per branch.
- Every feature ships with tests; reminder and schedule logic needs thorough tests. Compose previews for each screen in Arabic (RTL) and English, at 1.0x and 2.0x font scale.
- Every TODO carries an owner tag: TODO(eng), TODO(clinical-review), TODO(legal).
- Immutable UI state; no global singletons outside Hilt.
- Record key decisions in docs/DECISIONS.md (short entries).

## Definition of Done (every task)

Builds cleanly; tests and lint pass; verified on an emulator, with instructions for the humans to verify on a physical phone; strings localized (Arabic and English); accessibility checked; no personal data in logs; a short summary of what changed and what the humans must review.

## How the Agent Must Work

- Plan first. For anything touching more than about five files, show the plan and wait for approval.
- Ask when a requirement is ambiguous or when a medical or legal decision is needed. Do not guess.
- Do not add features, dependencies, or permissions that are not in this file without asking.
- After each change, run the build, lint, and tests and report the results honestly, including failures you could not fix.
- Never claim something works unless you ran it.

## Placeholders

- ???? ?????? (Medication Companion): (to be filled in)
- package name: com.medcompanion.app (to be filled in)
- Local emergency number: (to be filled in)
