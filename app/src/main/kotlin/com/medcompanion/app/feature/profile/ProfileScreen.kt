package com.medcompanion.app.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.PlaceholderScreen
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Profile screen (reached from the top bar). T1 ships a placeholder;
 * the full profile arrives in T8.
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.profile_title), modifier = modifier)
}

@ScreenPreviews
@Composable
private fun ProfileScreenPreview() {
    MedCompanionTheme(dynamicColor = false) { ProfileScreen() }
}
