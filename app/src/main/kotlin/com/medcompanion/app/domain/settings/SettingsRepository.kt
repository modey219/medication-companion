package com.medcompanion.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Everything the patient can set (D-014). Writes are clamped to the reviewed
 * ranges of [ReminderPreferences], and [clear] resets the settings to their
 * defaults while keeping the first-run consent record.
 */
interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setEmergencyNumber(number: String)

    /** Minutes after the dose time before it counts as missed. */
    suspend fun setGraceMinutes(minutes: Long)

    /** Minutes the "Snooze" notification action postpones the dose. */
    suspend fun setSnoozeMinutes(minutes: Long)

    /** Minutes between repeated reminders while a dose is still unacted. */
    suspend fun setRepeatMinutes(minutes: Long)

    /** Doses left at or below which a medication is shown as low stock. */
    suspend fun setLowStockThreshold(doses: Int)

    /** The pharmacy the refill request goes to; empty means the patient picks. */
    suspend fun setPharmacyNumber(number: String)

    /** Must only be called after the first-run consent was confirmed. */
    suspend fun completeOnboarding()

    suspend fun clear()
}
