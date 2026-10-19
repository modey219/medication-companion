package com.medcompanion.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Privacy policy (T8a).
 *
 * TODO(legal): the text is a placeholder and must be replaced by a reviewed
 * policy before release.
 */
@Composable
internal fun PrivacyPolicyContent(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.privacy_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.privacy_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun PrivacyPolicyPreview() {
    MedCompanionTheme(dynamicColor = false) {
        PrivacyPolicyContent(onBack = {})
    }
}
