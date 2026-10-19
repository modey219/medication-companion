# AGENTS.md - ???? ?????? (Medication Companion) Patient Medication Companion

## Project Overview

???? ?????? (Medication Companion) is an Android app that helps patients take their medication correctly and keep a clear personal medication history. Users are mostly Arabic-speaking adults, including elderly and non-technical people. It is a companion, not a medical device: it organizes what the patient enters, reminds, records, and explains in plain language. It never replaces a doctor or pharmacist.

## Version 1 Scope

Five bottom tabs, plus a profile icon in the top bar:

1. **Today**: today's doses grouped by time; each dose can be marked Taken, Skipped, or Snoozed; overdue doses are highlighted.
2. **My medications**: active, completed, and stopped medications; add, edit, stop.
3. **Symptoms**: quick log with symptom name, severity 1 to 5, optional note, and time.
4. **History**: timeline of medications, doses, and symptoms; adherence summary; export a PDF summary to show a doctor or pharmacist.
5. **Assistant**: AI chat about the patient’s own treatment (see “AI assistant”). It reviews the patient’s own profile before answering, and accepts photos, or short videos reduced to frames.

Top bar Profile: patient basics, allergies and chronic conditions with severity, medical history, the per-section “not asked / none confirmed / listed” status, language, reminder preferences, consent records, export and delete data.

## Non-goals for v1 (do not build)

- A doctor or pharmacist app, accounts or login, cloud sync, multi-user profiles.
- A drug interaction checker (needs a licensed database), a dose calculator, a definitive diagnosis, prescribing or dosing, audio analysis, cloud storage of media, sending media to a clinician, a child or pediatric mode, medicine-box OCR.
- Ads, analytics or tracking SDKs, in-app purchases.
- iOS, Wear OS, tablet-specific layouts.

## Tech Stack (fixed unless the plan finds a real problem)

- Kotlin, Jetpack Compose with Material 3, single activity, Navigation Compose.
- MVVM with unidirectional data flow; layers ui, domain, data; Hilt for dependency injection; Coroutines and Flow.
- Room for all data (explicit migrations, exported schemas); DataStore for settings.
- Dose reminders use AlarmManager exact alarms. Do not use WorkManager for dose times, because it cannot guarantee timing; use it only for non-time-critical maintenance. Evaluate setAlarmClock versus setExactAndAllowWhileIdle and justify the choice in the plan.
- Gradle Kotlin DSL with a version catalog; latest stable Android Gradle Plugin and Kotlin; minSdk 26; targetSdk the latest stable.
- Tests: JUnit, MockK, Turbine, Room tests, Compose UI tests. Lint: ktlint and Android Lint. CI: GitHub Actions running build, lint, and unit tests on every push. The backend also ships a regression corpus (30 or more JSON cases) and an evaluation harness that prints per-case results for human review; under-triage is reported separately and never silently passes. The team supplies a labelled test-image folder: the agent never generates or downloads medical images.
- Android and Google Play rules change often. Check the current official documentation for permissions and policies instead of relying on memory.

## Data Model (Room)

- **Medication**: id, name, form (tablet, capsule, syrup, injection, drops, cream, other), strength (text), instructions (text), source (SELF, DOCTOR, PHARMACY), startDate, endDate (nullable), status (ACTIVE, COMPLETED, STOPPED), stopReason (nullable text), createdAt.
- **Schedule**: id, medicationId, type (DAILY_TIMES, EVERY_N_HOURS, WEEKDAYS, AS_NEEDED), times, intervalHours, weekdays, doseAmount (text such as “1 tablet”), foodRelation (ANY, BEFORE, WITH, AFTER).
- **DoseEvent**: id, medicationId, scheduledAt, status (PENDING, TAKEN, MISSED, SKIPPED, SNOOZED), actedAt (nullable), note (nullable).
- **SymptomLog**: id, loggedAt, name, severity (1 to 5), note (nullable).
- **ProfileEntry**: id, type (ALLERGY, CONDITION), label, severity (nullable 1 to 3), reaction (nullable text), sinceYear (nullable), note (nullable).
- **PatientBasics**: birthYear, sex (nullable), pregnantOrBreastfeeding (nullable), weightKg (nullable). One row only. Store the birth year, never a full date of birth.
- **MedicalHistoryEntry**: id, type (PAST_ILLNESS, SURGERY, HOSPITALIZATION, FAMILY_HISTORY, OTHER), label, year (nullable), note (nullable).
- **SectionStatus**: section (ALLERGIES, CONDITIONS, HISTORY), status (NOT_ASKED, NONE_CONFIRMED, LISTED), confirmedAt (nullable). The medications status is derived from the medication table and is not stored. An empty list is not the same as “none”: absence of data is never evidence of absence.
- **SymptomMedia**: id, symptomLogId, thumbnailPath, createdAt. The thumbnail stays in app-private storage and is excluded from backup.
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

- Health data lives on the device. The only network traffic is the Assistant tab: the typed messages, the minimized patient context, and photos or video frames, each only with its matching consent. The provider must not retain or train on the data; that requirement is part of the environment configuration and is documented in backend/README.md.
- Turn off automatic cloud backup for app data (allowBackup="false" plus data extraction rules). Offer an explicit export instead.
- Evaluate encrypting the database (for example SQLCipher with a key held in the Android Keystore) and decide in the plan.
- Never log medication names, symptoms, or any personal data. No crash-reporting SDK in v1 unless approved.
- Profile has “Export my data” (JSON and PDF), the consent records, and “Delete all my data”, which also removes saved media and the consent records.
- Required screens: first-run consent, privacy policy (placeholder text tagged TODO(legal)), and a clear “not a substitute for professional medical advice” notice.
- Never commit secrets. API keys live only on the backend. Keep local config files in .gitignore.

## Medical Safety Rules (hard rules for every string, screen, and AI response)

The assistant personalizes its guidance from the patient’s own profile. It may:
- give possible explanations with explicit uncertainty, never as a diagnosis;
- state an urgency level;
- suggest safe self-care steps that involve no medicines;
- draw cautions from the patient’s allergies, conditions, medical history, and current medications;
- suggest questions to ask a doctor or pharmacist.

It must never:
- prescribe or recommend a specific medicine;
- tell the patient to start, stop, skip, or change a medicine or a dose;
- give dose amounts;
- say that a medicine, food, herb, or supplement is safe to combine with their medicines;
- present a test result or an image as a diagnosis;
- identify people in images.

- Urgency is one of EMERGENCY, URGENT_TODAY, SEE_DOCTOR_SOON, SELF_CARE. When two levels are possible, choose the higher one.
- Never state or imply that the patient has no allergies or conditions unless that section’s status is NONE_CONFIRMED. Absence of data is never evidence of absence.
- Emergency guidance is never delayed, shortened, or replaced by a request for more data.
- The app reviews the profile before answering. Missing data that matters for the question is asked for, never guessed.
- A photo or a video frame is evidence about appearance only: it cannot show movement, sound, or the inside of the body.
- Do not invent drug facts. If drug information is needed, ask the team for a source file.
- Mark every medical string, explanation, and default value with TODO(clinical-review) and list it in docs/CLINICAL_REVIEW.md.
- For urgent symptoms (chest pain, difficulty breathing, severe allergic reaction, fainting, signs of a stroke, serious bleeding, thoughts of self-harm) show an emergency message with the configurable local emergency number and urge contacting emergency services immediately.
- When unsure, say so and direct the user to a pharmacist or doctor.

## AI Assistant (Assistant tab)

- The Android app must never contain an LLM API key. It calls a small backend proxy in /backend that holds the key, applies the safety system prompt (Appendix A), rate-limits per install, and neither stores conversations nor logs message content.
- Backend: the smallest viable stack (propose options in the plan, for example a serverless function). Provider-agnostic through environment variables (LLM_PROVIDER, LLM_MODEL, LLM_API_KEY). One POST /v1/chat endpoint with input validation, size limits, timeouts, and structured errors; streaming is optional.
- Abuse and cost protection: per-install rate limits and daily caps; verify that requests come from the genuine app (for example with Play Integrity) and plan how.
- Client: chat UI with message bubbles, typing indicator, retry, clear conversation, and a persistent one-line disclaimer. Two separate, versioned, revocable consents are stored with their version and time: (a) personalize with my health profile, (b) send photos or videos for analysis. Both are off by default. Without (a) the app runs in “generic mode”, shows a clear “not personalized” banner, and the assistant gives general information only, with no medication-specific guidance.
- Review before answer: sending is disabled until the patient context is built on the device. POST /v1/chat requires a valid context or mode="generic", otherwise HTTP 400. The reply must begin with contextReview (what in the profile is relevant to this question and why) and dataGaps (sections still NOT_ASKED); when a gap matters for the question, the assistant asks the patient to complete that section (deep link to Profile) instead of answering.
- Structured reply, validated on the server: contextReview, dataGaps, urgency, summary, personalizedConsiderations, possibleExplanations, whatYouCanDoNow, redFlags, questionsForClinician, followUpQuestions. On a validation failure the server retries once with a corrective instruction and then shows a safe fallback message. Cards show urgency as an icon plus text, never color alone, with a collapsible “What I considered” list.
- Media: JPEG only with magic-byte verification, at most 4 photos or one video reduced to at most 8 evenly spaced frames, held in memory only, never logged or persisted. A vision-capable model is selected by a capability flag behind the same provider-agnostic interface, and the provider must not retain or train on the data.
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

Builds cleanly; tests and lint pass; verified on an emulator, with instructions for the humans to verify on a physical phone; strings localized (Arabic and English); accessibility checked; no personal data in logs; a short summary of what changed and what the humans must review. Anything that uses the camera, the photo picker, or the microphone is verified on a physical phone, because emulators are not reliable for them.

## How the Agent Must Work

- Plan first. For anything touching more than about five files, show the plan and wait for approval.
- Ask when a requirement is ambiguous or when a medical or legal decision is needed. Do not guess.
- Do not add features, dependencies, or permissions that are not in this file without asking.
- After each change, run the build, lint, and tests and report the results honestly, including failures you could not fix.
- Never claim something works unless you ran it.

## Placeholders

- ???? ?????? (Medication Companion): (to be filled in)
- package name: com.medcompanion.app (to be filled in)
- Local emergency number: (to be filled in). Until it is set the code falls back to 112, and every string that uses it carries TODO(clinical-review).
