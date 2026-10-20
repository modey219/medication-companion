package com.medcompanion.app.feature.profile.basics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.Sex
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
 * "Basic information" (T12): the birth year, the sex, whether the patient is
 * pregnant or breastfeeding, and the weight. It is only written when the
 * patient saves, so a background read can never overwrite their typing.
 */
@HiltViewModel
class ProfileBasicsViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                ProfileBasicsUiState(
                    isLoading = true,
                    currentYear = clock.today().year,
                ),
            )
        val uiState: StateFlow<ProfileBasicsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                profileRepository.observeBasics().collect { basics ->
                    _uiState.update { current ->
                        if (current.isDirty) {
                            current.copy(isLoading = false)
                        } else {
                            current.copy(
                                isLoading = false,
                                birthYear = basics?.birthYear?.toString().orEmpty(),
                                sex = basics?.sex,
                                pregnantOrBreastfeeding = basics?.pregnantOrBreastfeeding,
                                weightKg = basics?.weightKg?.toString().orEmpty(),
                            )
                        }
                    }
                }
            }
        }

        fun onBirthYearChange(birthYear: String) {
            _uiState.update { it.copy(birthYear = birthYear, isDirty = true) }
        }

        fun onSexSelected(sex: Sex?) {
            _uiState.update { it.copy(sex = sex, isDirty = true) }
        }

        fun onPregnancySelected(pregnantOrBreastfeeding: Boolean?) {
            _uiState.update { it.copy(pregnantOrBreastfeeding = pregnantOrBreastfeeding, isDirty = true) }
        }

        fun onWeightChange(weightKg: String) {
            _uiState.update { it.copy(weightKg = weightKg, isDirty = true) }
        }

        fun onSave() {
            val state = _uiState.value
            if (!state.canSave) return
            viewModelScope.launch {
                try {
                    profileRepository.saveBasics(state.toBasics() ?: PatientBasics())
                    _uiState.update { it.copy(isDirty = false, message = ProfileBasicsMessage.SAVED) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _uiState.update { it.copy(message = ProfileBasicsMessage.SAVE_FAILED) }
                }
            }
        }

        fun onMessageShown() {
            _uiState.update { it.copy(message = null) }
        }
    }
