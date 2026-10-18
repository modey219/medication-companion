package com.medcompanion.app.data.reminders

import android.content.Intent
import com.medcompanion.app.core.reminder.ReminderActions
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.reminder.ReminderEngine
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single routing point for every reminder-related broadcast. Receivers stay
 * thin (inject this, goAsync, finish) and everything testable lives here:
 * alarm fires, notification actions and system changes all resolve to engine
 * calls plus notification show/cancel.
 *
 * Failures are swallowed except cancellation: a broken database read must
 * not crash a BroadcastReceiver or the app.
 */
@Singleton
class ReminderBroadcastHandler
    @Inject
    constructor(
        private val engine: ReminderEngine,
        private val notificationFactory: DoseNotificationFactory,
    ) {
        suspend fun handle(intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ReminderActions.ACTION_REMINDER ->
                    withDose(intent) { doseId ->
                        engine.onReminderFired(doseId)?.let(notificationFactory::showReminder)
                    }
                ReminderActions.ACTION_GRACE ->
                    withDose(intent) { doseId ->
                        engine.onGraceFired(doseId)?.let(notificationFactory::showFollowUp)
                    }
                ReminderActions.ACTION_TAKEN ->
                    withDose(intent) { doseId ->
                        engine.performAction(doseId, DoseStatus.TAKEN)
                    }
                ReminderActions.ACTION_SNOOZE ->
                    withDose(intent) { doseId ->
                        engine.performAction(doseId, DoseStatus.SNOOZED)
                    }
                ReminderActions.ACTION_SKIP ->
                    withDose(intent) { doseId ->
                        engine.performAction(doseId, DoseStatus.SKIPPED)
                    }
                in ReminderActions.SYSTEM_CHANGE_ACTIONS -> safely { engine.refresh() }
                else -> Unit
            }
        }

        private suspend fun withDose(
            intent: Intent,
            block: suspend (Long) -> Unit,
        ) {
            val doseId = intent.getLongExtra(ReminderActions.EXTRA_DOSE_ID, INVALID_DOSE_ID)
            if (doseId == INVALID_DOSE_ID) return
            safely { block(doseId) }
        }

        private suspend fun safely(block: suspend () -> Unit) {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Best effort only; the next refresh repairs whatever failed.
            }
        }

        private companion object {
            const val INVALID_DOSE_ID = -1L
        }
    }
