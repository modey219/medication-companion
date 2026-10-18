package com.medcompanion.app.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medcompanion.app.MainActivity
import com.medcompanion.app.core.reminder.ReminderActions
import com.medcompanion.app.domain.reminder.DoseAlarmScheduler
import com.medcompanion.app.receiver.DoseAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact alarms via [AlarmManager.setAlarmClock] (D-008): setAlarmClock is
 * never quota-deferred, wakes the device from doze and is allowed while
 * background-restricted, which is what a medication reminder needs.
 * setExactAndAllowWhileIdle would be deferred under the Android 14+
 * inexact-quota rules, so it is not used for dose times.
 *
 * Degrades silently when SCHEDULE_EXACT_ALARM is revoked (Android 14+ denies
 * it by default): scheduling becomes a no-op and
 * [com.medcompanion.app.domain.reminder.ReminderPermissionChecker] surfaces
 * the problem in the setup screen.
 */
@Singleton
class AlarmManagerDoseAlarmScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DoseAlarmScheduler {
        private val appContext = context.applicationContext
        private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

        override fun scheduleReminder(
            doseId: Long,
            triggerAt: Instant,
        ) = schedule(doseId, triggerAt, ReminderActions.ACTION_REMINDER)

        override fun scheduleGrace(
            doseId: Long,
            triggerAt: Instant,
        ) = schedule(doseId, triggerAt, ReminderActions.ACTION_GRACE)

        override fun cancel(doseId: Long) {
            ReminderActions.run {
                listOf(ACTION_REMINDER, ACTION_GRACE).forEach { action ->
                    val pending =
                        PendingIntent.getBroadcast(
                            appContext,
                            doseId.toInt(),
                            alarmIntent(action, doseId),
                            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                        )
                    if (pending != null) {
                        alarmManager.cancel(pending)
                        pending.cancel()
                    }
                }
            }
        }

        override fun canScheduleExact(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        private fun schedule(
            doseId: Long,
            triggerAt: Instant,
            action: String,
        ) {
            if (!canScheduleExact()) return
            val operation =
                PendingIntent.getBroadcast(
                    appContext,
                    doseId.toInt(),
                    alarmIntent(action, doseId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            // Tapping the alarm's system intent opens the app (Today screen).
            val showIntent =
                PendingIntent.getActivity(
                    appContext,
                    0,
                    Intent(appContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent),
                operation,
            )
        }

        private fun alarmIntent(
            action: String,
            doseId: Long,
        ) = Intent(appContext, DoseAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActions.EXTRA_DOSE_ID, doseId)
        }
    }
