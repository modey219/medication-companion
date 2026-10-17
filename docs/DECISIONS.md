# Key decisions

Short entries. Newest first. Format: ID, date, decision, why, status.

---

## D-001 — Build toolchain versions (T1)

Date: 2026-10-17
Status: Accepted (verify on first Gradle sync)

Decision:
- AGP **9.0.0**, Gradle **9.1.0**, JDK **17**
- Kotlin **2.3.21**, KSP **2.3.12**, Hilt **2.60.1**
- Compose BOM **2026.09.00**, compileSdk/targetSdk **36**, minSdk **26**
- ktlint Gradle plugin **14.2.0**
- AGP built-in Kotlin is disabled (`android.builtInKotlin=false`); the classic
  `org.jetbrains.kotlin.android` plugin is applied explicitly.

Why:
- The Hilt Gradle plugin (Dagger 2.59+) requires AGP 9.0.0 or newer.
- KSP 2.3.12 is built against Kotlin 2.3.20, so the KSP-supported Kotlin line is
  2.3.x. Kotlin 2.3.20–2.3.21 supports AGP up to 9.0.0, and AGP 9.0 requires
  Gradle 9.1.0 — this pins the whole toolchain consistently.
- Compose compiler comes from the Kotlin Compose plugin at the same Kotlin
  version, avoiding a separate compiler-extension version.
- Built-in Kotlin (AGP 9 default) is new; opting out keeps Kotlin/KSP/Hilt/
  Compose versions explicit and predictable.

Note: all versions above were read from Google Maven, Maven Central and the
Gradle Plugin Portal. The build could not be executed in the authoring
environment (no JDK/Android SDK). Run a Gradle sync and `./gradlew assembleDebug`
to confirm; adjust only this catalog if a version is unavailable.

---

## D-002 — Arabic is the default resource locale (T1)

Date: 2026-10-17
Status: Accepted

Decision: Arabic strings live in `res/values/` (default) and English in
`res/values-en/`. `resourceConfigurations = ["ar", "en"]`.

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
