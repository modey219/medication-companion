package com.medcompanion.app.feature.profile.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.feature.profile.labelRes
import com.medcompanion.app.ui.components.Choice
import com.medcompanion.app.ui.components.ChoiceSelector
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Medical history (T12): what the patient had in the past. It is listed as
 * entered, with no judgement, and can be corrected or removed at any time.
 */
@Composable
fun MedicalHistoryScreen(
    viewModel: MedicalHistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.message
    val messageText = if (message != null) stringResource(message.messageRes()) else null
    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.onMessageShown()
    }

    MedicalHistoryContent(
        state = state,
        actions =
            MedicalHistoryActions(
                onAdd = viewModel::onAdd,
                onEdit = viewModel::onEdit,
                onTypeSelected = viewModel::onTypeSelected,
                onLabelChanged = viewModel::onLabelChanged,
                onYearChanged = viewModel::onYearChanged,
                onNoteChanged = viewModel::onNoteChanged,
                onDialogDismissed = viewModel::onDialogDismissed,
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
            ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Every change the screen can send back, so the UI stays stateless. */
internal data class MedicalHistoryActions(
    val onAdd: () -> Unit,
    val onEdit: (MedicalHistoryEntry) -> Unit,
    val onTypeSelected: (MedicalHistoryType) -> Unit,
    val onLabelChanged: (String) -> Unit,
    val onYearChanged: (String) -> Unit,
    val onNoteChanged: (String) -> Unit,
    val onDialogDismissed: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: (MedicalHistoryEntry) -> Unit,
)

@Composable
internal fun MedicalHistoryContent(
    state: MedicalHistoryUiState,
    actions: MedicalHistoryActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BodyText(stringResource(R.string.profile_history_desc))
                    }
                }
            }
            if (state.entries.isEmpty()) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BodyText(stringResource(R.string.profile_history_empty))
                        }
                    }
                }
            }
            items(count = state.entries.size, key = { state.entries[it].id }) { index ->
                HistoryRow(entry = state.entries[index], actions = actions)
            }
            item(key = "add") {
                TextButton(
                    onClick = actions.onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.profile_history_add))
                }
            }
        }

        state.dialog?.let { dialog ->
            HistoryDialog(dialog = dialog, actions = actions)
        }
    }
}

@Composable
private fun HistoryRow(
    entry: MedicalHistoryEntry,
    actions: MedicalHistoryActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = entry.label, style = MaterialTheme.typography.bodyLarge)
            BodyText(stringResource(entry.type.labelRes()))
            entry.year?.let { year ->
                BodyText(stringResource(R.string.profile_history_year_value, year))
            }
            entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                BodyText(note)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { actions.onEdit(entry) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(
                    onClick = { actions.onDelete(entry) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun HistoryDialog(
    dialog: HistoryDialogState,
    actions: MedicalHistoryActions,
) {
    val typeChoices =
        MedicalHistoryType.entries.map { type -> Choice(value = type, labelRes = type.labelRes()) }
    AlertDialog(
        onDismissRequest = actions.onDialogDismissed,
        title = {
            Text(
                stringResource(
                    if (dialog.isEditing) R.string.profile_history_edit else R.string.profile_history_add,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BodyText(stringResource(R.string.profile_history_type_field))
                ChoiceSelector(
                    choices = typeChoices,
                    selected = dialog.type,
                    onSelect = { type -> type?.let(actions.onTypeSelected) },
                )
                OutlinedTextField(
                    value = dialog.label,
                    onValueChange = actions.onLabelChanged,
                    label = { Text(stringResource(R.string.profile_history_label_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.year,
                    onValueChange = actions.onYearChanged,
                    label = { Text(stringResource(R.string.profile_history_year)) },
                    placeholder = { Text(stringResource(R.string.profile_history_year_hint)) },
                    isError = !dialog.isYearValid,
                    supportingText = {
                        if (!dialog.isYearValid) {
                            BodyText(stringResource(R.string.profile_entry_year_error))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.note,
                    onValueChange = actions.onNoteChanged,
                    label = { Text(stringResource(R.string.profile_entry_note)) },
                    placeholder = { Text(stringResource(R.string.profile_entry_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSave, enabled = dialog.canSave) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDialogDismissed) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@ScreenPreviews
@Composable
private fun MedicalHistoryPreview() {
    MedCompanionTheme(dynamicColor = false) {
        MedicalHistoryContent(
            state =
                MedicalHistoryUiState(
                    isLoading = false,
                    entries =
                        listOf(
                            MedicalHistoryEntry(
                                id = 1,
                                type = MedicalHistoryType.HOSPITALIZATION,
                                label = "التهاب رئوي",
                                year = 2019,
                            ),
                            MedicalHistoryEntry(
                                id = 2,
                                type = MedicalHistoryType.FAMILY_HISTORY,
                                label = "السكري في العائلة",
                            ),
                        ),
                ),
            actions =
                MedicalHistoryActions(
                    onAdd = {},
                    onEdit = {},
                    onTypeSelected = {},
                    onLabelChanged = {},
                    onYearChanged = {},
                    onNoteChanged = {},
                    onDialogDismissed = {},
                    onSave = {},
                    onDelete = {},
                ),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
