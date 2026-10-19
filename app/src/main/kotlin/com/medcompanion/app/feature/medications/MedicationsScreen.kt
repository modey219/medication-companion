package com.medcompanion.app.feature.medications

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.ui.components.formatDate
import com.medcompanion.app.ui.components.formatTime
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * My medications tab: sections for active, completed and stopped medications
 * with add, edit and stop actions (AGENTS.md > Part C, T3).
 */
@Composable
fun MedicationsScreen(
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    viewModel: MedicationsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var stopTarget by remember { mutableStateOf<MedicationListItem?>(null) }

    MedicationsContent(
        state = state,
        onAddClick = onAddClick,
        onEditClick = onEditClick,
        onStopClick = { stopTarget = it },
        modifier = modifier,
    )

    stopTarget?.let { target ->
        StopDialog(
            medicationName = target.name,
            onConfirm = { reason ->
                viewModel.stop(target.id, reason)
                stopTarget = null
            },
            onDismiss = { stopTarget = null },
        )
    }
}

@Composable
internal fun MedicationsContent(
    state: MedicationsUiState,
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onStopClick: (MedicationListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onAddClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.action_add_medication))
        }

        if (state.isLoading) {
            EmptyMessage(stringResource(R.string.placeholder_message))
        } else if (state.isEmpty) {
            EmptyMessage(stringResource(R.string.medications_empty))
        } else {
            val activeTitle = stringResource(R.string.section_active)
            val completedTitle = stringResource(R.string.section_completed)
            val stoppedTitle = stringResource(R.string.section_stopped)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                section(
                    title = activeTitle,
                    items = state.active,
                    showSection = state.active.isNotEmpty(),
                    pharmacyNumber = state.pharmacyNumber,
                    onEditClick = onEditClick,
                    onStopClick = onStopClick,
                )
                section(
                    title = completedTitle,
                    items = state.completed,
                    showSection = state.completed.isNotEmpty(),
                    pharmacyNumber = state.pharmacyNumber,
                    onEditClick = onEditClick,
                    onStopClick = null,
                )
                section(
                    title = stoppedTitle,
                    items = state.stopped,
                    showSection = state.stopped.isNotEmpty(),
                    pharmacyNumber = state.pharmacyNumber,
                    onEditClick = onEditClick,
                    onStopClick = null,
                )
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    items: List<MedicationListItem>,
    showSection: Boolean,
    pharmacyNumber: String,
    onEditClick: (Long) -> Unit,
    onStopClick: ((MedicationListItem) -> Unit)?,
) {
    if (!showSection) return
    item(key = "header_$title") {
        SectionHeader(title)
    }
    items(items, key = { it.id }) { item ->
        MedicationCard(
            item = item,
            pharmacyNumber = pharmacyNumber,
            onEditClick = { onEditClick(item.id) },
            onStopClick =
                if (onStopClick != null) {
                    { onStopClick(item) }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun MedicationCard(
    item: MedicationListItem,
    pharmacyNumber: String,
    onEditClick: () -> Unit,
    onStopClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
            )
            if (item.strength.isNotBlank()) {
                Text(
                    text = item.strength,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = stringResource(item.form.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            ScheduleSummary(item)
            StockSummary(item, context, pharmacyNumber)
            if (item.status == MedicationStatus.STOPPED && !item.stopReason.isNullOrBlank()) {
                Text(
                    text = item.stopReason,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onStopClick != null) {
                    TextButton(onClick = onStopClick) {
                        Text(stringResource(R.string.action_stop))
                    }
                }
                TextButton(onClick = onEditClick) {
                    Text(stringResource(R.string.action_edit))
                }
            }
        }
    }
}

@Composable
private fun ScheduleSummary(item: MedicationListItem) {
    val context = LocalContext.current
    val separator = stringResource(R.string.summary_separator)
    val labels = weekdayLabels()
    val text =
        when (item.scheduleType) {
            ScheduleType.DAILY_TIMES ->
                stringResource(
                    R.string.schedule_summary_daily_times,
                    item.times.joinToString(separator) { it.formatTime(context) },
                )

            ScheduleType.EVERY_N_HOURS ->
                stringResource(
                    R.string.schedule_summary_interval,
                    item.intervalHours ?: 0,
                )

            ScheduleType.WEEKDAYS ->
                stringResource(
                    R.string.schedule_summary_weekdays,
                    item.weekdays
                        .sorted()
                        .joinToString(separator) { labels.getValue(it) },
                )

            ScheduleType.AS_NEEDED -> stringResource(R.string.schedule_summary_as_needed)
            null -> ""
        }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Expiry and stock lines. Expired / low-stock states use color AND text, never
 * color alone (AGENTS.md > Accessibility).
 */
@Composable
private fun StockSummary(
    item: MedicationListItem,
    context: Context,
    pharmacyNumber: String,
) {
    item.expiryDate?.let { expiry ->
        Text(
            text = stringResource(R.string.meds_expiry, expiry.formatDate(context)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (item.isExpired) {
            Text(
                text = stringResource(R.string.meds_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    item.remainingDoses?.let { remaining ->
        Text(
            text = stringResource(R.string.meds_remaining, remaining),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (item.isLowStock && item.status == MedicationStatus.ACTIVE) {
            Text(
                text = stringResource(R.string.meds_low_stock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = { openWhatsAppRefill(context, item.name, pharmacyNumber) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_refill_order))
            }
        }
    }
}

/**
 * Opens WhatsApp with a pre-filled refill message. When the patient saved a
 * pharmacy number in the Profile tab the request goes straight to it, otherwise
 * WhatsApp asks the patient which contact to use. The message is a request to
 * the pharmacy, never a recommendation about the medication. Failures are
 * silent: WhatsApp may not be installed.
 */
private fun openWhatsAppRefill(
    context: Context,
    medicationName: String,
    pharmacyNumber: String,
) {
    val message = context.getString(R.string.refill_message, medicationName)
    val encoded = URLEncoder.encode(message, "UTF-8")
    val pharmacy = pharmacyNumber.filter { it.isDigit() || it == '+' }
    val uri =
        if (pharmacy.isBlank()) {
            "https://api.whatsapp.com/send?text=$encoded".toUri()
        } else {
            "https://api.whatsapp.com/send?phone=$pharmacy&text=$encoded".toUri()
        }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/** Resolves all weekday short names in composable context (no stringResource in lambdas). */
@Composable
private fun weekdayLabels(): Map<DayOfWeek, String> =
    mapOf(
        DayOfWeek.SATURDAY to stringResource(R.string.weekday_saturday),
        DayOfWeek.SUNDAY to stringResource(R.string.weekday_sunday),
        DayOfWeek.MONDAY to stringResource(R.string.weekday_monday),
        DayOfWeek.TUESDAY to stringResource(R.string.weekday_tuesday),
        DayOfWeek.WEDNESDAY to stringResource(R.string.weekday_wednesday),
        DayOfWeek.THURSDAY to stringResource(R.string.weekday_thursday),
        DayOfWeek.FRIDAY to stringResource(R.string.weekday_friday),
    )

@Composable
private fun StopDialog(
    medicationName: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stop_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stop_dialog_message))
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.stop_dialog_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text(stringResource(R.string.action_stop))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@ScreenPreviews
@Composable
private fun MedicationsScreenPreview() {
    val sample =
        MedicationListItem(
            id = 1,
            name = "باراسيتامول",
            strength = "500 ملغ",
            form = MedicationForm.TABLET,
            status = MedicationStatus.ACTIVE,
            startDate = Instant.parse("2026-10-01T00:00:00Z"),
            endDate = null,
            stopReason = null,
            scheduleType = ScheduleType.DAILY_TIMES,
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            intervalHours = null,
            weekdays = emptySet(),
            foodRelation = FoodRelation.AFTER,
        )
    MedCompanionTheme(dynamicColor = false) {
        MedicationsContent(
            state = MedicationsUiState(isLoading = false, active = listOf(sample)),
            onAddClick = {},
            onEditClick = {},
            onStopClick = {},
        )
    }
}
