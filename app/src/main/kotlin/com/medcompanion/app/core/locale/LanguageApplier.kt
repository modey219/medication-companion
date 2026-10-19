package com.medcompanion.app.core.locale

import com.medcompanion.app.domain.settings.AppLanguage

/**
 * Applies the patient's language choice to the running app. It is an
 * interface so the ViewModels can be tested without the framework delegate.
 */
fun interface LanguageApplier {
    fun apply(language: AppLanguage)
}
