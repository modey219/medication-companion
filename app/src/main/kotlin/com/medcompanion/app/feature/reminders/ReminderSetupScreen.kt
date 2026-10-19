package com.medcompanion.app.feature.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.settings.ReminderPreferences
import com.medcompanion.app.ui.components.ValueStepperRow
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Reminder setup (T5): plain-language steps for notifications, exact alarms
 * and battery-optimization exemption, plus OEM background guidance
 * (AGENTS.md > Reminders).
 */
@Composable
fun ReminderSetupScreen(
    viewModel: ReminderSetupViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsRequested by remember { mutableStateOf(false) }
    val notificationsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshStatus()
        }

    // The user comes back from system dialogs and Settings at any moment, so
    // the status is re-read on every resume (and, below, after each tap).
    LifecycleResumeEffect(Unit) {
        viewModel.refreshStatus()
        onPauseOrDispose { }
    }

    ReminderSetupContent(
        state = state,
        notificationsActionLabelRes =
            if (notificationsRequested) {
                R.string.reminders_setup_action_open_settings
            } else {
                R.string.reminders_setup_action_grant
            },
        onNotificationsClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsRequested) {
                notificationsRequested = true
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(appNotificationSettingsIntent(context))
            }
        },
        onExactAlarmClick = { openExactAlarmSettings(context) },
        onBatteryClick = { openBatteryOptimizationSettings(context) },
        onHideNameToggle = viewModel::onHideNameToggle,
        onGraceMinutesChange = viewModel::onGraceMinutesChange,
        onSnoozeMinutesChange = viewModel::onSnoozeMinutesChange,
        onRepeatMinutesChange = viewModel::onRepeatMinutesChange,
        modifier = modifier,
    )
}

@Composable
internal fun ReminderSetupContent(
    state: ReminderSetupUiState,
    @StringRes notificationsActionLabelRes: Int,
    onNotificationsClick: () -> Unit,
    onExactAlarmClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onHideNameToggle: (Boolean) -> Unit,
    onGraceMinutesChange: (Int) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onRepeatMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var oemExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Text(
                text = stringResource(R.string.reminders_setup_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item(key = "notifications") {
            PermissionCard(
                title = stringResource(R.string.reminders_setup_notifications_title),
                description = stringResource(R.string.reminders_setup_notifications_desc),
                granted = state.notificationsGranted,
                actionLabel = stringResource(notificationsActionLabelRes),
                onClick = onNotificationsClick,
            )
        }
        item(key = "exact_alarm") {
            PermissionCard(
                title = stringResource(R.string.reminders_setup_exact_alarm_title),
                description = stringResource(R.string.reminders_setup_exact_alarm_desc),
                granted = state.exactAlarmsGranted,
                actionLabel = stringResource(R.string.reminders_setup_action_grant),
                onClick = onExactAlarmClick,
            )
        }
        item(key = "battery") {
            PermissionCard(
                title = stringResource(R.string.reminders_setup_battery_title),
                description = stringResource(R.string.reminders_setup_battery_desc),
                granted = state.batteryExempt,
                actionLabel = stringResource(R.string.reminders_setup_action_grant),
                onClick = onBatteryClick,
            )
        }
        if (state.isComplete) {
            item(key = "all_set") {
                Text(
                    text = stringResource(R.string.reminders_setup_all_set),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item(key = "lock_privacy") {
            PrivacySwitchRow(
                checked = state.hideNameOnLockScreen,
                onCheckedChange = onHideNameToggle,
            )
        }
        item(key = "preferences_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.reminders_preferences_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.reminders_preferences_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "grace") {
            val title = stringResource(R.string.reminders_grace_title)
            ValueStepperRow(
                title = title,
                description = stringResource(R.string.reminders_grace_desc),
                valueText = stringResource(R.string.reminders_minutes_value, state.graceMinutes),
                decreaseContentDescription = stringResource(R.string.action_decrease, title),
                increaseContentDescription = stringResource(R.string.action_increase, title),
                canDecrease = state.graceMinutes > ReminderPreferences.GRACE_MINUTES_RANGE.first,
                canIncrease = state.graceMinutes < ReminderPreferences.GRACE_MINUTES_RANGE.last,
                onDecrease = { onGraceMinutesChange(-1) },
                onIncrease = { onGraceMinutesChange(1) },
            )
        }
        item(key = "snooze") {
            val title = stringResource(R.string.reminders_snooze_title)
            ValueStepperRow(
                title = title,
                description = stringResource(R.string.reminders_snooze_desc),
                valueText = stringResource(R.string.reminders_minutes_value, state.snoozeMinutes),
                decreaseContentDescription = stringResource(R.string.action_decrease, title),
                increaseContentDescription = stringResource(R.string.action_increase, title),
                canDecrease = state.snoozeMinutes > ReminderPreferences.SNOOZE_MINUTES_RANGE.first,
                canIncrease = state.snoozeMinutes < ReminderPreferences.SNOOZE_MINUTES_RANGE.last,
                onDecrease = { onSnoozeMinutesChange(-1) },
                onIncrease = { onSnoozeMinutesChange(1) },
            )
        }
        item(key = "repeat") {
            val title = stringResource(R.string.reminders_repeat_title)
            ValueStepperRow(
                title = title,
                description = stringResource(R.string.reminders_repeat_desc),
                valueText = stringResource(R.string.reminders_minutes_value, state.repeatMinutes),
                decreaseContentDescription = stringResource(R.string.action_decrease, title),
                increaseContentDescription = stringResource(R.string.action_increase, title),
                canDecrease = state.repeatMinutes > ReminderPreferences.REPEAT_MINUTES_RANGE.first,
                canIncrease = state.repeatMinutes < ReminderPreferences.REPEAT_MINUTES_RANGE.last,
                onDecrease = { onRepeatMinutesChange(-1) },
                onIncrease = { onRepeatMinutesChange(1) },
            )
        }
        item(key = "oem_header") {
            Column {
                Text(
                    text = stringResource(R.string.reminders_setup_oem_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { oemExpanded = !oemExpanded }) {
                    Text(stringResource(R.string.reminders_setup_oem_desc))
                }
            }
        }
        if (oemExpanded) {
            item(key = "oem_steps") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.reminders_setup_oem_xiaomi,
                        R.string.reminders_setup_oem_samsung,
                        R.string.reminders_setup_oem_huawei,
                        R.string.reminders_setup_oem_oppo,
                        R.string.reminders_setup_oem_vivo,
                    ).forEach { stepRes ->
                        Text(
                            text = stringResource(stepRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item(key = "footer") {
            Text(
                text = stringResource(R.string.reminders_setup_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    // Status is a text label, never color alone (AGENTS.md > Localization and UX).
    val statusText =
        stringResource(
            if (granted) R.string.reminders_setup_status_ok else R.string.reminders_setup_status_missing,
        )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!granted) {
                Button(
                    onClick = onClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.reminders_lock_privacy_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.reminders_lock_privacy_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

private fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )
}

private fun openBatteryOptimizationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null)),
    )
}

@ScreenPreviews
@Composable
private fun ReminderSetupPreview() {
    MedCompanionTheme(dynamicColor = false) {
        ReminderSetupContent(
            state =
                ReminderSetupUiState(
                    notificationsGranted = false,
                    exactAlarmsGranted = true,
                    batteryExempt = false,
                ),
            notificationsActionLabelRes = R.string.reminders_setup_action_grant,
            onNotificationsClick = {},
            onExactAlarmClick = {},
            onBatteryClick = {},
            onHideNameToggle = {},
            onGraceMinutesChange = {},
            onSnoozeMinutesChange = {},
            onRepeatMinutesChange = {},
        )
    }
}
