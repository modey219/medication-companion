# Key decisions

Short entries. Newest first. Format: ID, date, decision, why, status.

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
