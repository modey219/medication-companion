package com.medcompanion.app.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the reminder setup screen shows; every flag is read from the system. */
data class ReminderSetupUiState(
    val notificationsGranted: Boolean = false,
    val exactAlarmsGranted: Boolean = false,
    val batteryExempt: Boolean = false,
) {
    val isComplete: Boolean
        get() = notificationsGranted && exactAlarmsGranted && batteryExempt
}

/**
 * Reminder setup (T5). The screen re-reads the permissions on every resume,
 * because the user returns from system dialogs and Settings without any
 * callback we can rely on.
 */
@HiltViewModel
class ReminderSetupViewModel
    @Inject
    constructor(
        private val permissionChecker: ReminderPermissionChecker,
        private val reminderEngine: ReminderEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReminderSetupUiState())
        val uiState: StateFlow<ReminderSetupUiState> = _uiState.asStateFlow()

        init {
            refreshStatus()
        }

        /**
         * Re-reads all three permissions. When setup becomes complete, alarms
         * skipped while permission was missing are scheduled right away.
         */
        fun refreshStatus() {
            val next =
                ReminderSetupUiState(
                    notificationsGranted = permissionChecker.notificationsEnabled(),
                    exactAlarmsGranted = permissionChecker.exactAlarmsEnabled(),
                    batteryExempt = permissionChecker.batteryOptimizationExempt(),
                )
            val changed = next != _uiState.value
            _uiState.value = next
            if (changed && next.isComplete) {
                viewModelScope.launch { reminderEngine.refresh() }
            }
        }
    }
