package com.medcompanion.app.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.PlaceholderScreen
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Assistant tab. T1 ships a placeholder; the chat arrives in T10.
 */
@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_assistant), modifier = modifier)
}

@ScreenPreviews
@Composable
private fun AssistantScreenPreview() {
    MedCompanionTheme(dynamicColor = false) { AssistantScreen() }
}
