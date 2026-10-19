package com.medcompanion.app.core.reminder

/**
 * Fixed reminder parameters that are not patient-facing (T5).
 *
 * The grace period, the snooze length and the repeat cadence used to live here;
 * since T8b they come from the patient's settings
 * ([com.medcompanion.app.domain.settings.ReminderPreferences]) and are read by
 * [com.medcompanion.app.data.reminders.ReminderEngineImpl] on every pass.
 */
object ReminderDefaults {
    /** How many days of dose events are generated ahead of time. */
    const val GENERATION_WINDOW_DAYS: Long = 7
}
