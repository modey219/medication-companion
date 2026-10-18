package com.medcompanion.app.domain.reminder

import java.time.Instant

/**
 * Exact alarms for dose reminders, one reminder alarm plus one grace alarm
 * per dose (AGENTS.md > Reminders: "One exact alarm per upcoming dose").
 *
 * Implementations must degrade gracefully when the exact-alarm special
 * access is revoked: skip scheduling instead of crashing, so
 * [ReminderPermissionChecker] can surface the problem in the UI.
 */
interface DoseAlarmScheduler {
    /** Fires the reminder at [triggerAt]; replaces any existing reminder of the dose. */
    fun scheduleReminder(
        doseId: Long,
        triggerAt: Instant,
    )

    /** Fires the grace-end check at [triggerAt]; replaces any existing grace alarm of the dose. */
    fun scheduleGrace(
        doseId: Long,
        triggerAt: Instant,
    )

    /** Cancels both the reminder and the grace alarm of the dose. */
    fun cancel(doseId: Long)

    /** Whether exact alarms may currently be scheduled. */
    fun canScheduleExact(): Boolean
}
