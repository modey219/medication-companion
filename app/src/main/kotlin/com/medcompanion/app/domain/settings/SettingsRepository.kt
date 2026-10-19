package com.medcompanion.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Patient settings (T8a). [clear] resets the settings to their defaults but
 * keeps the first-run consent record, so "delete all my data" does not send
 * the patient through onboarding again.
 */
interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setEmergencyNumber(number: String)

    suspend fun completeOnboarding()

    suspend fun clear()
}
