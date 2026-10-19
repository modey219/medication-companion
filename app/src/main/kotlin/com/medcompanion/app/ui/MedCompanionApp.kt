package com.medcompanion.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.feature.onboarding.OnboardingContent
import com.medcompanion.app.feature.onboarding.OnboardingViewModel
import com.medcompanion.app.feature.onboarding.PrivacyPolicyContent
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Entry point of the app. Until the patient consents on the first run, only
 * the onboarding shows; afterwards the app shell does. The privacy policy can
 * be read from there before consenting.
 */
@Composable
fun MedCompanionApp(
    nudgeReminderSetup: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isPrivacyVisible by remember { mutableStateOf(false) }

    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        isPrivacyVisible -> PrivacyPolicyContent(onBack = { isPrivacyVisible = false })

        state.isCompleted -> MedCompanionRoot(nudgeReminderSetup = nudgeReminderSetup)

        else ->
            OnboardingContent(
                language = state.language,
                onLanguageSelected = { viewModel.onLanguageSelected(it) },
                onAccept = { viewModel.onAccept() },
                onOpenPrivacy = { isPrivacyVisible = true },
            )
    }
}

@ScreenPreviews
@Composable
private fun OnboardingPreview() {
    MedCompanionTheme(dynamicColor = false) {
        OnboardingContent(
            language = AppLanguage.ARABIC,
            onLanguageSelected = {},
            onAccept = {},
            onOpenPrivacy = {},
        )
    }
}
