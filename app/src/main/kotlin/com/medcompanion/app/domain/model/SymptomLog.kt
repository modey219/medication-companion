package com.medcompanion.app.domain.model

import java.time.Instant

/**
 * A symptom the patient logged. [severity] is 1 (mild) to 5 (severe).
 */
data class SymptomLog(
    val id: Long = 0,
    val loggedAt: Instant,
    val name: String,
    val severity: Int,
    val note: String? = null,
) {
    init {
        require(severity in MIN_SEVERITY..MAX_SEVERITY) {
            "severity must be between $MIN_SEVERITY and $MAX_SEVERITY but was $severity"
        }
    }

    companion object {
        const val MIN_SEVERITY = 1
        const val MAX_SEVERITY = 5
    }
}
