package com.medcompanion.app.domain.settings

/**
 * Everything the patient can set in the Profile tab (T8a); the values are
 * stored in DataStore and never leave the device.
 *
 * TODO(eng, T8b): reminder preferences (grace period, snooze, repeat interval,
 * low-stock threshold, pharmacy number) join this model and are read by the
 * reminder engine.
 */
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val emergencyNumber: String = "",
    /**
     * The first-run consent record. It is kept when the patient deletes all
     * their data, because it is a record of consent and not a health entry.
     */
    val onboardingCompleted: Boolean = false,
)
