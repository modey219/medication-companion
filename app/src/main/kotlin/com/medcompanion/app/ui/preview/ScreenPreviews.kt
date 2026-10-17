package com.medcompanion.app.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Standard preview set required by AGENTS.md: every screen is previewed in
 * Arabic (RTL, default locale) and English, at 1.0x and 2.0x font scale.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Preview(name = "AR 1.0x", locale = "ar", fontScale = 1.0f, showBackground = true)
@Preview(name = "AR 2.0x", locale = "ar", fontScale = 2.0f, showBackground = true)
@Preview(name = "EN 1.0x", locale = "en", fontScale = 1.0f, showBackground = true)
@Preview(name = "EN 2.0x", locale = "en", fontScale = 2.0f, showBackground = true)
annotation class ScreenPreviews
