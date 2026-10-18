package com.medcompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.medcompanion.app.data.reminders.ReminderSetupNudge
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.ui.MedCompanionRoot
import com.medcompanion.app.ui.theme.MedCompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Single activity hosting all Compose screens. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var reminderEngine: ReminderEngine

    @Inject
    lateinit var setupNudge: ReminderSetupNudge

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // First launch only: open the setup screen once so the permission
        // explanations come before any system dialog (AGENTS.md > Reminders).
        val nudgeSetup = setupNudge.shouldNudge()
        if (nudgeSetup) setupNudge.markNudged()
        setContent {
            MedCompanionTheme {
                MedCompanionRoot(nudgeReminderSetup = nudgeSetup)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Every foreground entry regenerates the window and reschedules alarms;
        // refresh() never throws, so no wrapping is needed here.
        lifecycleScope.launch { reminderEngine.refresh() }
    }
}
