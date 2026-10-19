package com.medcompanion.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.locale.LanguageApplier
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Startup state: the first-run screen or the app itself. */
data class OnboardingUiState(
    val isLoading: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val isCompleted: Boolean = false,
)

/**
 * Drives the first-run consent screen (T8a) and, through the same settings
 * flow, tells the app shell whether the patient has already consented.
 * Nothing is written until [onAccept].
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val languageApplier: LanguageApplier,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository.observe().collect { settings ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            language = settings.language,
                            isCompleted = settings.onboardingCompleted,
                        )
                    }
                }
            }
        }

        fun onLanguageSelected(language: AppLanguage) {
            if (_uiState.value.language == language) return
            viewModelScope.launch {
                // Persist first: applying the language recreates the activity.
                settingsRepository.setLanguage(language)
                languageApplier.apply(language)
            }
        }

        fun onAccept() {
            viewModelScope.launch {
                settingsRepository.completeOnboarding()
            }
        }
    }
