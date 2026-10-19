package com.medcompanion.app.feature.symptoms

import com.medcompanion.app.domain.model.SymptomLog
import java.time.Instant

/** The severity choices offered by the log (1 to 5), from the domain model. */
val SEVERITY_RANGE: IntRange = SymptomLog.MIN_SEVERITY..SymptomLog.MAX_SEVERITY

/** Immutable UI state for the Symptoms tab (T6). */
data class SymptomsUiState(
    val isLoading: Boolean = true,
    val entries: List<SymptomEntry> = emptyList(),
    val form: SymptomFormState = SymptomFormState(),
    val isSaving: Boolean = false,
    val showEmergencyWarning: Boolean = false,
    val emergencyNumber: String = "",
    val error: SymptomsError? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && entries.isEmpty()
}

/**
 * The quick log form. Severity starts unchosen so the patient states it
 * instead of the app assuming one.
 */
data class SymptomFormState(
    val name: String = "",
    val severity: Int? = null,
    val note: String = "",
) {
    val canSave: Boolean
        get() = name.isNotBlank() && severity != null
}

/** One logged symptom, ready to render without further queries. */
data class SymptomEntry(
    val id: Long,
    val loggedAt: Instant,
    val name: String,
    val severity: Int,
    val note: String?,
)

/** User-visible failures; the screen maps them to strings. */
enum class SymptomsError {
    SAVE_FAILED,
    DELETE_FAILED,
}
