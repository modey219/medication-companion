# Key decisions

Short entries. Newest first. Format: ID, date, decision, why, status.

---

## D-012 — Symptoms log (T6)

Date: 2026-10-19
Status: Accepted

Decisions:
1. The quick log is name + severity (1 to 5) + optional note; the recorded
   time is `clock.now()` at the moment of saving and is shown on every entry.
   Backdating a symptom is deferred (the History tab owns the timeline).
2. Severity starts **unchosen** on purpose (`Int?`, save disabled until the
   patient picks a level): the app never assumes how bad a symptom is.
3. Severity labels are words next to the number ("3 - متوسط"), so the screen
   never depends on color alone and reads correctly with TalkBack and at 2.0x
   font scale (five radio rows instead of a chip row that would overflow).
4. Severity levels are described by intensity only. Nothing in the screen says
   what the symptom means, what to do, or whether to stop a medication.
5. A local keyword check (`core/emergency/EmergencySymptoms`) runs on the typed
   name in Arabic and English and shows the emergency card (contact emergency
   services immediately) as soon as a term matches. It is matched after
   normalizing case, spaces and Arabic diacritics/letter variants.
6. The emergency card is **not** triggered by severity 5 alone: the app does
   not triage, and "showing the emergency card to anyone who picks 5" would be
   an invented rule. Severity 5 is simply logged.
7. `emergency_number` is a placeholder (empty, `TODO(clinical-review)`); the
   call button only appears once the team fills it in. Coming from the phone
   it opens the dialer (`ACTION_DIAL`), it never places the call itself.
8. Deleting an entry is allowed (typo or wrong entry) and uses plain, matter of
   fact copy: the log is the patient's, and the app never judges a dose or a
   symptom as failed.

---

## D-011 — Repeat dose reminders until the patient acts (Phase 1)

Date: 2026-10-19
Status: Accepted

Decision: while a dose is still PENDING or SNOOZED and inside its grace
period, the reminder is re-armed every
`ReminderDefaults.REPEAT_INTERVAL_MINUTES` (5 minutes) by
`ReminderEngineImpl.onReminderFired`. The repeat is just another reminder
alarm for the same dose, so Taken/Snooze/Skip from the notification cancels
it through the existing single cancel path, and no repeat is scheduled once
`scheduledAt + grace` has passed.

Why: one notification is easy to miss, especially for the target users;
repeating only inside the grace window avoids nagging after the dose is
MISSED and never adds a second follow-up (the grace alarm still sends at most
one gentle follow-up).

Notes:
- The cadence is fixed for v1; TODO(eng) T8 makes it configurable next to the
  grace period and snooze.
- A refresh (app start, reboot, timezone or clock change) re-arms reminders
  for unacted doses, which also restarts the repeat chain after a reboot.
- The cadence is listed for review in docs/CLINICAL_REVIEW.md.

---

## D-010 — Stock, expiry dates and refill request (Phase 1, F2)

Date: 2026-10-19
Status: Accepted (verified 2026-10-19: 159 tests, 0 failures; lint 0 errors)

Decisions:
1. `medications` gains three nullable columns (**migration 1 -> 2**):
   `expiryDate`, `openedAt`, `remainingDoses`. All are patient-entered and
   optional; `null` means "not provided / not tracking", never an assumption.
2. Both dates are stored at the **start of the chosen calendar day** in the
   user's zone (calendar dates, not intervals, so no half-open convention).
   Device timezone changes can therefore shift them by a day; accepted for v1
   (documented here), same trade-off as the schedule times.
- Status: ONGOING
- TODO(eng) T8: revisiting timezone-stable storage (e.g. store the ISO date
  string) together with profiles and reminder settings.

## D-009 — Hide the medicine name on the lock screen (Phase 1)

Date: 2026-10-19
Status: Accepted (verified 2026-10-19: assembleDebug + ktlintCheck +
testDebugUnitTest + lintDebug pass — 149 tests, 0 failures; lint 0 errors /
9 warnings, all previously accepted categories)

Decision: a privacy toggle on the reminder setup screen (off by default,
stored in SharedPreferences) makes dose notifications post a generic public
version (`VISIBILITY_PRIVATE` + `setPublicVersion`) so the lock screen shows
only "Dose reminder" instead of the medicine name. The expanded shade keeps
the full details for the user.

Why: AGENTS.md keeps all data on the device and treats medical data as
private; a phone on a table should not reveal which medicine the patient
takes.

Notes:
- The generic wording is marked TODO(clinical-review) like every medical
  string.
- The setting is read when each notification is posted, so toggling applies
  to the next reminder without a restart.

---

## D-008 — Dose reminders: exact alarms, generation and catch-up (T5)

Date: 2026-10-18 (revisions 2026-10-19)
Status: Accepted (verified 2026-10-19: assembleDebug + ktlintCheck +
testDebugUnitTest + lintDebug pass — 144 tests, 0 failures; lint 0 errors /
9 warnings, all previously accepted categories)

Decisions:

1. **`AlarmManager.setAlarmClock` with `SCHEDULE_EXACT_ALARM`.** AGENTS.md asks
   to choose between `setAlarmClock` and `setExactAndAllowWhileIdle` and justify
   it. `setAlarmClock` is used because a dose reminder is the canonical
   user-visible alarm: it is exempt from Doze/App Standby deferral, survives
   more OEM battery managers (Xiaomi/Samsung/Huawei/Oppo/Vivo), is allowed to
   fire even when the app is in the restricted bucket, and it shows the user a
   system alarm icon - which is honest for a time-critical reminder. The cost
   is `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`), which is denied by default
   on Android 14+. **Revision 2 (2026-10-19):** while the permission is missing
   the scheduler no longer no-ops; it degrades to
   `AlarmManager.setAndAllowWhileIdle` (inexact wakeup, may fire a few minutes
   late) because a late reminder beats no reminder at all; the setup screen
   still explains how to grant exact alarms and
   `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` triggers a
   `refresh()` that upgrades the inexact alarms back to alarm-clock alarms.
2. **One reminder alarm + one grace alarm per upcoming dose**, for the whole
   generation window (7 days) instead of a short rolling slice. The app is
   offline-only and has no sync, so being able to fire for a week without an app
   start is the safer failure mode. `refresh()` is idempotent and cheap
   (one read per interval), so the window is regenerated on every relevant
   event: app start, screen resume, schedule save, medication stop, reboot,
   timezone/clock change, app update, permission grant.
3. **`DoseGenerator` is pure** (`medications`, `schedules`, `zone`, window) and
   never fabricates a time before `from`. `EVERY_N_HOURS` steps in absolute time
   anchored at local midnight of the start date, so a 6-hour schedule does not
   drift with DST.
4. **Reconcile, do not wipe.** `refresh()` deletes only *future, still pending,
   never-acted* doses that are no longer expected, then inserts with
   `OnConflictStrategy.IGNORE`. Past doses and any dose the patient acted on are
   history and are never deleted - stopping a medication removes only its future
   doses.
5. **Catch-up while the device is off is silent.** Doses older than the grace
   period are marked MISSED with no notification; only the grace alarm path
   notifies (with at most one follow-up), so the "never alarm the patient late"
   rule in AGENTS.md holds. For a reminder whose trigger time passed while the
   device was off, the alarm fires at the next refresh (`maxOf(trigger, now)`)
   as long as the dose is still inside its grace period.
6. **Grace is anchored at the scheduled time, snooze at the action time.**
   Grace = `scheduledAt + 60 min`, snooze = `actedAt + 10 min`, both from
   `ReminderDefaults` (configurable in T8 via DataStore). These anchors are
   fixed here because they are what the tests pin down.
7. **Notification actions work without opening the app**: `Taken`, `Snooze`,
   `Skip` are `PendingIntent.getBroadcast` to `DoseActionReceiver`, which calls
   `ReminderEngine.performAction` and cancels the alarms + notification. A stale
   action on an already-handled dose is applied the same way the Today screen
   applies it (last action wins), because refusing would only confuse the user.
8. **First launch opens the reminder setup screen once**
   (`ReminderSetupNudge`, SharedPreferences flag). AGENTS.md requires
   plain-language explanations before permission dialogs; the Today banner
   alone was easy to miss and left notifications blocked on Android 13+ with
   no alarm scheduled on 14+. Later launches rely on the banner as before.

---

## D-007 — Today screen scope and behaviour (T4)

Date: 2026-10-17
Status: Accepted (build and tests verified locally)

Verification (2026-10-18): `assembleDebug` + `ktlintCheck` + `testDebugUnitTest`
+ `lintDebug` all pass — 75 tests, 0 failures (TodayViewModelTest: 12);
lint 0 errors / 9 warnings (all previously accepted categories plus the same
`ModifierParameter` warning already accepted on MedicationsScreen); APK
13,273,561 bytes. The first verification attempt failed: the in-ViewModel
delay loop kept `runTest` draining the test scheduler after the test body
finished (body itself completed in 86 ms), running for minutes until
`OutOfMemoryError`. Fix: the ticker now arrives through
`@PeriodicTicker Flow<Instant>` (see Decision above); tests drive it with a
controllable `MutableStateFlow`.

Decision:
- **No dose generation in T4.** The rolling-window generator is T5's
  (AGENTS.md brief); the Today screen renders what is in `dose_events`, so on a
  real device it stays empty until T5. Tests seed doses through the repository.
- **No automatic MISSED transitions in T4.** Grace-period handling
  ("no action within 60 minutes → MISSED") is T5's reminder engine. T4 only
  *displays* MISSED rows and *highlights* a PENDING dose whose `scheduledAt`
  is in the past.
- **Window: today only**, `[start of today, start of tomorrow)` in the injected
  clock's zone, recomputed from `now` on every tick so the screen rolls over
  at midnight. Overdue from previous days shows in History (T7), not here.
- **30-second ticker, injected**: the ViewModel combines a
  `@PeriodicTicker Flow<Instant>` (provided by `core/di/TimeTickerModule.kt`,
  emits `clock.now()` immediately then every 30 s) with the Room flows, so
  overdue flags update while the screen is open without any data change (and
  the window re-queries daily). The stream comes from Hilt rather than a
  private delay loop so unit tests substitute a controllable
  `MutableStateFlow` and drive ticks with `.value =`; an autonomous loop on
  the test scheduler keeps `runTest` draining it until the heap is exhausted
  (observed: body finishes in ms, epilogue runs for minutes, then OOM).
- **Snooze in T4 = database only**: `setStatus(SNOOZED, clock.now())` and the
  row shows "snoozed until actedAt + 10 minutes". The constant lives in
  `core/time/SnoozeDefaults.DEFAULT_SNOOZE_MINUTES = 10` so the T5 alarm
  engine reuses it; making it configurable needs DataStore (Profile, T8).
- **Grouping** by exact local time of `scheduledAt`, groups sorted ascending,
  rows sorted by medication name. Times formatted with the platform formatter
  (device 12/24-hour setting).
- **Status labels are text, never color alone**; overdue rows additionally use
  `errorContainer`. Labels are neutral and marked `TODO(clinical-review)`.
- **Actions** go through the single `DoseEventRepository.setStatus` path;
  Room's Flow refreshes the UI automatically. A failed action shows a
  Snackbar (`TodayError.UPDATE_FAILED`) and the row stays actionable.
- Added `ScheduleRepository.observeAll()` (+ DAO query) so rows can show the
  dose amount and food relation; the empty state without any medication offers
  an "Add medication" CTA into the `medications/add` route.

Why:
- Keeps T4 exactly as scoped by the brief and avoids duplicating T5 logic;
  the injectable clock + ticker makes overdue behaviour deterministic in tests
  (AGENTS.md: all time logic through an injectable Clock).

---

## D-006 — Add/edit/stop medication flow (T3)

Date: 2026-10-17
Status: Accepted (build and tests verified locally)

Decision:
- **Injectable `AppClock`** (`core/time/AppClock.kt`, bound to
  `SystemAppClock` via Hilt `@Binds` in `core/di/TimeModule.kt`). The edit
  ViewModel and the stop action read `now()`/`today()`/`zone()` through it so
  tests use `FakeAppClock` (AGENTS.md > Reminders: all time logic goes through
  an injectable Clock).
- **Pure-Kotlin form validation**: `domain/schedule/ScheduleValidation.kt`
  exposes `validateDraft(MedicationDraft): Set<DraftError>`. Rules: non-blank
  name and dose amount; end date not before start; DAILY_TIMES and WEEKDAYS
  need ≥1 time (no duplicates); EVERY_N_HOURS needs an interval in 1..24 and
  ignores times/weekdays; WEEKDAYS needs ≥1 day; AS_NEEDED only checks
  name/dose. All applicable errors are reported together; editing any field
  clears previous errors (revalidated on save).
- **Half-open date convention for medications**: start stored at
  `startDate.atStartOfDay(zone)`, end at `(endDate + 1 day).atStartOfDay(zone)`
  so `[startDate, endDate)` includes the picked last day. On load the end
  instant is read back with `minusSeconds(1)` so the picker shows the original
  chosen date. The stop action stores `clock.now()` as the end instant.
- **Navigation**: `medications/add` and `medications/edit/{medicationId}`
  (`ARG_MEDICATION_ID`, Long) are nested under the Medications tab;
  `topLevelDestinationFromRoute` matches by prefix so the tab stays selected
  and a back arrow replaces the profile action on both routes.
- **One schedule per medication** (first row of `getByMedication`); editing
  updates it in place, creating one only if the medication had none.
- Weekday chips are ordered **SAT→FRI** (Arabic week start); time and date
  formatting use `android.text.format.DateFormat`, honouring the device
  12/24-hour setting (AGENTS.md > Localization).

Why:
- AGENTS.md fixes the schedule types, requires plain-language validation
  without inventing medical facts, and mandates an injectable Clock for testable
  time logic. The half-open interval matches D-005's window convention and
  avoids duplicating the "end day" question in later dose-window code.

Verification (local, JDK 21): `assembleDebug`, `ktlintCheck`,
`testDebugUnitTest` (62 tests, 0 failures) and `lintDebug` (0 errors, 8
accepted warnings) all pass. Lint caught `LocalDate.EPOCH` (API 34 vs minSdk
26) — replaced with `LocalDate.of(1970, 1, 1)`.

---

## D-005 — Room data layer and JVM test setup (T2)

Date: 2026-10-17
Status: Accepted (build and tests verified locally)

Decision:
- One Room database `MedCompanionDatabase` (name `medcompanion.db`, version **1**)
  with entities `MedicationEntity`, `ScheduleEntity`, `DoseEventEntity`,
  `SymptomLogEntity`, `ProfileEntryEntity`, DAOs for each, and a `Converters`
  class for the domain enums and `Instant`/`LocalTime`/`DayOfWeek` values.
- `DoseEventEntity` has a **unique index on `(medicationId, scheduledAt)`** so
  dose generation is idempotent (`@Insert(IGNORE)` returns `-1` for duplicates).
- Schedules and doses reference `medications` with **`ON DELETE CASCADE`**.
- Windowed dose queries (`getBetween` / `observeBetween`) use a **half-open
  interval `[start, end)`** so adjacent windows never double-count a dose.
- `exportSchema = true`; the schema JSON is committed under `app/schemas/`.
- Repositories are plain interfaces in `domain/repository/` bound to `data/`
  implementations (`*RepositoryImpl`) via Hilt `@Binds`; `DatabaseModule`
  provides the database and DAOs. **DataStore (settings) is deferred** to a
  later task (Profile/reminder preferences).
- Room tests run on the JVM with **Robolectric** (`@Config(sdk = [37])`), so they
  run in CI without an emulator.

Why:
- AGENTS.md fixes the data model, requires explicit schemas/migrations, and
  demands idempotent dose generation. The unique index enforces idempotency at
  the storage layer, independent of application logic.

Test JVM (important):
- **Robolectric 4.17 needs Java 21 to simulate SDK 36/37**, while the code still
  compiles to Java 17 bytecode. The build and the unit tests therefore run on
  **JDK 21** (this supersedes the "JDK 17" note in D-001). On JDK 21, Robolectric
  additionally needs OpenJDK internals opened; the unit-test task sets the
  documented `--add-opens=java.base/...=ALL-UNNAMED` flags
  (https://robolectric.org/getting-started/). Pinning tests to `@Config(sdk=[35])`
  to stay on JDK 17 was rejected because it would not exercise the real target.

Verification (local, JDK 21): `assembleDebug`, `ktlintCheck`,
`testDebugUnitTest` (19 tests, 0 failures) and `lintDebug` (0 errors, same 5
accepted warnings as D-001) all pass.

---

## D-001 — Build toolchain versions (T1)

Date: 2026-10-17
Status: Accepted (build verified locally — see D-004)

Decision:
- AGP **9.4.1**, Gradle **9.6.0**, JDK **17**
- Kotlin **2.4.10**, KSP **2.3.12**, Hilt **2.60.1**
- Compose BOM **2026.09.00**
- compileSdk **37.1**, targetSdk **37**, minSdk **26**
- ktlint Gradle plugin **14.2.0**
- AGP **built-in Kotlin is used**: the `org.jetbrains.kotlin.android`
  plugin is NOT applied; `org.jetbrains.kotlin.plugin.compose` is still applied.

Why:
- The current stable AndroidX/Compose releases (Compose 1.12.x, core-ktx 1.19.x,
  navigation-compose 2.10.x, hilt-navigation-compose 1.4.x, lifecycle 2.11.x)
  declare `minCompileSdk = 37` and require **AGP 9.1.0+**; Compose specifically
  requires compileSdk **37.1**. That forces compileSdk 37.1 and a recent AGP.
- **Android 17 is API 37** and, since Android 16, ships as *minor API levels*
  (37.0/37.1). Only the minor platforms are published to the stable channel
  (`platforms;android-37.1`, `platforms;android-37.2`); there is no base
  `android-37`. The compile SDK is therefore pinned with the AGP 9 DSL:
  `compileSdk { version = release(37) { minorApiLevel = 1 } }`.
- targetSdk is **37** (latest stable; AGENTS.md asks for the latest stable
  targetSdk). Play requires targetSdk ≥ 36 from 2026-08-31.
- AGP 9 applies **built-in Kotlin** by default and **rejects** an explicitly
  applied `org.jetbrains.kotlin.android` (it fails with a
  `ClassCastException … AgpWithBuiltInKotlinAppliedCheck`). We therefore rely on
  built-in Kotlin and only apply the Compose compiler and KSP plugins.
- AGP 9.4.1 requires Gradle 9.6.0. Kotlin 2.4.10 is the line paired with AGP 9.4;
  KSP 2.3.12 (latest) is compatible with it.

Remaining lint warnings (accepted, non-blocking): a newer Gradle (9.8.1) and
Kotlin (2.4.21) exist — deliberately pinned to the AGP-9.4-verified line; the
`mipmap-anydpi-v26` `ObsoleteSdkInt` warning is a false positive (the `anydpi`
density qualifier must keep `-v26` even at minSdk 26, AAPT rejects a bare
`mipmap-anydpi`); two strings (`cd_bottom_navigation`,
`notice_not_medical_advice`) are unused until later tasks.

Verification (local, JDK 17 + Android SDK): `assembleDebug`, `ktlintCheck`,
`testDebugUnitTest` and `lintDebug` all pass (0 lint errors).

---

## D-002 — Arabic is the default resource locale (T1)

Date: 2026-10-17
Status: Accepted

Decision: Arabic strings live in `res/values/` (default) and English in
`res/values-en/`. No `resourceConfigurations` filter is set (it was removed
because it is deprecated in AGP 9; the two locales are shipped as-is).

Why: AGENTS.md requires Arabic as the default with full RTL. Putting Arabic in
the default folder means it is used for any locale without a translation.

---

## D-003 — One Gradle module for v1 (T1)

Date: 2026-10-17
Status: Accepted

Decision: a single `:app` module with package layers `core/`, `data/`,
`domain/`, `feature/`, `ui/`. Revisit only if build times or team size demand it.

Why: the v1 scope is small; a single module keeps reviews small and avoids
premature multi-module overhead. The package layout still enforces the
ui/domain/data separation from AGENTS.md.

---

## D-004 — Local build verification environment (T1)

Date: 2026-10-17
Status: Accepted

Decision: T1 was verified by building locally with JDK 17 (Temurin 17.0.20.1)
and Android SDK platform **37.1** + build-tools 36.0.0, using the wrapper's
Gradle 9.6.0. Commands run: `assembleDebug`, `ktlintCheck`,
`testDebugUnitTest`, `lintDebug` — all pass.

Notes for humans:
- **Build outside OneDrive.** The project lives under a OneDrive folder; Gradle
  failed with `Unable to delete directory … app/build/intermediates/…` because
  OneDrive/Defender locks freshly written files. Building from a plain local
  copy (or excluding the `build/` folders from sync) fixes it. This is a
  local-environment issue, not a project defect. CI (Linux) is unaffected.
- **`local.properties` on Windows** should escape the drive colon, e.g.
  `sdk.dir=C\:/path/to/sdk`, otherwise Android Lint raises `PropertyEscape`.
  `local.properties` is git-ignored and machine-specific.
- Manual verification on a physical phone is still pending (install the debug
  APK, check the 5 RTL tabs + profile action, font scale 200%, TalkBack).
