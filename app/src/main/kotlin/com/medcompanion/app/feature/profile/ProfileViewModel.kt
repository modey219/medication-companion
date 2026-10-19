package com.medcompanion.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.locale.LanguageApplier
import com.medcompanion.app.data.export.DataJsonExporter
import com.medcompanion.app.data.export.DataPdfExporter
import com.medcompanion.app.domain.maintenance.DataMaintenance
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.repository.ProfileRepository
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.ReminderPreferences
import com.medcompanion.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profile tab (T8a): the patient's own facts (allergies and chronic
 * conditions), the language, the emergency number, and the data actions
 * (export JSON or PDF, delete everything).
 *
 * The emergency number is only written when the patient saves, so a
 * settings read can never overwrite what they are typing.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val languageApplier: LanguageApplier,
        private val profileRepository: ProfileRepository,
        private val jsonExporter: DataJsonExporter,
        private val pdfExporter: DataPdfExporter,
        private val dataMaintenance: DataMaintenance,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProfileUiState())
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository.observe().collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            language = settings.language,
                            emergencyNumber =
                                if (current.isEmergencyNumberDirty) {
                                    current.emergencyNumber
                                } else {
                                    settings.emergencyNumber
                                },
                            lowStockThreshold = settings.lowStockThreshold,
                            pharmacyNumber =
                                if (current.isPharmacyNumberDirty) {
                                    current.pharmacyNumber
                                } else {
                                    settings.pharmacyNumber
                                },
                        )
                    }
                }
            }
            viewModelScope.launch {
                profileRepository.observeAll().collect { entries ->
                    _uiState.update { it.copy(entries = entries) }
                }
            }
        }

        fun onLanguageSelected(language: AppLanguage) {
            if (_uiState.value.language == language) return
            viewModelScope.launch {
                // Persist first: applying the language recreates the activity,
                // which cancels this scope.
                settingsRepository.setLanguage(language)
                languageApplier.apply(language)
            }
        }

        fun onEmergencyNumberChange(number: String) {
            _uiState.update { it.copy(emergencyNumber = number, isEmergencyNumberDirty = true) }
        }

        fun onEmergencyNumberSave() {
            val number = _uiState.value.emergencyNumber
            viewModelScope.launch {
                settingsRepository.setEmergencyNumber(number)
                _uiState.update {
                    it.copy(isEmergencyNumberDirty = false, message = ProfileMessage.EMERGENCY_NUMBER_SAVED)
                }
            }
        }

        /**
         * Steps the low-stock threshold. It is applied immediately (there is
         * nothing to type), so the medications screen follows in the same pass.
         */
        fun onLowStockThresholdChange(steps: Int) {
            val next =
                _uiState.value.lowStockThreshold + steps * ReminderPreferences.LOW_STOCK_STEP
            viewModelScope.launch {
                settingsRepository.setLowStockThreshold(next)
                _uiState.update { it.copy(message = ProfileMessage.STOCK_THRESHOLD_SAVED) }
            }
        }

        fun onPharmacyNumberChange(number: String) {
            _uiState.update { it.copy(pharmacyNumber = number, isPharmacyNumberDirty = true) }
        }

        fun onPharmacyNumberSave() {
            val number = _uiState.value.pharmacyNumber
            viewModelScope.launch {
                settingsRepository.setPharmacyNumber(number)
                _uiState.update {
                    it.copy(isPharmacyNumberDirty = false, message = ProfileMessage.PHARMACY_NUMBER_SAVED)
                }
            }
        }

        fun onExportJson() = export(JSON_MIME_TYPE, jsonExporter::export)

        fun onExportPdf() = export(PDF_MIME_TYPE, pdfExporter::export)

        fun onShareLaunched() {
            _uiState.update { it.copy(share = null) }
        }

        fun onShareFailed() {
            _uiState.update { it.copy(share = null, message = ProfileMessage.EXPORT_FAILED) }
        }

        fun onDeleteAllRequested() {
            _uiState.update { it.copy(isDeleteConfirmVisible = true) }
        }

        fun onDeleteAllDismissed() {
            _uiState.update { it.copy(isDeleteConfirmVisible = false) }
        }

        fun onDeleteAllConfirmed() {
            _uiState.update { it.copy(isDeleteConfirmVisible = false, isDeleting = true) }
            viewModelScope.launch {
                try {
                    dataMaintenance.deleteAllData()
                    _uiState.update { it.copy(isDeleting = false, message = ProfileMessage.DATA_DELETED) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(isDeleting = false, message = ProfileMessage.DELETE_FAILED) }
                }
            }
        }

        fun onAddEntry(type: ProfileEntryType) {
            _uiState.update { it.copy(entryDialog = EntryDialogState(type = type)) }
        }

        fun onEditEntry(entry: ProfileEntry) {
            _uiState.update {
                it.copy(
                    entryDialog =
                        EntryDialogState(
                            type = entry.type,
                            entryId = entry.id,
                            label = entry.label,
                            note = entry.note.orEmpty(),
                        ),
                )
            }
        }

        fun onEntryLabelChanged(label: String) {
            _uiState.update { state ->
                state.copy(entryDialog = state.entryDialog?.copy(label = label))
            }
        }

        fun onEntryNoteChanged(note: String) {
            _uiState.update { state ->
                state.copy(entryDialog = state.entryDialog?.copy(note = note))
            }
        }

        fun onEntryDialogDismissed() {
            _uiState.update { it.copy(entryDialog = null) }
        }

        fun onEntrySave() {
            val dialog = _uiState.value.entryDialog ?: return
            if (!dialog.canSave) return
            viewModelScope.launch {
                try {
                    val entry =
                        ProfileEntry(
                            id = dialog.entryId ?: 0,
                            type = dialog.type,
                            label = dialog.label.trim(),
                            note = dialog.note.trim().ifBlank { null },
                        )
                    if (dialog.entryId == null) profileRepository.add(entry) else profileRepository.update(entry)
                    _uiState.update { it.copy(entryDialog = null) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(entryDialog = null, message = ProfileMessage.SAVE_FAILED) }
                }
            }
        }

        fun onEntryDelete(entry: ProfileEntry) {
            viewModelScope.launch {
                try {
                    profileRepository.delete(entry)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(message = ProfileMessage.DELETE_ENTRY_FAILED) }
                }
            }
        }

        fun onMessageShown() {
            _uiState.update { it.copy(message = null) }
        }

        private fun export(
            mimeType: String,
            exportFile: suspend () -> String,
        ) {
            if (_uiState.value.isExporting) return
            _uiState.update { it.copy(isExporting = true) }
            viewModelScope.launch {
                try {
                    val uri = exportFile()
                    _uiState.update { it.copy(isExporting = false, share = ProfileShare(uri, mimeType)) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(isExporting = false, message = ProfileMessage.EXPORT_FAILED) }
                }
            }
        }

        private companion object {
            const val JSON_MIME_TYPE = "application/json"
            const val PDF_MIME_TYPE = "application/pdf"
        }
    }
