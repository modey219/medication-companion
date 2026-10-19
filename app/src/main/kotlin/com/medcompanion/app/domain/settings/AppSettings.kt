package com.medcompanion.app.domain.settings

/**
 * Everything the patient can set in the Profile tab (T8a, T8b); the values are
 * stored in DataStore and never leave the device.
 *
 * The reminder and stock preferences are read by
 * [com.medcompanion.app.domain.reminder.ReminderEngine] and the medications
 * screen, so the defaults here are the same ones
 * [ReminderPreferences] documents.
 */
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val emergencyNumber: String = "",
    /**
     * The first-run consent record. It is kept when the patient deletes all
     * their data, because it is a record of consent and not a health entry.
     */
    val onboardingCompleted: Boolean = false,
    /** Minutes a dose may stay unacted before it becomes MISSED. */
    val graceMinutes: Long = ReminderPreferences.DEFAULT_GRACE_MINUTES,
    /** How long the Snooze action pushes a dose back. */
    val snoozeMinutes: Long = ReminderPreferences.DEFAULT_SNOOZE_MINUTES,
    /** Minutes between repeated reminders while the dose is still unacted. */
    val repeatMinutes: Long = ReminderPreferences.DEFAULT_REPEAT_MINUTES,
    /** Doses left at or below which a medication is shown as low on stock. */
    val lowStockThreshold: Int = ReminderPreferences.DEFAULT_LOW_STOCK_THRESHOLD,
    /**
     * The pharmacy to send a refill request to. Empty means the patient picks
     * an app each time, which stays the default: the app never assumes a
     * pharmacy.
     */
    val pharmacyNumber: String = "",
)
