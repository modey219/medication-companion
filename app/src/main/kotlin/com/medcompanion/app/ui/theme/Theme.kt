package com.medcompanion.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Calm, high-contrast palette. Never rely on color alone (see AGENTS.md > UX).
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF1B6C4B),
        onPrimary = Color.White,
        secondary = Color(0xFF4A6357),
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8FD6B4),
        onPrimary = Color(0xFF003824),
        secondary = Color(0xFFB1CCBD),
        background = Color(0xFF121212),
        surface = Color(0xFF121212),
    )

/**
 * Material 3 theme for the app.
 *
 * RTL and locale are handled by the platform: the manifest sets
 * `android:supportsRtl="true"` and Arabic is the default locale, so Compose
 * receives [androidx.compose.ui.unit.LayoutDirection.Rtl] automatically.
 */
@Composable
fun MedCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MedCompanionTypography,
        content = content,
    )
}
