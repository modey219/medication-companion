package com.medcompanion.app.core.reminder

import android.app.AlarmManager
import android.content.Intent

/**
 * Single source of truth for the intent contract between AlarmManager, the
 * notification actions and the receivers (AGENTS.md > Reminders: actions and
 * rescheduling must work without opening the app).
 *
 * The reminder and grace alarms of the same dose share a PendingIntent
 * requestCode ([doseId]) but never match each other because Intent equality
 * includes the action; both are FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE.
 */
object ReminderActions {
    /** Fired at the scheduled dose time. */
    const val ACTION_REMINDER = "com.medcompanion.app.action.DOSE_REMINDER"

    /** Fired when the grace period ends. */
    const val ACTION_GRACE = "com.medcompanion.app.action.DOSE_GRACE"

    const val ACTION_TAKEN = "com.medcompanion.app.action.DOSE_TAKEN"
    const val ACTION_SNOOZE = "com.medcompanion.app.action.DOSE_SNOOZE"
    const val ACTION_SKIP = "com.medcompanion.app.action.DOSE_SKIP"

    const val EXTRA_DOSE_ID = "com.medcompanion.app.extra.DOSE_ID"

    /** System events that make every scheduled alarm stale; a full refresh reschedules. */
    val SYSTEM_CHANGE_ACTIONS: Set<String> =
        setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
}
