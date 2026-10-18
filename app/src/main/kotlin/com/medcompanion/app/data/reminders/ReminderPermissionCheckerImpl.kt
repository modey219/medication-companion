package com.medcompanion.app.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only permission status for the reminder setup screen
 * (AGENTS.md > Reminders: plain-language flows for notifications, exact
 * alarms and battery optimization).
 */
@Singleton
class ReminderPermissionCheckerImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ReminderPermissionChecker {
        private val appContext = context.applicationContext

        override fun notificationsEnabled(): Boolean {
            val appLevel = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
            if (!appLevel) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }

        override fun exactAlarmsEnabled(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                appContext.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        override fun batteryOptimizationExempt(): Boolean =
            appContext
                .getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(appContext.packageName)
    }
