package com.medcompanion.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medcompanion.app.data.reminders.ReminderBroadcastHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Boot, timezone, clock, app-update and exact-alarm-permission changes all
 * invalidate every scheduled alarm: a full refresh regenerates the window and
 * reschedules (AGENTS.md > Reminders).
 */
@AndroidEntryPoint
class SystemChangeReceiver : BroadcastReceiver() {
    @Inject
    lateinit var handler: ReminderBroadcastHandler

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handler.handle(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
