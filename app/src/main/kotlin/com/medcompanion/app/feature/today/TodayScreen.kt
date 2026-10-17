package com.medcompanion.app.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.PlaceholderScreen
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Today tab. T1 ships a placeholder; the real dose timeline is built in T4.
 */
@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_today), modifier = modifier)
}

@ScreenPreviews
@Composable
private fun TodayScreenPreview() {
    MedCompanionTheme(dynamicColor = false) { TodayScreen() }
}
