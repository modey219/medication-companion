package com.medcompanion.app.core.reminder

/**
 * Fixed reminder parameters for v1 (T5). Making them user-configurable
 * needs DataStore and lands with Profile (T8); until then the defaults below
 * are the source of truth (AGENTS.md > Reminders: configurable grace period
 * and snooze, defaults 60 and 10 minutes).
 *
 * TODO(eng, T8): read grace period from settings; snooze already lives in
 * [com.medcompanion.app.core.time.SnoozeDefaults].
 */
object ReminderDefaults {
    /** Minutes without any action after the scheduled time before a dose becomes MISSED. */
    const val GRACE_PERIOD_MINUTES: Long = 60

    /**
     * Minutes between repeated dose reminders while the dose is still unacted
     * inside its grace period. Repeats stop as soon as the patient acts or the
     * grace period ends (TODO(eng, T8): make configurable with the grace period).
     */
    const val REPEAT_INTERVAL_MINUTES: Long = 5

    /** How many days of dose events are generated ahead of time. */
    const val GENERATION_WINDOW_DAYS: Long = 7
}
