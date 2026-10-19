package com.medcompanion.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medcompanion.app.R
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.feature.profile.labelRes
import com.medcompanion.app.ui.components.LanguageSelector

/**
 * First-run consent (T8a). It states what the app does with the patient's
 * data in plain language before anything is recorded, and the button is the
 * consent action itself: nothing is stored until the patient taps it.
 *
 * TODO(clinical-review): the consent wording. TODO(legal): the privacy policy
 * text behind the link.
 */
@Composable
internal fun OnboardingContent(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onAccept: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        listOf(
            R.string.onboarding_point_1,
            R.string.onboarding_point_2,
            R.string.onboarding_point_3,
        ).forEach { point ->
            Text(
                text = stringResource(point),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.notice_not_medical_advice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        LanguageSelector(
            selected = language,
            onSelect = onLanguageSelected,
            labelRes = AppLanguage::labelRes,
        )
        TextButton(onClick = onOpenPrivacy) {
            Text(stringResource(R.string.onboarding_privacy_link))
        }
        Button(
            onClick = onAccept,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.onboarding_accept))
        }
    }
}
