package com.medcompanion.app.feature.profile

import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionState
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.ProfileYears
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.ReminderPreferences

/**
 * State of the Profile tab (T8a, T8b, T12). The screen is stateless: everything
 * it shows comes from here, and every change goes back through the ViewModel.
 */
data class ProfileUiState(
    val isLoading: Boolean = true,
    val entries: List<ProfileEntry> = emptyList(),
    val basics: PatientBasics? = null,
    val history: List<MedicalHistoryEntry> = emptyList(),
    val sections: Map<ProfileSection, ProfileSectionState> = emptyMap(),
    val language: AppLanguage = AppLanguage.SYSTEM,
    val emergencyNumber: String = "",
    val isEmergencyNumberDirty: Boolean = false,
    val lowStockThreshold: Int = ReminderPreferences.DEFAULT_LOW_STOCK_THRESHOLD,
    val pharmacyNumber: String = "",
    val isPharmacyNumberDirty: Boolean = false,
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

    /** True once the patient entered at least one basic fact. */
    val hasBasics: Boolean
        get() = basics != null && !basics.isEmpty

    /** The state of one section; a section nobody asked about is "not asked". */
    fun sectionState(section: ProfileSection): ProfileSectionState =
        sections[section] ?: ProfileSectionState(section, ProfileSectionStatus.NOT_ASKED)
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
    val severity: Int? = null,
    val reaction: String = "",
    val sinceYear: String = "",
    val note: String = "",
) {
    val isEditing: Boolean
        get() = entryId != null

    /** The year typed by the patient, or null when the field is empty. */
    val sinceYearValue: Int?
        get() = sinceYear.trim().ifBlank { null }?.toIntOrNull()

    val isSinceYearValid: Boolean
        get() = sinceYear.isBlank() || (sinceYearValue?.let { it in ProfileYears.RANGE } == true)

    val canSave: Boolean
        get() = label.isNotBlank() && isSinceYearValid
}

/** One-line feedback (snackbar), never a medical statement. */
enum class ProfileMessage {
    EMERGENCY_NUMBER_SAVED,
    STOCK_THRESHOLD_SAVED,
    PHARMACY_NUMBER_SAVED,
    DATA_DELETED,
    EXPORT_FAILED,
    DELETE_FAILED,
    SAVE_FAILED,
    DELETE_ENTRY_FAILED,
    BASICS_SAVED,
    HISTORY_SAVED,
    SECTION_CONFIRMED,
    SECTION_CONFIRMATION_CLEARED,
}
