package com.medcompanion.app.domain.model

import java.time.Instant

/**
 * A medication the patient takes, as entered by the patient.
 *
 * This is patient-entered data only; it is never a diagnosis or a recommendation.
 */
data class Medication(
    val id: Long = 0,
    val name: String,
    val form: MedicationForm,
    val strength: String,
    val instructions: String,
    val source: MedicationSource,
    val startDate: Instant,
    val endDate: Instant? = null,
    val status: MedicationStatus = MedicationStatus.ACTIVE,
    val stopReason: String? = null,
    val createdAt: Instant,
)
