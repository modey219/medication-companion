package com.medcompanion.app.feature.symptoms

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.PlaceholderScreen
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Symptoms tab. T1 ships a placeholder; the symptom log arrives in T6.
 */
@Composable
fun SymptomsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.tab_symptoms), modifier = modifier)
}

@ScreenPreviews
@Composable
private fun SymptomsScreenPreview() {
    MedCompanionTheme(dynamicColor = false) { SymptomsScreen() }
}
