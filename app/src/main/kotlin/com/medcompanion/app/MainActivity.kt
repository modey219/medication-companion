package com.medcompanion.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medcompanion.app.data.reminders.ReminderSetupNudge
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.ui.MedCompanionApp
import com.medcompanion.app.ui.theme.MedCompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity hosting all Compose screens.
 *
 * It extends [AppCompatActivity] so that the app language can be chosen with
 * `AppCompatDelegate.setApplicationLocales` (per-app languages) on every
 * supported Android version (see docs/DECISIONS.md D-015).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
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
                MedCompanionApp(nudgeReminderSetup = nudgeSetup)
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
