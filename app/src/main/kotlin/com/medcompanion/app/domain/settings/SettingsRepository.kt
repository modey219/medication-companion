package com.medcompanion.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Everything the patient can set (D-014). Writes are clamped to the reviewed
 * ranges of [ReminderPreferences], and [clear] resets the settings to their
 * defaults while keeping the first-run consent record.
 *
 * The assistant consents and the first-use consent are *not* kept by [clear]:
 * after deleting all data the patient is asked again and every sharing choice
 * starts off, so no health data leaves the device by default.
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

    /** Must only be called after the assistant's first-use consent was read. */
    suspend fun completeAssistantConsent()

    /**
     * Records the patient's agreement to personalize the assistant's answers
     * with their health profile, stamped with the version they read and the
     * time they gave it (T13).
     */
    suspend fun grantAssistantProfileConsent()

    /** Withdraws that agreement; no health profile is sent from then on. */
    suspend fun revokeAssistantProfileConsent()

    /** Records the patient's agreement to send a photo or a short video (T16). */
    suspend fun grantAssistantMediaConsent()

    /** Withdraws the media agreement. */
    suspend fun revokeAssistantMediaConsent()

    /** Must only be called after the first-run consent was confirmed. */
    suspend fun completeOnboarding()

    suspend fun clear()
}
