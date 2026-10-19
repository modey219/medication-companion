package com.medcompanion.app.domain.settings

/**
 * The language the app shows its own text in. [SYSTEM] follows the device
 * setting; the app's default resources are Arabic, so a device set to any
 * other language still shows Arabic until the patient picks otherwise.
 */
enum class AppLanguage(
    val languageTag: String?,
) {
    SYSTEM(null),
    ARABIC("ar"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.languageTag == tag } ?: SYSTEM
    }
}
