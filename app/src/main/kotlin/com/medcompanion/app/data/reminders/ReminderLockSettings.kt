package com.medcompanion.app.data.reminders

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lock-screen privacy for dose notifications: the user may keep the drug
 * name out of notifications while the device is locked (privacy on shared
 * phones). Read on every post, so a toggle applies to the next reminder
 * without restarting the app.
 */
@Singleton
class ReminderLockSettings
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun hideNameOnLockScreen(): Boolean = prefs.getBoolean(KEY_HIDE, false)

        fun setHideNameOnLockScreen(value: Boolean) {
            prefs.edit { putBoolean(KEY_HIDE, value) }
        }

        private companion object {
            const val PREFS_NAME = "reminder_lock_settings"
            const val KEY_HIDE = "hide_name_on_lock_screen"
        }
    }
