package com.medcompanion.app.feature.profile

import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.settings.AppLanguage

/**
 * State of the Profile tab (T8a). The screen is stateless: everything it
 * shows comes from here, and every change goes back through the ViewModel.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val entries: List<ProfileEntry> = emptyList(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val emergencyNumber: String = "",
    val isEmergencyNumberDirty: Boolean = false,
    val isExporting: Boolean = false,
    val share: ProfileShare? = null,
    val isDeleteConfirmVisible: Boolean = false,
    val isDeleting: Boolean = false,
    val entryDialog: EntryDialogState? = null,
    val message: ProfileMessage? = null,
) {
    val allergies: List<ProfileEntry>
        get() = entries.filter { it.type == ProfileEntryType.ALLERGY }

    val conditions: List<ProfileEntry>
        get() = entries.filter { it.type == ProfileEntryType.CONDITION }
}

/** A file ready to be handed to the share sheet. */
data class ProfileShare(
    val uri: String,
    val mimeType: String,
)

/** The add/edit dialog of one allergy or chronic condition. */
data class EntryDialogState(
    val type: ProfileEntryType,
    val entryId: Long? = null,
    val label: String = "",
    val note: String = "",
) {
    val isEditing: Boolean
        get() = entryId != null

    val canSave: Boolean
        get() = label.isNotBlank()
}

/** One-line feedback (snackbar), never a medical statement. */
enum class ProfileMessage {
    EMERGENCY_NUMBER_SAVED,
    DATA_DELETED,
    EXPORT_FAILED,
    DELETE_FAILED,
    SAVE_FAILED,
    DELETE_ENTRY_FAILED,
}
