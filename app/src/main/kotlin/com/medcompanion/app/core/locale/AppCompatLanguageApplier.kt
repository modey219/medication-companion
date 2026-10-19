package com.medcompanion.app.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.medcompanion.app.domain.settings.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app locales (D-015): the AppCompat delegate stores the choice and, on
 * Android 13 and later, hands it to the platform, so the language also applies
 * to notifications and any text outside Compose. An empty locale list means
 * "follow the device".
 */
@Singleton
class AppCompatLanguageApplier
    @Inject
    constructor() : LanguageApplier {
        override fun apply(language: AppLanguage) {
            val tag = language.languageTag
            val locales =
                if (tag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
