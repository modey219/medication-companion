package com.medcompanion.app.feature.profile.history

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Medical history (T12): past illnesses, surgeries, hospital stays and family
 * history. The patient enters them; the app only records and shows them back.
 */
@HiltViewModel
class MedicalHistoryViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MedicalHistoryUiState())
        val uiState: StateFlow<MedicalHistoryUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                profileRepository.observeHistory().collect { entries ->
                    _uiState.update { it.copy(isLoading = false, entries = entries) }
                }
            }
        }

        fun onAdd() {
            _uiState.update { it.copy(dialog = HistoryDialogState()) }
        }

        fun onEdit(entry: MedicalHistoryEntry) {
            _uiState.update {
                it.copy(
                    dialog =
                        HistoryDialogState(
                            entryId = entry.id,
                            type = entry.type,
                            label = entry.label,
                            year = entry.year?.toString().orEmpty(),
                            note = entry.note.orEmpty(),
                        ),
                )
            }
        }

        fun onTypeSelected(type: MedicalHistoryType) {
            _uiState.update { state ->
                state.copy(dialog = state.dialog?.copy(type = type))
            }
        }

        fun onLabelChanged(label: String) {
            _uiState.update { state ->
                state.copy(dialog = state.dialog?.copy(label = label))
            }
        }

        fun onYearChanged(year: String) {
            _uiState.update { state ->
                state.copy(dialog = state.dialog?.copy(year = year))
            }
        }

        fun onNoteChanged(note: String) {
            _uiState.update { state ->
                state.copy(dialog = state.dialog?.copy(note = note))
            }
        }

        fun onDialogDismissed() {
            _uiState.update { it.copy(dialog = null) }
        }

        fun onSave() {
            val dialog = _uiState.value.dialog ?: return
            if (!dialog.canSave) return
            viewModelScope.launch {
                try {
                    val entry =
                        MedicalHistoryEntry(
                            id = dialog.entryId ?: 0,
                            type = dialog.type,
                            label = dialog.label.trim(),
                            year = dialog.yearValue,
                            note = dialog.note.trim().ifBlank { null },
                        )
                    if (dialog.entryId == null) {
                        profileRepository.addHistory(entry)
                    } else {
                        profileRepository.updateHistory(entry)
                    }
                    _uiState.update { it.copy(dialog = null, message = MedicalHistoryMessage.SAVED) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(dialog = null, message = MedicalHistoryMessage.SAVE_FAILED) }
                }
            }
        }

        fun onDelete(entry: MedicalHistoryEntry) {
            viewModelScope.launch {
                try {
                    profileRepository.deleteHistory(entry)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(message = MedicalHistoryMessage.DELETE_FAILED) }
                }
            }
        }

        fun onMessageShown() {
            _uiState.update { it.copy(message = null) }
        }
    }

/** One-line feedback shown in the snackbar. */
@StringRes
internal fun MedicalHistoryMessage.messageRes(): Int =
    when (this) {
        MedicalHistoryMessage.SAVED -> R.string.profile_history_saved
        MedicalHistoryMessage.SAVE_FAILED -> R.string.error_save_failed
        MedicalHistoryMessage.DELETE_FAILED -> R.string.profile_error_delete
    }
