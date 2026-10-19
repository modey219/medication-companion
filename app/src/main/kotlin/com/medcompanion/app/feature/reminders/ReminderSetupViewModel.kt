package com.medcompanion.app.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.data.reminders.ReminderLockSettings
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import com.medcompanion.app.domain.settings.ReminderPreferences
import com.medcompanion.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the reminder setup screen shows; permission flags come from the system,
 * the reminder preferences come from the patient's settings (T8b).
 */
data class ReminderSetupUiState(
    val notificationsGranted: Boolean = false,
    val exactAlarmsGranted: Boolean = false,
    val batteryExempt: Boolean = false,
    val hideNameOnLockScreen: Boolean = false,
    val graceMinutes: Long = ReminderPreferences.DEFAULT_GRACE_MINUTES,
    val snoozeMinutes: Long = ReminderPreferences.DEFAULT_SNOOZE_MINUTES,
    val repeatMinutes: Long = ReminderPreferences.DEFAULT_REPEAT_MINUTES,
) {
    val isComplete: Boolean
        get() = notificationsGranted && exactAlarmsGranted && batteryExempt
}

/**
 * Reminder setup (T5, T8b). The screen re-reads the permissions on every
 * resume, because the user returns from system dialogs and Settings without
 * any callback we can rely on.
 *
 * Every preference write is followed by an engine refresh: the grace period
 * and the snooze length decide when alarms fire, so they must be rescheduled
 * in the same step.
 */
@HiltViewModel
class ReminderSetupViewModel
    @Inject
    constructor(
        private val permissionChecker: ReminderPermissionChecker,
        private val reminderEngine: ReminderEngine,
        private val lockSettings: ReminderLockSettings,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReminderSetupUiState())
        val uiState: StateFlow<ReminderSetupUiState> = _uiState.asStateFlow()

        init {
            refreshStatus()
            viewModelScope.launch {
                settingsRepository.observe().collect { settings ->
                    _uiState.value =
                        _uiState.value.copy(
                            graceMinutes = settings.graceMinutes,
                            snoozeMinutes = settings.snoozeMinutes,
                            repeatMinutes = settings.repeatMinutes,
                        )
                }
            }
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
                    hideNameOnLockScreen = lockSettings.hideNameOnLockScreen(),
                    graceMinutes = _uiState.value.graceMinutes,
                    snoozeMinutes = _uiState.value.snoozeMinutes,
                    repeatMinutes = _uiState.value.repeatMinutes,
                )
            val changed = next != _uiState.value
            _uiState.value = next
            if (changed && next.isComplete) {
                viewModelScope.launch { reminderEngine.refresh() }
            }
        }

        fun onHideNameToggle(enabled: Boolean) {
            lockSettings.setHideNameOnLockScreen(enabled)
            _uiState.value = _uiState.value.copy(hideNameOnLockScreen = enabled)
        }

        /** Minutes after the dose time before it counts as missed. */
        fun onGraceMinutesChange(steps: Int) {
            updatePreferences {
                setGraceMinutes(_uiState.value.graceMinutes + steps * ReminderPreferences.GRACE_STEP_MINUTES)
            }
        }

        /** Minutes the notification's Snooze action postpones the dose. */
        fun onSnoozeMinutesChange(steps: Int) {
            updatePreferences {
                setSnoozeMinutes(_uiState.value.snoozeMinutes + steps * ReminderPreferences.SNOOZE_STEP_MINUTES)
            }
        }

        /** Minutes between repeated reminders while a dose is unacted. */
        fun onRepeatMinutesChange(steps: Int) {
            updatePreferences {
                setRepeatMinutes(_uiState.value.repeatMinutes + steps * ReminderPreferences.REPEAT_STEP_MINUTES)
            }
        }

        private fun updatePreferences(write: suspend SettingsRepository.() -> Unit) {
            viewModelScope.launch {
                settingsRepository.write()
                reminderEngine.refresh()
            }
        }
    }
