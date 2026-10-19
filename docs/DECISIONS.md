# Key decisions

Short entries. Newest first. Format: ID, date, decision, why, status.

---

## D-024 — Review, then answer, on the backend (T14)

Decision: every chat request now goes through two model passes — a strict JSON
**review** and, only if the review allows it, an **answer** — and the server, not
the model, owns the gates between them.

1. **Why two passes**: T13 started sending the patient's own profile, so the
   backend must decide *before* it writes anything whether the request is safe
   and how urgent it is. Asking a second pass for a strict JSON object gives a
   structured, testable decision (`urgency`, `forbiddenRequest`) that cannot be
   smuggled into prose, and it lets the server stop before paying for an answer
   it would only throw away. The system prompt still carries the rules; the
   review is a second, narrower reader of the same rules.
2. **Fail closed**: a review that is not a JSON object with a known `urgency`
   and a boolean `forbiddenRequest` is never guessed at. The request returns
   `502 llm_invalid_review` and no answer is attempted. A malformed safety
   decision is treated as a safety failure, not as "probably fine".
3. **Gate order** (cheapest and most certain first): local emergency keyword
   check → review → `urgency === EMERGENCY` returns the emergency card →
   `forbiddenRequest === true` returns the calm decline → answer → answer
   guard. When a gate fires, the answer pass is skipped entirely, so a refusal
   can never be softened by a second model.
4. **The server owns the schema**: the model fills in fields, the server
   validates them. Bullets are capped (5 considered, 5 gaps, 3 questions),
   trimmed, and dropped when they are too long or when `isUnsafeText` says they
   contain an instruction. The review's free text never reaches the patient
   unfiltered, and `usesProfile` never reaches the prompt.
5. **The answer guard is a net, not a proof**: `guardAnswer` flags leaked prompt
   markers, affirmative medicine/dose instructions and "safe to combine" claims,
   with negation handling so "do not stop your medicine without asking" passes.
   It is deliberately small and heuristic; a blocked answer is replaced by
   `safeBlockedReply()` at HTTP 200 (the request succeeded, the content did not)
   and logged as `chat.answer_blocked` with the reason only.
6. **The profile is rendered on the server, from typed fields**: the raw JSON
   the app sent is never pasted into a prompt. `renderPatientContext` builds
   short lines, flattens every value to one line (so a value cannot forge a
   section), writes `NOT_ASKED` / `NONE_CONFIRMED` explicitly, says "unknown"
   for sections the request omitted, and caps the whole block at
   `MAX_CONTEXT_CHARS` with an explicit truncation marker.
7. **Wire compatibility is one-way and additive**: `mode` present means the
   structured `context` is authoritative and the legacy `shareMedications` /
   `medicationContext` fields are ignored (T13 sends both, so rejecting them
   would break installed APKs). `mode` absent is the legacy shape and is read
   as `generic`. `generic` **with** a context is a 400. The response grows
   `urgency` and `review`; T13's parser ignores unknown keys, so no Android
   change ships with T14.
8. **Prompt files are the clinical artifact**: `prompts/system-prompt.txt` gains
   the "what you may do" / profile / urgency sections, and
   `prompts/review-prompt.txt` and `prompts/answer-prompt.txt` are new. All three
   are owned by the clinical reviewer (TODO(clinical-review, T14)); changing a
   pattern in `answerGuard.ts` is equally a clinical change.

---

## D-023 — The patient context and two consents (T13)

Decision: the assistant has two explicit consents and, when the profile consent
is on, sends a small, fixed, structured `context` built on the device. The
context is the only thing about the patient that ever leaves the phone, and it
never leaves without consent.

1. **Two versioned consents instead of one flag**: `assistant_consent_completed`
   (the first-use explanation) stays, and the old
   `shareMedicationsWithAssistant` boolean is replaced by two `ConsentRecord`s —
   `assistantProfileConsent` (send the profile context) and
   `assistantMediaConsent` (the T16 media path) — each storing the consent
   `version` and the `acceptedAt` instant. A consent is granted only when its
   version is current, so raising `CURRENT_VERSION` re-asks. The media consent is
   stored now but only read in T16.
2. **`mode` plus `context` on the wire, legacy fields kept**: `AssistantRequest`
   gains `mode` (`GENERIC`/`PERSONALIZED`) and `context` (`PatientContext?`), and
   keeps `shareMedications`/`medicationContext`. The generic mode sends neither
   the context nor the medication block. The legacy fields stay so an APK built
   today still works against the **already deployed** T9 Worker, whose
   `validation.ts` ignores unknown keys; T14 will read `mode`/`context` and treat
   the legacy shape as generic. Marked `TODO(eng, T14)` (D-018, D-021).
3. **All-or-nothing personalization**: when the profile consent is on but the
   context cannot be read, the question is **not sent at all** — the input is
   kept, `contextFailed` is shown, and no bubble is added. Answering
   generically while the patient believes the assistant has their profile is
   exactly the confusion AGENTS.md forbids. A failure to read the legacy
   medication block alone is not fatal, because the structured context still
   reaches the model.
4. **The context is fixed and small**: `PatientContextBuilder` reads only the
   repositories and caps everything — 20 allergies/conditions, 20 history
   entries, 30 active medications, 10 symptoms (14 days), adherence over 14 days,
   and medications stopped within 30 days — with active medications and
   recent symptoms sorted deterministically. The section status
   (`NOT_ASKED`/`NONE_CONFIRMED`/`LISTED`) travels with the data so the model can
   tell "we never asked" from "the patient has none" (D-022 > 4).
5. **No new permission, no new storage**: building the context reads existing
   Room tables synchronously in the ViewModel coroutine; nothing is cached and
   nothing is written.
6. **TODO(clinical-review)**: the two consent wordings, the "not personalized"
   banner, and the "could not read the profile" sentence; **TODO(eng)**: remove
   the legacy medication fields once T14 is deployed everywhere.

---

## D-022 — The health profile data model (T12)

Decision: the profile grows from allergies and conditions into a small, complete
health profile stored in Room v3: basic facts, medical history, and a derived
"how complete is this section" state. Only what the assistant context needs is
collected, and nothing is ever inferred from missing data.

1. **Basic facts, minimal by design**: `patient_basics` is a single row
   (`id = 1`) with the birth *year* (never a full date of birth), the sex,
   an optional pregnancy/breastfeeding flag, and the weight. The birth year is
   enough to compute an age for the assistant, and storing a full date of birth
   would be more identifying for no gain.
2. **Medical history as its own table**: `medical_history_entries` holds past
   illnesses, surgeries, hospitalizations and family history, each with an
   optional year and note. They are separate from `profile_entries` because they
   are read as a list (a timeline), not as one allergy-versus-condition pair.
3. **Detail fields on allergies and conditions**: `profile_entries` gains three
   nullable columns — `severity` (1 mild to 3 severe), `reaction` (what
   happened, in the patient's words) and `sinceYear`. They are nullable so every
   row written before v3 keeps working untouched.
4. **A section is "not asked" until the patient answers**: `profile_section_states`
   stores *only* the patient's own `NONE_CONFIRMED` confirmation, with the time
   it was given. `LISTED` is derived from the rows that exist, and the
   medications section is derived from the medication table, so neither is
   persisted. `ProfileSectionState.resolve` lets entries always win over an
   earlier "none", and an empty list with `NOT_ASKED` means "we never asked" —
   never "there is nothing there" (AGENTS.md > Medical Safety Rules). This is
   what the T13 context builder and the T15 "data gaps" card read.
5. **Export grows with it**: the JSON export moves to `schemaVersion` 2 and adds
   `basics`, `medicalHistory` and `profileSections`; profile entries gain
   `severity`, `reaction` and `sinceYear`; the PDF summary adds a basic-facts
   section and a medical-history section. The migration 2 -> 3 is covered by
   tests that run the real exported Room schemas.
6. **TODO(clinical-review)**: the birth-year and weight ranges, the 1 to 3
   severity scale and its labels, and every new Arabic wording.

## D-021 — Personalized guidance, profile review and media (T12–T18)

Decision: the assistant may personalize its answers from the patient’s own
profile and look at photos or a short video reduced to frames, under the new
“Medical Safety Rules” in AGENTS.md. Allowed: possible explanations with
explicit uncertainty, an urgency level, safe self-care steps that involve no
medicines, cautions drawn from the profile, and questions for a clinician. Still
forbidden: prescribing, recommending a specific medicine, telling the patient to
start, stop, skip or change a medicine or dose, giving dose amounts, saying
anything is safe to combine, treating a test result or an image as a diagnosis,
and identifying people in images.

1. **Review before answer, enforced in code and not only in the prompt**: the
   client cannot send until the patient context is built, and `POST /v1/chat`
   needs a valid context or `mode="generic"` (HTTP 400 otherwise). The reply
   starts with `contextReview` and `dataGaps`, so the patient sees what was
   considered and what is missing. A section is `NOT_ASKED` until the patient
   either lists items or confirms “none”, and absence of data is never treated
   as evidence of absence.
2. **Two consents replace the single “share my medication list” toggle**:
   (a) personalize with my health profile, (b) send photos or videos for
   analysis. Both are versioned, timestamped, revocable and off by default.
   They are separate because media is the most sensitive thing the app can send.
3. **A deterministic layer stays around the model**: the emergency keyword check
   is unchanged, a normalized allergy-versus-medicine-name match pre-flags the
   question, and a post-filter rejects dose amounts, start/stop instructions,
   “you have <disease>” claims and “safe to combine” statements. A rejected
   reply is regenerated once with a corrective instruction, then replaced by a
   safe fallback. Violations are counted without any content.
4. **Media is processed on the device before it leaves**: re-encoded as JPEG
   (which strips EXIF and GPS), longest side 1600 px, under 1 MB, at most four
   photos or one video reduced to eight evenly spaced frames, held in memory on
   the server only, never logged or persisted. The provider must not retain or
   train on the data; that is an environment-level requirement.
5. **Status**: T12–T18 are planned and this entry records the rule change they
   depend on. The emergency number is still the 112 fallback, so every string
   that uses it carries TODO(clinical-review), and the deployed Worker is still
   pending, so T14 and T17 can only be verified end to end after it is finished.

## D-020 — Backend identity and the CI typecheck fix (T9/T10 follow-up)

Decision: the Worker, its npm package and the Cloudflare project all use the
single name `medication-companion`, the Cloudflare root directory is `backend`,
and `@types/node` becomes a dev-only dependency of the backend so `npm ci` +
`npm run typecheck` pass on a clean machine.

1. **Name and root directory**: Workers Builds requires the `name` in
   `backend/wrangler.toml` to equal the Worker name in the dashboard and the
   configured root directory to be the folder that holds that file. It was
   `medcompanion-assistant` at the repository root, which failed the deploy step
   ("Missing entry-point" / name mismatch). The deployed URL is therefore
   `https://medication-companion.<subdomain>.workers.dev`.
2. **`@types/node` is a build-only tool**: `vitest.config.ts` reads
   `prompts/system-prompt.txt` with `node:fs` so the tests load the same prompt
   the Worker ships. Vite and Vitest list `@types/node` as an *optional peer*, so
   a clean `npm ci` never installs it and `tsc` failed with TS2591. Pinning
   `@types/node@^22.12.0` (the CI Node line) and adding `"node"` to `types` in
   `tsconfig.json` fixes it. It is a devDependency: nothing ships to the Worker,
   and mixing the Node globals with `@cloudflare/workers-types` is safe here
   because `skipLibCheck` is on.
3. **Status**: verified locally with a clean `npm ci` → `npm run typecheck` →
   `npm test` (85 passing) → `npm run build`. The Android side is untouched by
   both changes.

## D-019 — Assistant chat UI: no new dependencies, nothing kept (T10)

Decision: the Assistant tab is a plain Jetpack Compose screen that talks to the
T9 proxy with `HttpURLConnection` and the platform's `org.json`. No HTTP client,
serialization or chat library was added, and the conversation is never stored.

1. **Transport**: one `POST /v1/chat` with a JSON body. `HttpURLConnection` on
   `Dispatchers.IO` (10 s connect, 20 s read) is enough for one request, and
   `org.json` is already used by the JSON export. Adding OkHttp or
   kotlinx.serialization for a single call would be a dependency the app does not
   need; the wire shape lives in one tested object (`AssistantPayloads`).
2. **No key, no account**: the app sends only the typed messages and an opaque
   random install id (`X-Install-Id`, 32 hex characters, kept in DataStore). The
   base URL comes from `BuildConfig.ASSISTANT_BASE_URL`, filled from the
   `assistantBaseUrl` Gradle property, so pointing at `wrangler dev`, staging or
   production needs no code change. The default placeholder must be replaced
   before release.
3. **Conversation in memory only**: the messages live in the `ViewModel` and die
   with the process. "Clear conversation" empties the list; nothing is written to
   Room, DataStore or a file, so "delete all my data" has nothing to delete here.
4. **Consent and sharing**: a first-use consent screen states exactly what is
   sent. "Share my medication list with the assistant" sits there, is **off by
   default**, and can be changed later from "Privacy and sharing" in the chat.
   The medication block is built only when it is on, and a failure to read the
   list never blocks the question. Deleting all data clears both flags, so the
   patient is asked again.
5. **Defence in depth on emergencies**: the client runs the same
   `EmergencySymptoms` check as the Symptoms tab before anything is sent; while
   it matches, `canSend` is false and the emergency card is shown instead. A
   server `emergency: true` reply renders the same card rather than model text.
6. **`INTERNET` permission**: the Assistant tab is the only network traffic in
   v1, so the manifest carries the permission with a comment saying exactly that.
7. **Report a problem**: the share sheet opens with a prefilled template that
   includes the answer only because the patient tapped that message; nothing is
   sent until they choose an app.

Why: AGENTS.md asks for a chat that never holds a key, never stores
conversations, is clear about what leaves the device, and cannot crash on a
network error. This keeps the app dependency-free beyond what it already uses
and puts every safety decision in a place a test can reach.

Status: shipped in T10 (300 app tests). Play Integrity client-side token is still
future work; the backend verifier is ready but disabled (D-018).

---

## D-018 — Assistant backend: one Cloudflare Worker, provider-agnostic (T9)

Decision: the Assistant tab calls a single small proxy in `backend/`, deployed as a
Cloudflare Worker (TypeScript). The Android app never holds an LLM API key.

1. **Stack**: Cloudflare Workers + TypeScript, bundled with Wrangler. It is the
   smallest viable option: nothing to patch, a free tier that covers v1 volume,
   platform-managed secrets, and `vitest` tests that need no device or emulator.
2. **Provider-agnostic**: one `POST /v1/chat` endpoint. `LLM_PROVIDER`,
   `LLM_MODEL`, `LLM_API_KEY` and `LLM_BASE_URL` choose the provider. v1 ships an
   OpenAI-compatible adapter plus a deterministic `mock` (the default in tests and
   local development), so adding another provider means one new adapter and no
   change to the route.
2. **Safety prompt loaded from a file**: Appendix A of AGENTS.md is kept verbatim
   in `backend/prompts/system-prompt.txt` so the medical reviewer can diff it. The
   server also re-runs the urgent-symptom check on the last user message and
   short-circuits to a calm emergency reply without calling the model; the app runs
   the same check before sending (T6, T10), so this is defence in depth.
4. **Abuse and cost protection**: 10 requests/minute and 50/day per install, plus a
   global ceiling of 2000/day, counted in Workers KV (with an in-memory fallback
   when no KV binding is configured). The app sends an opaque random
   `X-Install-Id`. Play Integrity verification is implemented but **off by
   default** (`PLAY_INTEGRITY_MODE=off`); when set to `enforce` the proxy decodes
   the token through the Play Integrity API and checks the package name and a
   request hash bound to the install id, which blunts replay across installs.
5. **No content, no identifiers in logs**: only metadata is logged (event, a short
   SHA-256 tag of the install id, status, counts, duration, provider, model).
   Conversations are never stored, and message text is never logged.
6. **Secrets only on the server**: `LLM_API_KEY` and the Play Integrity service
   account are Worker secrets (`wrangler secret put`). `backend/.env.example`
   contains placeholders only and `.env` is git-ignored.

Why: AGENTS.md asks for the smallest viable backend that holds the key, applies the
safety prompt, rate-limits per install and stores nothing, and for the abuse plan to
be justified. A single Worker with a KV counter meets that without a database or a
container to operate.

Status: shipped in T9 with 85 backend tests; Play Integrity verification is present
but disabled until the Google Cloud project and the app-side token (T10) exist.

---

## D-017 — Patient-editable reminder and stock preferences (T8b)

Decision: the patient can change five values, all stored in the same DataStore as
the other settings:

- **Grace period** (default 60 min, 15–180 in steps of 15): how long after the
  dose time the dose still counts as unacted before it becomes MISSED.
- **Snooze length** (default 10 min, 5–60 in steps of 5): what the notification's
  Snooze action adds to the dose time.
- **Repeat cadence** (default 5 min, 5–30 in steps of 5): the gap between repeated
  reminders while the dose is unacted and still inside the grace period.
- **Low-stock level** (default 3 doses, 1–10): the remaining-dose count at which
  an active medication shows the low-stock hint.
- **Pharmacy number** (free text, optional): where the WhatsApp refill request goes;
  empty means the patient picks the contact each time.

1. Values are entered with bounded **± steppers** (`ui/components/ValueStepper.kt`),
   never typed. The ranges are narrow and reviewed (ReminderPreferences), so a dose
   can never be silently marked MISSED because of an accidental keystroke.
2. Clamping happens in the repository on every write (`ReminderPreferences.clamp*`),
   so a value read back from disk is always usable even if it was written by an
   older version.
3. `ReminderEngineImpl` reads the settings on every pass instead of caching them:
   the engine already re-reads the clock on every pass, so alarms can never fire
   on a value the patient has since changed.
4. Changing the grace, snooze, or repeat value refreshes the engine in the same step
   (`ReminderSetupViewModel.updatePreferences`), because those three decide when the
   already-scheduled alarms fire.
5. The reminder preferences live on the reminder setup screen (they are reminder
   behaviour); the low-stock level and the pharmacy number live in the Profile tab
   next to the other patient facts.

Why: AGENTS.md asks for a configurable grace period and snooze, and gives the app a
low-stock hint and a pharmacy refill request. Putting them in the settings keeps the
same single source of truth as the language and the emergency number, and the stepper
removes the failure mode of a typed value. The defaults and ranges are provisional
until clinical review (see CLINICAL_REVIEW.md).

Status: implemented; defaults and ranges pending clinical review.

---

## D-014 — Patient settings and "delete all my data" (T8a)

Date: 2026-10-19
Status: Accepted

Decision:
1. Patient settings live in a **DataStore (Preferences)** named `settings`
   behind `domain/settings/SettingsRepository`, exposed as an immutable
   `AppSettings(language, emergencyNumber, onboardingCompleted)`. Room stays the
   store for everything the patient records; settings are not health entries.
2. `DataMaintenance.deleteAllData()` is the single "delete all my data" path. It
   (a) reads every dose id and cancels its alarm and notification, (b) calls
   `clearAllTables()` on the database, (c) clears the settings **except the
   first-run consent record** (a record of consent is not a health entry), and
   (d) resets the app language to the device language.
3. Order matters: the reminders are cancelled *before* the rows disappear,
   because once a dose row is gone nothing knows which alarms belonged to it.
4. "Export my data" collects everything through one `FullDataCollector`, so the
   JSON and the PDF can never disagree, and writes it to `cacheDir/exports/`
   (shared through this app's FileProvider). The JSON uses ISO-8601 instants and
   stable enum names on purpose: the file is read by machines and by someone
   moving their data elsewhere, so it must not depend on the phone's locale or
   digit system.
5. The emergency number lives in the settings (Profile tab) and falls back to
   the built-in `R.string.emergency_number` placeholder when it is empty.

Why: preferences are small, need no schema migration and are read on startup;
keeping them out of Room also means "delete all my data" cannot forget a table.
The consent record surviving the wipe is deliberate: the patient consented once,
and wiping data must not silently re-ask or look like a fresh install.

---

## D-015 — Choosing the app language with AppCompat per-app locales (T8a)

Date: 2026-10-19
Status: Accepted

Decision: the language is chosen with AppCompat's per-app locales
(`AppCompatDelegate.setApplicationLocales`) through
`core/locale/AppCompatLanguageApplier`, and `MainActivity` extends
`androidx.appcompat.app.AppCompatActivity`. The two app themes
(`values/themes.xml`, `values-night/themes.xml`) inherit from
`Theme.AppCompat.*` instead of `android:Theme.Material.*`. The `SYSTEM` choice
is applied as an **empty** locale list, which means "follow the device".

Why: Android 13+ has per-app languages in the framework, but the app must also
work on API 26 to 32, where there is no framework equivalent. AppCompat backports
the same behaviour to every supported version and remembers the choice itself,
so nothing has to be re-applied on each start. The settings repository still
keeps the choice as the app's own record (and that is what the JSON export
writes), and "delete all my data" resets both the record and the applied locale
to the device language.

Consequences: a new dependency (`androidx.appcompat` 1.8.0) and the theme parent
change; the activity is recreated when the language changes, which is intended.
The ViewModels persist the language **before** applying it, because applying it
recreates the activity and cancels their scope.

---

## D-016 — No database encryption in v1 (T8a)

Date: 2026-10-19
Status: Accepted

Decision: the Room database is **not** encrypted in v1 (no SQLCipher). What
protects the data instead: `allowBackup="false"` with data-extraction rules (no
cloud backup), all health data staying on the device, no analytics or
crash-reporting SDK, and no medication or symptom names in any log. Adding
SQLCipher later is an additive change (open the database with a key held in the
Android Keystore); it changes neither the schema nor the domain layer.

Why: AGENTS.md asks to *evaluate* encryption, not to ship it. SQLCipher would add
a native dependency, a passphrase path on every I/O and a real risk of data loss
when the key is lost, for a protection that only matters if the device is
unlocked and rooted or the files are extracted with developer access. v1 keeps
the smaller, reviewable surface and records the trade-off here. Revisit for v2
together with the "should the app ask for a device passcode or biometrics"
question.

---

## D-013 — History timeline, adherence summary and PDF export (T7)

Date: 2026-10-19
Status: Accepted

Decisions:
1. The window is "today plus the previous days, starting at local midnight",
   read from `AppClock`: 7 or 30 days, and `HistoryPeriod` is the only place
   the day counts live.
2. The Arabic period labels avoid digits (`آخر أسبوع` / `آخر شهر`) because a
   literal digit in a resource would force a digit system on the patient;
   English says "Last week" / "Last month" (AGENTS.md > Localization and UX:
   follow the device digit style, do not force one).
3. The timeline merges doses, symptoms and medications that started in the
   window, newest first, grouped by local day. A dose whose medication is no
   longer known is left out of the timeline (there is nothing to name) but
   still counts in the summary, so the export never prints a nameless line.
4. The summary counts taken, missed, skipped and "not recorded yet" (PENDING
   and SNOOZED). The percentage is taken/acted and is `null` until something
   was acted on: no percentage out of nothing. Every line is a count with
   neutral wording; the summary is a record, never a grade.
5. The PDF uses the platform `android.graphics.pdf.PdfDocument` (A4, 595x842)
   with `StaticLayout` and an RTL/LTR text direction, so Arabic is shaped and
   wrapped correctly and long reports paginate. No new dependency, no server,
   no key.
6. The timeline and the export are built from the same snapshot, so the file
   and the screen can never disagree.
7. The file is written to `cacheDir/exports` and offered through a
   `FileProvider` Uri (`${applicationId}.fileprovider`) to the single app the
   patient picks in the share sheet; nothing is uploaded and the app keeps no
   other copy. The line under the button says the file stays on the device
   until an app is chosen.
8. Export failures (file write, or no app able to take the file) show one
   plain retry message, never a crash, and never leave a stale share Uri
   behind.
9. `HistoryEntry`/`HistoryUiState` are feature-local; the exporter lives in
   `data/export` and reuses the existing label helpers
   (`DoseStatus.labelRes`, `severityLabelRes`, and `MedicationStatus.labelRes`
   added here) so no user-visible string is duplicated.

Notes:
- The medicine names and symptoms in the file come from what the patient
  entered; the app adds no interpretation
  (`TODO(clinical-review)`: the summary wording, the export disclaimer and
  the PDF footer, listed in `docs/CLINICAL_REVIEW.md`).
- `TODO(eng, T8)`: the period lengths become configurable with the other
  reminder settings.

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
