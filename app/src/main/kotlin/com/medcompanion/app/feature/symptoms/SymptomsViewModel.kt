package com.medcompanion.app.feature.symptoms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.emergency.EmergencySymptoms
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.repository.SymptomRepository
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
 * Symptoms tab: the quick log (name, severity 1 to 5, optional note) and the
 * recent entries.
 *
 * The recorded time is the moment of saving and comes from the injectable
 * clock. The screen never judges a symptom, never rates what it means and
 * never suggests a treatment (AGENTS.md > Medical Safety Rules).
 *
 * The emergency check runs on the device against the typed name, so the card
 * appears before anything is saved and nothing leaves the phone.
 */
@HiltViewModel
class SymptomsViewModel
    @Inject
    constructor(
        private val symptomRepository: SymptomRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SymptomsUiState())
        val uiState: StateFlow<SymptomsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                symptomRepository.observeAll().collect { entries ->
                    _uiState.update { it.copy(isLoading = false, entries = entries.map { entry -> entry.toEntry() }) }
                }
            }
            viewModelScope.launch {
                settingsRepository.observe().collect { settings ->
                    _uiState.update { it.copy(emergencyNumber = settings.emergencyNumber) }
                }
            }
        }

        fun onNameChange(name: String) =
            _uiState.update { state ->
                state.copy(
                    form = state.form.copy(name = name),
                    showEmergencyWarning = EmergencySymptoms.isEmergencyText(name),
                    error = null,
                )
            }

        /** Ignores values outside [SEVERITY_RANGE] instead of storing them. */
        fun onSeverityChange(severity: Int) =
            _uiState.update { state ->
                if (severity !in SEVERITY_RANGE) {
                    state
                } else {
                    state.copy(form = state.form.copy(severity = severity), error = null)
                }
            }

        fun onNoteChange(note: String) = _uiState.update { it.copy(form = it.form.copy(note = note), error = null) }

        fun dismissError() = _uiState.update { it.copy(error = null) }

        /** Saves the form and clears it on success; keeps it on failure. */
        fun save() {
            val state = _uiState.value
            val form = state.form
            if (!form.canSave || state.isSaving) return

            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                try {
                    symptomRepository.add(
                        SymptomLog(
                            loggedAt = clock.now(),
                            name = form.name.trim(),
                            severity = requireNotNull(form.severity),
                            note = form.note.trim().ifBlank { null },
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            form = SymptomFormState(),
                            showEmergencyWarning = false,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSaving = false, error = SymptomsError.SAVE_FAILED) }
                }
            }
        }

        fun delete(entry: SymptomEntry) {
            viewModelScope.launch {
                try {
                    symptomRepository.delete(entry.toDomain())
                    _uiState.update { it.copy(error = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = SymptomsError.DELETE_FAILED) }
                }
            }
        }
    }

private fun SymptomLog.toEntry() = SymptomEntry(id, loggedAt, name, severity, note)

private fun SymptomEntry.toDomain() = SymptomLog(id, loggedAt, name, severity, note)
