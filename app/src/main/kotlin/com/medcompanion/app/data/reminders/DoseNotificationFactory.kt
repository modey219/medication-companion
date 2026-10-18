package com.medcompanion.app.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medcompanion.app.MainActivity
import com.medcompanion.app.R
import com.medcompanion.app.core.reminder.ReminderActions
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.reminder.DoseReminderPayload
import com.medcompanion.app.receiver.DoseActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts the two dose notifications (reminder at dose time and the
 * one gentle follow-up at grace end) and cancels them when an action is
 * recorded. Lives in the data layer so [ReminderEngineImpl] can cancel
 * notifications without depending on UI state; the Today screen reports its
 * actions through the engine, which funnels here.
 *
 * Wording is TODO(clinical-review) (docs/CLINICAL_REVIEW.md): it must stay
 * neutral, never scold a missed dose, and the follow-up is gentle by design.
 */
@Singleton
class DoseNotificationFactory
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val lockSettings: ReminderLockSettings,
    ) {
        private val appContext = context.applicationContext
        private val notificationManager = NotificationManagerCompat.from(appContext)

        fun showReminder(payload: DoseReminderPayload) = post(payload, followUp = false)

        fun showFollowUp(payload: DoseReminderPayload) = post(payload, followUp = true)

        fun cancel(doseId: Long) = notificationManager.cancel(doseId.toInt())

        private fun post(
            payload: DoseReminderPayload,
            followUp: Boolean,
        ) {
            if (!notificationManager.areNotificationsEnabled()) return
            ensureChannels()

            val channel = if (followUp) CHANNEL_FOLLOW_UP else CHANNEL_REMINDERS
            val header =
                if (payload.strength.isBlank()) {
                    payload.medicationName
                } else {
                    "${payload.medicationName} ${payload.strength}"
                }
            val body = detailBody(payload)

            val builder =
                NotificationCompat
                    .Builder(appContext, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .setContentTitle(header)
                    .setContentText(if (followUp) followUpText(body) else body.ifBlank { header })
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(if (followUp) "$header\n${followUpText(body)}" else "$header — $body"),
                    ).addAction(payload, ReminderActions.ACTION_TAKEN, R.string.action_take, 1)
                    .addAction(payload, ReminderActions.ACTION_SNOOZE, R.string.action_snooze, 2)
                    .addAction(payload, ReminderActions.ACTION_SKIP, R.string.action_skip, 3)

            if (lockSettings.hideNameOnLockScreen()) {
                // Lock screen shows a generic notice without the drug name;
                // the shade keeps the full details. TODO(clinical-review).
                builder
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(
                        NotificationCompat
                            .Builder(appContext, channel)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setAutoCancel(true)
                            .setContentIntent(openAppIntent())
                            .setContentTitle(appContext.getString(R.string.notification_lock_title))
                            .build(),
                    )
            }

            notificationManager.notify(payload.doseId.toInt(), builder.build())
        }

        private fun NotificationCompat.Builder.addAction(
            payload: DoseReminderPayload,
            action: String,
            labelRes: Int,
            slot: Int,
        ): NotificationCompat.Builder {
            val request = payload.doseId.toInt() * 10 + slot
            val intent =
                PendingIntent.getBroadcast(
                    appContext,
                    request,
                    Intent(appContext, DoseActionReceiver::class.java).apply {
                        this.action = action
                        putExtra(ReminderActions.EXTRA_DOSE_ID, payload.doseId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            return addAction(0, appContext.getString(labelRes), intent)
        }

        private fun detailBody(payload: DoseReminderPayload): String {
            val foodLabel =
                when (payload.foodRelation) {
                    FoodRelation.BEFORE -> appContext.getString(R.string.food_before)
                    FoodRelation.WITH -> appContext.getString(R.string.food_with)
                    FoodRelation.AFTER -> appContext.getString(R.string.food_after)
                    FoodRelation.ANY -> null
                }
            return listOf(payload.doseAmount.takeIf { it.isNotBlank() }, foodLabel)
                .filterNotNull()
                .joinToString(" — ")
        }

        private fun followUpText(body: String): String {
            val message = appContext.getString(R.string.notification_follow_up_text)
            return if (body.isBlank()) message else "$message\n$body"
        }

        private fun openAppIntent(): PendingIntent {
            val openFlags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            return PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java).apply { flags = openFlags },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureChannels() {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            val reminders =
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    appContext.getString(R.string.notification_channel_reminders_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = appContext.getString(R.string.notification_channel_reminders_desc)
                    enableVibration(true)
                }
            val followUp =
                NotificationChannel(
                    CHANNEL_FOLLOW_UP,
                    appContext.getString(R.string.notification_channel_follow_up_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = appContext.getString(R.string.notification_channel_follow_up_desc)
                    enableVibration(true)
                }
            manager.createNotificationChannel(reminders)
            manager.createNotificationChannel(followUp)
        }

        private companion object {
            const val CHANNEL_REMINDERS = "dose_reminders"
            const val CHANNEL_FOLLOW_UP = "dose_follow_up"
        }
    }
