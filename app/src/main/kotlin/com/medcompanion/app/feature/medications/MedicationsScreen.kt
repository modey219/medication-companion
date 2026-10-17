package com.medcompanion.app.feature.medications

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.PlaceholderScreen
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * My medications tab. T1 ships a placeholder; add/edit/stop arrives in T3.
 */
@Composable
fun MedicationsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_medications), modifier = modifier)
}

@ScreenPreviews
@Composable
private fun MedicationsScreenPreview() {
    MedCompanionTheme(dynamicColor = false) { MedicationsScreen() }
}
