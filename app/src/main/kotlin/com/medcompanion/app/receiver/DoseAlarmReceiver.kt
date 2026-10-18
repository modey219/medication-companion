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

/** Fires at the dose time and at grace end; all logic lives in the handler. */
@AndroidEntryPoint
class DoseAlarmReceiver : BroadcastReceiver() {
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
