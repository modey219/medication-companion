package com.medcompanion.app.domain.reminder

/**
 * Read-only view of everything the reminder setup flow needs: notification
 * permission, exact-alarm special access and battery-optimization exemption.
 * Implemented in the data layer against the Android system services.
 */
interface ReminderPermissionChecker {
    /** True when notifications are allowed (enabled app-wide and POST_NOTIFICATIONS granted on 33+). */
    fun notificationsEnabled(): Boolean

    /** True when exact alarms may be scheduled (always true below Android 12). */
    fun exactAlarmsEnabled(): Boolean

    /** True when the app is exempt from battery optimization. */
    fun batteryOptimizationExempt(): Boolean

    /** True when no reminder permission is missing. */
    fun isReminderSetupComplete(): Boolean =
        notificationsEnabled() && exactAlarmsEnabled() && batteryOptimizationExempt()
}
