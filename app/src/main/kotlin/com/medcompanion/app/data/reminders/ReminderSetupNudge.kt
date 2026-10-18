package com.medcompanion.app.data.reminders

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time reminder-setup nudge (AGENTS.md > Reminders: permission flows
 * with plain-language explanations). The first app launch opens the setup
 * screen once so the user reads why notifications, exact alarms and battery
 * exemption are needed before any system dialog appears; later launches rely
 * on the Today banner.
 */
@Singleton
class ReminderSetupNudge
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** True only until [markNudged] runs (first launch). */
        fun shouldNudge(): Boolean = !prefs.getBoolean(KEY_NUDGED, false)

        fun markNudged() {
            prefs.edit { putBoolean(KEY_NUDGED, true) }
        }

        private companion object {
            const val PREFS_NAME = "reminder_setup_nudge"
            const val KEY_NUDGED = "nudged"
        }
    }
