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

/**
 * Sex as the patient reports it. Only used to shape the assistant's context and
 * the profile wording, never to draw a medical conclusion.
 *
 * TODO(clinical-review): the wording of the three options.
 */
enum class Sex {
    FEMALE,
    MALE,
    OTHER,
}

/** A fact from the patient's past, used as context for the assistant. */
enum class MedicalHistoryType {
    PAST_ILLNESS,
    SURGERY,
    HOSPITALIZATION,
    FAMILY_HISTORY,
    OTHER,
}

/**
 * A section of the health profile. [MEDICATIONS] is derived from the medication
 * table and is never stored: an empty profile section is not the same as "none".
 */
enum class ProfileSection {
    ALLERGIES,
    CONDITIONS,
    HISTORY,
    MEDICATIONS,
}

/**
 * How complete a profile section is. [LISTED] is derived from the entries the
 * patient has, so it is never persisted on its own.
 */
enum class ProfileSectionStatus {
    NOT_ASKED,
    NONE_CONFIRMED,
    LISTED,
}
