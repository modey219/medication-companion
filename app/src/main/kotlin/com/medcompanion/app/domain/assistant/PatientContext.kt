package com.medcompanion.app.domain.assistant

import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.Sex
import java.time.Instant

/** One allergy or chronic condition, as the patient entered it. */
data class PatientContextEntry(
    val label: String,
    val severity: Int? = null,
    val reaction: String? = null,
    val sinceYear: Int? = null,
)

/** One item of the patient's medical history. */
data class PatientContextHistory(
    val type: MedicalHistoryType,
    val label: String,
    val year: Int? = null,
)

/** One medication with the schedules the assistant needs to answer about it. */
data class PatientContextMedication(
    val name: String,
    val strength: String,
    val status: MedicationStatus,
    val schedules: List<String> = emptyList(),
    val stopReason: String? = null,
)

/** One recent symptom the patient logged. */
data class PatientContextSymptom(
    val name: String,
    val severity: Int,
    val loggedAt: Instant,
)

/**
 * The last-14-days dose record. [recorded] is how many doses fell in the window
 * at all, so 0 means "nothing was recorded", which is not the same as a good
 * record (AGENTS.md > Medical Safety Rules).
 */
data class PatientAdherence(
    val taken: Int,
    val missed: Int,
    val recorded: Int,
)

/**
 * Everything about the patient the assistant may be personalized with (T13,
 * D-021). Built on the device, sent only while the profile consent is granted,
 * and never stored: the app keeps the model and the backend keeps nothing.
 *
 * Every field is optional or a (possibly empty) list, and [sections] records how
 * complete each part is, so an empty list is never read as "none". The age comes
 * from the birth year alone, never a full date of birth.
 *
 * TODO(clinical-review): the exact set of fields the assistant receives, and
 * whether the adherence window and size caps are the right ones.
 */
data class PatientContext(
    val generatedAt: Instant,
    val ageYears: Int? = null,
    val sex: Sex? = null,
    val pregnantOrBreastfeeding: Boolean? = null,
    val weightKg: Int? = null,
    val allergies: List<PatientContextEntry> = emptyList(),
    val conditions: List<PatientContextEntry> = emptyList(),
    val medicalHistory: List<PatientContextHistory> = emptyList(),
    val medications: List<PatientContextMedication> = emptyList(),
    val stoppedLast30Days: List<PatientContextMedication> = emptyList(),
    val adherenceLast14Days: PatientAdherence? = null,
    val recentSymptoms: List<PatientContextSymptom> = emptyList(),
    val sections: Map<ProfileSection, ProfileSectionStatus> = emptyMap(),
)
