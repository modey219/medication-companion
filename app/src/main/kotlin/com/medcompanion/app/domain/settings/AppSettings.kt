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
    /**
     * Whether the patient agreed to the assistant's first-use consent. Until
     * this is true the Assistant tab shows the consent text instead of the
     * chat.
     */
    val assistantConsentCompleted: Boolean = false,
    /**
     * Whether the assistant may personalize its answers with the patient's
     * health profile: basic facts, allergies, conditions, medical history, the
     * medication list, recent adherence and recent symptoms (T13, D-021).
     * Versioned, timestamped and off by default.
     */
    val assistantProfileConsent: ConsentRecord = ConsentRecord(),
    /**
     * Whether the patient agreed to send a photo or a short video for analysis.
     * Kept separate from the profile consent because media is the most sensitive
     * thing the app can send; the capture screen arrives in T16.
     */
    val assistantMediaConsent: ConsentRecord = ConsentRecord(),
)
