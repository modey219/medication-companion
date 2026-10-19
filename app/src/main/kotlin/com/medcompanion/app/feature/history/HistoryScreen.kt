package com.medcompanion.app.feature.history

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.history.AdherenceSummary
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.feature.medications.labelRes
import com.medcompanion.app.feature.symptoms.severityLabelRes
import com.medcompanion.app.ui.components.formatDate
import com.medcompanion.app.ui.components.formatDateTime
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme
import java.time.Instant
import java.time.LocalDate

/**
 * History tab: the timeline of doses, symptoms and started medications, the
 * adherence summary and the PDF export (AGENTS.md > Part C, T7).
 *
 * The summary counts are plain numbers with neutral wording and never a grade:
 * the app does not judge missed doses (AGENTS.md > Localization and UX).
 * The export writes a file in the app cache and only then opens the share
 * sheet, so the patient decides whether anything leaves the device.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        if (sharePdf(context, uri)) {
            viewModel.onShareLaunched()
        } else {
            viewModel.onShareFailed()
        }
    }

    HistoryContent(
        state = state,
        onPeriodChange = viewModel::onPeriodChange,
        onExport = viewModel::export,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

/** Opens the share sheet for the exported file; returns false if no app can. */
private fun sharePdf(
    context: Context,
    uri: String,
): Boolean =
    runCatching {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.history_share_title)))
    }.isSuccess

@Composable
internal fun HistoryContent(
    state: HistoryUiState,
    onPeriodChange: (HistoryPeriod) -> Unit,
    onExport: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            SummaryCard(summary = state.summary, isLoading = state.isLoading)
        }
        item(key = "period") {
            PeriodRow(selected = state.period, onPeriodChange = onPeriodChange)
        }
        item(key = "export") {
            ExportSection(
                isExporting = state.isExporting,
                enabled = !state.isEmpty,
                onExport = onExport,
            )
        }
        state.error?.let { error ->
            item(key = "error") {
                ErrorRow(
                    message = stringResource(error.messageRes()),
                    onDismiss = onDismissError,
                )
            }
        }
        item(key = "timeline-title") {
            SectionTitle(stringResource(R.string.history_entries_title))
        }
        when {
            state.isLoading -> item(key = "loading") { BodyText(stringResource(R.string.placeholder_message)) }
            state.isEmpty -> item(key = "empty") { BodyText(stringResource(R.string.history_empty)) }
            else ->
                items(state.dayGroups, key = { it.date.toString() }) { group ->
                    DayCard(group = group, context = context)
                }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: AdherenceSummary,
    isLoading: Boolean,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle(stringResource(R.string.history_summary_title))
            if (isLoading) {
                BodyText(stringResource(R.string.placeholder_message))
            } else {
                BodyText(stringResource(R.string.history_summary_taken, summary.taken))
                BodyText(stringResource(R.string.history_summary_missed, summary.missed))
                BodyText(stringResource(R.string.history_summary_skipped, summary.skipped))
                BodyText(stringResource(R.string.history_summary_pending, summary.pending))
                val percent = summary.takenPercent
                BodyText(
                    text =
                        if (percent == null) {
                            stringResource(R.string.history_summary_none)
                        } else {
                            stringResource(R.string.history_summary_percent, percent)
                        },
                )
            }
        }
    }
}

@Composable
private fun PeriodRow(
    selected: HistoryPeriod,
    onPeriodChange: (HistoryPeriod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onPeriodChange(period) },
                label = { Text(stringResource(period.labelRes())) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun ExportSection(
    isExporting: Boolean,
    enabled: Boolean,
    onExport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = onExport,
            enabled = enabled && !isExporting,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.history_export))
            }
        }
        BodyText(stringResource(R.string.history_export_disclaimer))
    }
}

@Composable
private fun DayCard(
    group: HistoryDayGroup,
    context: Context,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.date.formatDate(context),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .semantics { heading() },
            )
            group.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                EntryBlock(entry = entry, context = context)
            }
        }
    }
}

@Composable
private fun EntryBlock(
    entry: HistoryEntry,
    context: Context,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BodyText(stringResource(entry.kindLabelRes()))
        when (entry) {
            is HistoryEntry.DoseLogged -> {
                EntryTitle(entry.medicationName)
                val details =
                    listOfNotNull(
                        stringResource(entry.status.labelRes()),
                        entry.doseAmount.takeIf { it.isNotBlank() },
                    ).joinToString(SEPARATOR)
                BodyText(details)
            }

            is HistoryEntry.SymptomLogged -> {
                EntryTitle(entry.name)
                BodyText(
                    stringResource(
                        R.string.symptom_severity_option,
                        entry.severity,
                        stringResource(severityLabelRes(entry.severity)),
                    ),
                )
                entry.note?.takeIf { it.isNotBlank() }?.let { note -> BodyText(note) }
            }

            is HistoryEntry.MedicationStarted -> {
                EntryTitle(entry.name)
                entry.strength.takeIf { it.isNotBlank() }?.let { strength -> BodyText(strength) }
            }
        }
        BodyText(entry.at.formatDateTime(context))
    }
}

@Composable
private fun EntryTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SectionTitle(text: String) {
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
private fun ErrorRow(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.action_dismiss))
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val PDF_MIME_TYPE = "application/pdf"
private const val SEPARATOR = " · "

@ScreenPreviews
@Composable
private fun HistoryScreenPreview() {
    val groups =
        listOf(
            HistoryDayGroup(
                date = LocalDate.parse("2026-10-17"),
                entries =
                    listOf(
                        HistoryEntry.DoseLogged(
                            medicationName = "ميتفورمين",
                            doseAmount = "حبة واحدة",
                            status = DoseStatus.TAKEN,
                            at = Instant.parse("2026-10-17T08:00:00Z"),
                        ),
                        HistoryEntry.SymptomLogged(
                            name = "صداع",
                            severity = 3,
                            note = null,
                            at = Instant.parse("2026-10-17T09:30:00Z"),
                        ),
                    ),
            ),
            HistoryDayGroup(
                date = LocalDate.of(2026, 10, 16),
                entries =
                    listOf(
                        HistoryEntry.MedicationStarted(
                            name = "فيتامين د",
                            strength = "1000 وحدة",
                            at = Instant.parse("2026-10-16T18:00:00Z"),
                        ),
                    ),
            ),
        )
    MedCompanionTheme(dynamicColor = false) {
        HistoryContent(
            state =
                HistoryUiState(
                    isLoading = false,
                    dayGroups = groups,
                    summary = AdherenceSummary(taken = 5, missed = 1, skipped = 0, pending = 2),
                ),
            onPeriodChange = {},
            onExport = {},
            onDismissError = {},
        )
    }
}
