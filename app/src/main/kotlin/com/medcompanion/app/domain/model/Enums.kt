package com.medcompanion.app.domain.model

/** The physical form of a medication. Mirrors AGENTS.md > Data model. */
enum class MedicationForm {
    TABLET,
    CAPSULE,
    SYRUP,
    INJECTION,
    DROPS,
    CREAM,
    OTHER,
}

/** Where a medication entry came from, so future versions can trust clinician data. */
enum class MedicationSource {
    SELF,
    DOCTOR,
    PHARMACY,
}

/** Lifecycle state of a medication. */
enum class MedicationStatus {
    ACTIVE,
    COMPLETED,
    STOPPED,
}

/** How doses are scheduled for a medication. */
enum class ScheduleType {
    DAILY_TIMES,
    EVERY_N_HOURS,
    WEEKDAYS,
    AS_NEEDED,
}

/** Relationship between a dose and food. */
enum class FoodRelation {
    ANY,
    BEFORE,
    WITH,
    AFTER,
}

/** State of a single scheduled dose. */
enum class DoseStatus {
    PENDING,
    TAKEN,
    MISSED,
    SKIPPED,
    SNOOZED,
}

/** A user-entered profile fact. */
enum class ProfileEntryType {
    ALLERGY,
    CONDITION,
}
