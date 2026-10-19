package com.medcompanion.app.feature.profile

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.ui.components.LanguageSelector
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme

/**
 * Profile tab (T8a): the patient's own facts (allergies, chronic conditions),
 * the app language, the emergency number, and data export and deletion.
 *
 * The screen is stateless; [ProfileContent] takes the state and the actions
 * so it can be previewed and tested without a ViewModel.
 */
@Composable
fun ProfileScreen(
    onOpenPrivacy: () -> Unit = {},
    onOpenReminderSetup: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.share) {
        val share = state.share ?: return@LaunchedEffect
        if (shareFile(context, share)) {
            viewModel.onShareLaunched()
        } else {
            viewModel.onShareFailed()
        }
    }

    val message = state.message
    val messageText = if (message != null) stringResource(message.messageRes()) else null
    LaunchedEffect(messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.onMessageShown()
    }

    ProfileContent(
        state = state,
        actions = ProfileActions(viewModel),
        snackbarHostState = snackbarHostState,
        onOpenPrivacy = onOpenPrivacy,
        onOpenReminderSetup = onOpenReminderSetup,
        modifier = modifier,
    )
}

/** Every change the screen can send back, so the UI stays stateless. */
internal data class ProfileActions(
    val onLanguageSelected: (AppLanguage) -> Unit,
    val onEmergencyNumberChange: (String) -> Unit,
    val onEmergencyNumberSave: () -> Unit,
    val onExportJson: () -> Unit,
    val onExportPdf: () -> Unit,
    val onDeleteAllRequested: () -> Unit,
    val onDeleteAllDismissed: () -> Unit,
    val onDeleteAllConfirmed: () -> Unit,
    val onAddEntry: (ProfileEntryType) -> Unit,
    val onEditEntry: (ProfileEntry) -> Unit,
    val onEntryLabelChanged: (String) -> Unit,
    val onEntryNoteChanged: (String) -> Unit,
    val onEntryDialogDismissed: () -> Unit,
    val onEntrySave: () -> Unit,
    val onEntryDelete: (ProfileEntry) -> Unit,
) {
    constructor(viewModel: ProfileViewModel) : this(
        onLanguageSelected = viewModel::onLanguageSelected,
        onEmergencyNumberChange = viewModel::onEmergencyNumberChange,
        onEmergencyNumberSave = viewModel::onEmergencyNumberSave,
        onExportJson = viewModel::onExportJson,
        onExportPdf = viewModel::onExportPdf,
        onDeleteAllRequested = viewModel::onDeleteAllRequested,
        onDeleteAllDismissed = viewModel::onDeleteAllDismissed,
        onDeleteAllConfirmed = viewModel::onDeleteAllConfirmed,
        onAddEntry = viewModel::onAddEntry,
        onEditEntry = viewModel::onEditEntry,
        onEntryLabelChanged = viewModel::onEntryLabelChanged,
        onEntryNoteChanged = viewModel::onEntryNoteChanged,
        onEntryDialogDismissed = viewModel::onEntryDialogDismissed,
        onEntrySave = viewModel::onEntrySave,
        onEntryDelete = viewModel::onEntryDelete,
    )
}

/** Opens the share sheet for the exported file; false if no app can take it. */
private fun shareFile(
    context: Context,
    share: ProfileShare,
): Boolean =
    runCatching {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = share.mimeType
                putExtra(Intent.EXTRA_STREAM, share.uri.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.profile_export_share_title)),
        )
    }.isSuccess

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    actions: ProfileActions,
    snackbarHostState: SnackbarHostState,
    onOpenPrivacy: () -> Unit,
    onOpenReminderSetup: () -> Unit,
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
            item(key = "allergies") {
                EntryListCard(ProfileEntryType.ALLERGY, state.allergies, actions)
            }
            item(key = "conditions") {
                EntryListCard(ProfileEntryType.CONDITION, state.conditions, actions)
            }
            item(key = "language") {
                LanguageCard(language = state.language, onLanguageSelected = actions.onLanguageSelected)
            }
            item(key = "emergency") {
                EmergencyNumberCard(state = state, actions = actions)
            }
            item(key = "data") {
                DataCard(state = state, actions = actions)
            }
            item(key = "info") {
                InfoCard(
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenReminderSetup = onOpenReminderSetup,
                )
            }
        }

        state.entryDialog?.let { dialog ->
            EntryDialog(dialog = dialog, actions = actions)
        }

        if (state.isDeleteConfirmVisible) {
            DeleteAllDialog(actions = actions)
        }
    }
}

@Composable
private fun EntryListCard(
    type: ProfileEntryType,
    entries: List<ProfileEntry>,
    actions: ProfileActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeading(stringResource(type.sectionTitleRes()))
            if (entries.isEmpty()) {
                BodyText(stringResource(R.string.profile_empty_list))
            }
            entries.forEach { entry ->
                EntryRow(entry = entry, actions = actions)
            }
            TextButton(
                onClick = { actions.onAddEntry(type) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(type.addLabelRes()))
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: ProfileEntry,
    actions: ProfileActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = entry.label, style = MaterialTheme.typography.bodyLarge)
        entry.note?.let { note ->
            BodyText(note)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { actions.onEditEntry(entry) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_edit))
            }
            TextButton(
                onClick = { actions.onEntryDelete(entry) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun LanguageCard(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_language_title))
            BodyText(stringResource(R.string.profile_language_desc))
            LanguageSelector(
                selected = language,
                onSelect = onLanguageSelected,
                labelRes = AppLanguage::labelRes,
            )
        }
    }
}

@Composable
private fun EmergencyNumberCard(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_emergency_title))
            BodyText(stringResource(R.string.profile_emergency_desc))
            OutlinedTextField(
                value = state.emergencyNumber,
                onValueChange = actions.onEmergencyNumberChange,
                label = { Text(stringResource(R.string.profile_emergency_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isEmergencyNumberDirty) {
                Button(
                    onClick = actions.onEmergencyNumberSave,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun DataCard(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_data_title))
            Button(
                onClick = actions.onExportJson,
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_export_json))
            }
            Button(
                onClick = actions.onExportPdf,
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_export_pdf))
            }
            BodyText(stringResource(R.string.profile_export_note))
            OutlinedButton(
                onClick = actions.onDeleteAllRequested,
                enabled = !state.isDeleting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_delete_all))
            }
        }
    }
}

@Composable
private fun InfoCard(
    onOpenPrivacy: () -> Unit,
    onOpenReminderSetup: () -> Unit,
) {
    val context = LocalContext.current
    val versionName =
        remember(context) {
            @Suppress("DEPRECATION")
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull()
        }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_info_title))
            BodyText(stringResource(R.string.notice_not_medical_advice))
            TextButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_privacy_link))
            }
            TextButton(
                onClick = onOpenReminderSetup,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_reminders_link))
            }
            if (versionName != null) {
                BodyText(stringResource(R.string.profile_version, versionName))
            }
        }
    }
}

@Composable
private fun EntryDialog(
    dialog: EntryDialogState,
    actions: ProfileActions,
) {
    AlertDialog(
        onDismissRequest = actions.onEntryDialogDismissed,
        title = {
            Text(
                stringResource(
                    if (dialog.isEditing) dialog.type.sectionTitleRes() else dialog.type.addLabelRes(),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.label,
                    onValueChange = actions.onEntryLabelChanged,
                    label = { Text(stringResource(dialog.type.fieldLabelRes())) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.note,
                    onValueChange = actions.onEntryNoteChanged,
                    label = { Text(stringResource(R.string.profile_entry_note)) },
                    placeholder = { Text(stringResource(R.string.profile_entry_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onEntrySave, enabled = dialog.canSave) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onEntryDialogDismissed) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteAllDialog(actions: ProfileActions) {
    AlertDialog(
        onDismissRequest = actions.onDeleteAllDismissed,
        title = { Text(stringResource(R.string.profile_delete_confirm_title)) },
        text = { Text(stringResource(R.string.profile_delete_confirm_body)) },
        confirmButton = {
            TextButton(
                onClick = actions.onDeleteAllConfirmed,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDeleteAllDismissed) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
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
private fun ProfilePreview() {
    MedCompanionTheme(dynamicColor = false) {
        ProfileContent(
            state =
                ProfileUiState(
                    isLoading = false,
                    language = AppLanguage.ARABIC,
                    emergencyNumber = "123",
                    entries =
                        listOf(
                            ProfileEntry(id = 1, type = ProfileEntryType.ALLERGY, label = "البنسلين", note = null),
                            ProfileEntry(
                                id = 2,
                                type = ProfileEntryType.CONDITION,
                                label = "السكري",
                                note = "النوع الثاني",
                            ),
                        ),
                ),
            actions =
                ProfileActions(
                    onLanguageSelected = {},
                    onEmergencyNumberChange = {},
                    onEmergencyNumberSave = {},
                    onExportJson = {},
                    onExportPdf = {},
                    onDeleteAllRequested = {},
                    onDeleteAllDismissed = {},
                    onDeleteAllConfirmed = {},
                    onAddEntry = {},
                    onEditEntry = {},
                    onEntryLabelChanged = {},
                    onEntryNoteChanged = {},
                    onEntryDialogDismissed = {},
                    onEntrySave = {},
                    onEntryDelete = {},
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenPrivacy = {},
            onOpenReminderSetup = {},
        )
    }
}
