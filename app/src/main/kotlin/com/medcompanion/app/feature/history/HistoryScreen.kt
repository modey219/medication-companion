package com.medcompanion.app.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.PlaceholderScreen
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * History tab. T1 ships a placeholder; the timeline and PDF export arrive in T7.
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_history), modifier = modifier)
}

@ScreenPreviews
@Composable
private fun HistoryScreenPreview() {
    MedCompanionTheme(dynamicColor = false) { HistoryScreen() }
}
