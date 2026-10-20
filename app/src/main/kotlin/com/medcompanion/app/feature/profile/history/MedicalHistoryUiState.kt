package com.medcompanion.app.feature.profile.history

import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.ProfileYears

/**
 * State of the "medical history" screen (T12). The year is held as text so the
 * patient's typing is never thrown away, and the screen stays stateless.
 */
data class MedicalHistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<MedicalHistoryEntry> = emptyList(),
    val dialog: HistoryDialogState? = null,
    val message: MedicalHistoryMessage? = null,
)

/** The add/edit dialog of one medical history item. */
data class HistoryDialogState(
    val entryId: Long? = null,
    val type: MedicalHistoryType = MedicalHistoryType.PAST_ILLNESS,
    val label: String = "",
    val year: String = "",
    val note: String = "",
) {
    val isEditing: Boolean
        get() = entryId != null

    val yearValue: Int?
        get() = year.trim().ifBlank { null }?.toIntOrNull()

    val isYearValid: Boolean
        get() = year.isBlank() || (yearValue?.let { it in ProfileYears.RANGE } == true)

    val canSave: Boolean
        get() = label.isNotBlank() && isYearValid
}

/** One-line feedback (snackbar), never a medical statement. */
enum class MedicalHistoryMessage {
    SAVED,
    SAVE_FAILED,
    DELETE_FAILED,
}
