package com.medcompanion.app.domain.reminder

import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation

/**
 * Everything the notification layer needs to render one dose reminder.
 * Carries no [com.medcompanion.app.domain.model.DoseEvent] so the reminder
 * wording is built from plain, localizable values.
 */
data class DoseReminderPayload(
    val doseId: Long,
    val medicationName: String,
    val strength: String,
    val doseAmount: String,
    val foodRelation: FoodRelation,
)

/**
 * Orchestrates dose generation, missed-dose catch-up and exact alarms
 * (AGENTS.md > Reminders, T5).
 *
 * All implementations must be safe to call from a BroadcastReceiver, the
 * activity and ViewModels alike; [refresh] in particular never throws
 * (except cancellation) so a reminder refresh can never crash the app.
 *
 * Missed-dose rules:
 * - A dose whose grace period expired while the app or device was away is
 *   marked MISSED silently by [refresh].
 * - The gentle follow-up is sent only by the grace alarm ([onGraceFired]),
 *   at most once per dose, so "at most one gentle follow-up" always holds.
 */
interface ReminderEngine {
    /**
     * Regenerates the rolling window, removes stale future events (schedule
     * edit, stop, timezone or clock change), marks overdue unacted doses
     * MISSED and (re)schedules every reminder and grace alarm.
     */
    suspend fun refresh()

    /**
     * Records a user action (from the Today screen or a notification) with
     * the injected clock, cancels the dose's alarms and schedules the snooze
     * reminder when the status is [DoseStatus.SNOOZED].
     */
    suspend fun performAction(
        doseId: Long,
        status: DoseStatus,
    )

    /** A reminder alarm fired; null when the dose no longer needs a notification. */
    suspend fun onReminderFired(doseId: Long): DoseReminderPayload?

    /**
     * The grace alarm fired; marks the dose MISSED when still unacted and
     * returns the payload for the one gentle follow-up, or null when nothing
     * should be sent.
     */
    suspend fun onGraceFired(doseId: Long): DoseReminderPayload?
}
