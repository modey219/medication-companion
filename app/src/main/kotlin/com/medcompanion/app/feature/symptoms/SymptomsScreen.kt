package com.medcompanion.app.feature.symptoms

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.ui.components.EmergencyCard
import com.medcompanion.app.ui.components.formatDateTime
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme
import java.time.Instant

/**
 * Symptoms tab: the quick log (name, severity 1 to 5, optional note) and the
 * recent entries (AGENTS.md > Part C, T6). Every severity level is written as
 * a word next to its number, so nothing depends on color alone.
 */
@Composable
fun SymptomsScreen(
    viewModel: SymptomsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SymptomsContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onSeverityChange = viewModel::onSeverityChange,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}

@Composable
internal fun SymptomsContent(
    state: SymptomsUiState,
    onNameChange: (String) -> Unit,
    onSeverityChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (SymptomEntry) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "form") {
            SymptomForm(
                state = state,
                onNameChange = onNameChange,
                onSeverityChange = onSeverityChange,
                onNoteChange = onNoteChange,
                onSave = onSave,
            )
        }
        if (state.showEmergencyWarning) {
            item(key = "emergency") { EmergencyCard(number = state.emergencyNumber.trim()) }
        }
        state.error?.let { error ->
            item(key = "error") {
                ErrorRow(message = stringResource(error.messageRes()), onDismiss = onDismissError)
            }
        }
        item(key = "recent_title") { SectionTitle(stringResource(R.string.symptoms_recent_title)) }
        when {
            state.isLoading -> item(key = "loading") { BodyText(stringResource(R.string.placeholder_message)) }
            state.isEmpty -> item(key = "empty") { BodyText(stringResource(R.string.symptoms_empty)) }
            else ->
                items(state.entries, key = { it.id }) { entry ->
                    SymptomCard(entry = entry, context = context, onDelete = { onDelete(entry) })
                }
        }
    }
}

@Composable
private fun SymptomForm(
    state: SymptomsUiState,
    onNameChange: (String) -> Unit,
    onSeverityChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.symptoms_log_title))
            OutlinedTextField(
                value = state.form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.symptom_name_label)) },
                placeholder = { Text(stringResource(R.string.symptom_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.symptom_severity_label),
                style = MaterialTheme.typography.bodyLarge,
            )
            SEVERITY_RANGE.forEach { severity ->
                SeverityRow(
                    severity = severity,
                    selected = state.form.severity == severity,
                    onClick = { onSeverityChange(severity) },
                )
            }
            if (state.form.name.isNotBlank() && state.form.severity == null) {
                Text(
                    text = stringResource(R.string.symptom_severity_required),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = state.form.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.symptom_note_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Text(
                text = stringResource(R.string.symptom_time_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onSave,
                enabled = state.form.canSave && !state.isSaving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun SeverityRow(
    severity: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(severityLabelRes(severity))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(R.string.symptom_severity_option, severity, label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SymptomCard(
    entry: SymptomEntry,
    context: Context,
    onDelete: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    stringResource(
                        R.string.symptom_severity_option,
                        entry.severity,
                        stringResource(severityLabelRes(entry.severity)),
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = entry.loggedAt.formatDateTime(context),
                style = MaterialTheme.typography.bodyMedium,
            )
            entry.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
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

@ScreenPreviews
@Composable
private fun SymptomsScreenPreview() {
    val entry =
        SymptomEntry(
            id = 1,
            loggedAt = Instant.parse("2026-10-17T09:30:00Z"),
            name = "صداع",
            severity = 3,
            note = null,
        )
    MedCompanionTheme(dynamicColor = false) {
        SymptomsContent(
            state = SymptomsUiState(isLoading = false, entries = listOf(entry)),
            onNameChange = {},
            onSeverityChange = {},
            onNoteChange = {},
            onSave = {},
            onDelete = {},
            onDismissError = {},
        )
    }
}
