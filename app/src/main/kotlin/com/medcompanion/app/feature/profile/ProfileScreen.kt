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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.ReminderPreferences
import com.medcompanion.app.ui.components.Choice
import com.medcompanion.app.ui.components.ChoiceSelector
import com.medcompanion.app.ui.components.LanguageSelector
import com.medcompanion.app.ui.components.ValueStepperRow
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
    onOpenBasics: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
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
        actions = ProfileActions(viewModel, onOpenBasics, onOpenHistory),
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
    val onLowStockThresholdChange: (Int) -> Unit,
    val onPharmacyNumberChange: (String) -> Unit,
    val onPharmacyNumberSave: () -> Unit,
    val onExportJson: () -> Unit,
    val onExportPdf: () -> Unit,
    val onDeleteAllRequested: () -> Unit,
    val onDeleteAllDismissed: () -> Unit,
    val onDeleteAllConfirmed: () -> Unit,
    val onOpenBasics: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onAddEntry: (ProfileEntryType) -> Unit,
    val onEditEntry: (ProfileEntry) -> Unit,
    val onEntryLabelChanged: (String) -> Unit,
    val onEntrySeverityChanged: (Int?) -> Unit,
    val onEntryReactionChanged: (String) -> Unit,
    val onEntrySinceYearChanged: (String) -> Unit,
    val onEntryNoteChanged: (String) -> Unit,
    val onEntryDialogDismissed: () -> Unit,
    val onEntrySave: () -> Unit,
    val onEntryDelete: (ProfileEntry) -> Unit,
    val onConfirmSectionEmpty: (ProfileSection) -> Unit,
    val onClearSectionConfirmation: (ProfileSection) -> Unit,
) {
    constructor(viewModel: ProfileViewModel) : this(viewModel, {}, {})

    constructor(
        viewModel: ProfileViewModel,
        onOpenBasics: () -> Unit,
        onOpenHistory: () -> Unit,
    ) : this(
        onLanguageSelected = viewModel::onLanguageSelected,
        onEmergencyNumberChange = viewModel::onEmergencyNumberChange,
        onEmergencyNumberSave = viewModel::onEmergencyNumberSave,
        onLowStockThresholdChange = viewModel::onLowStockThresholdChange,
        onPharmacyNumberChange = viewModel::onPharmacyNumberChange,
        onPharmacyNumberSave = viewModel::onPharmacyNumberSave,
        onExportJson = viewModel::onExportJson,
        onExportPdf = viewModel::onExportPdf,
        onDeleteAllRequested = viewModel::onDeleteAllRequested,
        onDeleteAllDismissed = viewModel::onDeleteAllDismissed,
        onDeleteAllConfirmed = viewModel::onDeleteAllConfirmed,
        onOpenBasics = onOpenBasics,
        onOpenHistory = onOpenHistory,
        onAddEntry = viewModel::onAddEntry,
        onEditEntry = viewModel::onEditEntry,
        onEntryLabelChanged = viewModel::onEntryLabelChanged,
        onEntrySeverityChanged = viewModel::onEntrySeverityChanged,
        onEntryReactionChanged = viewModel::onEntryReactionChanged,
        onEntrySinceYearChanged = viewModel::onEntrySinceYearChanged,
        onEntryNoteChanged = viewModel::onEntryNoteChanged,
        onEntryDialogDismissed = viewModel::onEntryDialogDismissed,
        onEntrySave = viewModel::onEntrySave,
        onEntryDelete = viewModel::onEntryDelete,
        onConfirmSectionEmpty = viewModel::onConfirmSectionEmpty,
        onClearSectionConfirmation = viewModel::onClearSectionConfirmation,
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
            item(key = "basics") {
                BasicsCard(state = state, actions = actions)
            }
            item(key = "history") {
                HistoryCard(state = state, actions = actions)
            }
            item(key = "allergies") {
                EntryListCard(ProfileEntryType.ALLERGY, state, actions)
            }
            item(key = "conditions") {
                EntryListCard(ProfileEntryType.CONDITION, state, actions)
            }
            item(key = "language") {
                LanguageCard(language = state.language, onLanguageSelected = actions.onLanguageSelected)
            }
            item(key = "emergency") {
                EmergencyNumberCard(state = state, actions = actions)
            }
            item(key = "stock") {
                StockCard(state = state, actions = actions)
            }
            item(key = "pharmacy") {
                PharmacyCard(state = state, actions = actions)
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
private fun BasicsCard(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_basics_title))
            BodyText(stringResource(R.string.profile_basics_desc))
            val parts = basicsSummaryParts(state.basics)
            if (parts.isEmpty()) {
                BodyText(stringResource(R.string.profile_basics_summary_empty))
            } else {
                parts.forEach { BodyText(it) }
            }
            TextButton(
                onClick = actions.onOpenBasics,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_basics_open))
            }
        }
    }
}

/** The basic facts as short lines; empty when nothing was entered. */
@Composable
private fun basicsSummaryParts(basics: PatientBasics?): List<String> {
    if (basics == null) return emptyList()
    return buildList {
        basics.birthYear?.let { add(stringResource(R.string.profile_basics_birth_year_value, it)) }
        basics.sex?.let { add(stringResource(it.labelRes())) }
        basics.weightKg?.let { add(stringResource(R.string.profile_basics_weight_value, it)) }
    }
}

@Composable
private fun HistoryCard(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_history_title))
            BodyText(stringResource(R.string.profile_history_desc))
            BodyText(
                if (state.history.isEmpty()) {
                    stringResource(R.string.profile_history_empty)
                } else {
                    stringResource(R.string.profile_history_count, state.history.size)
                },
            )
            TextButton(
                onClick = actions.onOpenHistory,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.profile_history_open))
            }
        }
    }
}

@Composable
private fun EntryListCard(
    type: ProfileEntryType,
    state: ProfileUiState,
    actions: ProfileActions,
) {
    val entries = if (type == ProfileEntryType.ALLERGY) state.allergies else state.conditions
    val sectionState = state.sectionState(type.section())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeading(stringResource(type.sectionTitleRes()))
            BodyText(stringResource(sectionState.status.descriptionRes()))
            if (sectionState.status == ProfileSectionStatus.NOT_ASKED) {
                BodyText(stringResource(R.string.profile_section_hint))
            }
            if (entries.isEmpty()) {
                BodyText(stringResource(R.string.profile_empty_list))
            }
            entries.forEach { entry ->
                EntryRow(entry = entry, actions = actions)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { actions.onAddEntry(type) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(type.addLabelRes()))
                }
                if (sectionState.status == ProfileSectionStatus.NOT_ASKED) {
                    TextButton(
                        onClick = { actions.onConfirmSectionEmpty(type.section()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.profile_section_confirm_none))
                    }
                }
                if (sectionState.status == ProfileSectionStatus.NONE_CONFIRMED) {
                    TextButton(
                        onClick = { actions.onClearSectionConfirmation(type.section()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.profile_section_clear_confirmation))
                    }
                }
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
        entry.severity?.let { severity ->
            BodyText(stringResource(severityLabelRes(severity)))
        }
        entry.reaction?.takeIf { it.isNotBlank() }?.let { reaction ->
            BodyText(reaction)
        }
        entry.sinceYear?.let { year ->
            BodyText(stringResource(R.string.profile_entry_since_year_value, year))
        }
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
private fun StockCard(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    val thresholdTitle = stringResource(R.string.profile_low_stock_title)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_stock_title))
            BodyText(stringResource(R.string.profile_stock_desc))
            ValueStepperRow(
                title = thresholdTitle,
                description = stringResource(R.string.profile_low_stock_desc),
                valueText = stringResource(R.string.profile_low_stock_value, state.lowStockThreshold),
                decreaseContentDescription = stringResource(R.string.action_decrease, thresholdTitle),
                increaseContentDescription = stringResource(R.string.action_increase, thresholdTitle),
                canDecrease = state.lowStockThreshold > ReminderPreferences.LOW_STOCK_THRESHOLD_RANGE.first,
                canIncrease = state.lowStockThreshold < ReminderPreferences.LOW_STOCK_THRESHOLD_RANGE.last,
                onDecrease = { actions.onLowStockThresholdChange(-1) },
                onIncrease = { actions.onLowStockThresholdChange(1) },
            )
        }
    }
}

@Composable
private fun PharmacyCard(
    state: ProfileUiState,
    actions: ProfileActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(stringResource(R.string.profile_pharmacy_title))
            OutlinedTextField(
                value = state.pharmacyNumber,
                onValueChange = actions.onPharmacyNumberChange,
                label = { Text(stringResource(R.string.profile_pharmacy_label)) },
                supportingText = { BodyText(stringResource(R.string.profile_pharmacy_desc)) },
                placeholder = { Text(stringResource(R.string.profile_pharmacy_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isPharmacyNumberDirty) {
                Button(
                    onClick = actions.onPharmacyNumberSave,
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
    val severityChoices: List<Choice<Int>> =
        listOf(
            Choice(value = null, labelRes = R.string.profile_entry_severity_unset),
            Choice(value = ProfileEntry.MIN_SEVERITY, labelRes = R.string.profile_severity_mild),
            Choice(value = ProfileEntry.MIN_SEVERITY + 1, labelRes = R.string.profile_severity_moderate),
            Choice(value = ProfileEntry.MAX_SEVERITY, labelRes = R.string.profile_severity_severe),
        )
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = dialog.label,
                    onValueChange = actions.onEntryLabelChanged,
                    label = { Text(stringResource(dialog.type.fieldLabelRes())) },
                    modifier = Modifier.fillMaxWidth(),
                )
                BodyText(stringResource(R.string.profile_entry_severity))
                ChoiceSelector(
                    choices = severityChoices,
                    selected = dialog.severity,
                    onSelect = actions.onEntrySeverityChanged,
                )
                OutlinedTextField(
                    value = dialog.reaction,
                    onValueChange = actions.onEntryReactionChanged,
                    label = { Text(stringResource(R.string.profile_entry_reaction)) },
                    placeholder = { Text(stringResource(R.string.profile_entry_reaction_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.sinceYear,
                    onValueChange = actions.onEntrySinceYearChanged,
                    label = { Text(stringResource(R.string.profile_entry_since_year)) },
                    placeholder = { Text(stringResource(R.string.profile_entry_since_year_hint)) },
                    isError = !dialog.isSinceYearValid,
                    supportingText = {
                        if (!dialog.isSinceYearValid) {
                            BodyText(stringResource(R.string.profile_entry_year_error))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    onLowStockThresholdChange = {},
                    onPharmacyNumberChange = {},
                    onPharmacyNumberSave = {},
                    onExportJson = {},
                    onExportPdf = {},
                    onDeleteAllRequested = {},
                    onDeleteAllDismissed = {},
                    onDeleteAllConfirmed = {},
                    onOpenBasics = {},
                    onOpenHistory = {},
                    onAddEntry = {},
                    onEditEntry = {},
                    onEntryLabelChanged = {},
                    onEntrySeverityChanged = {},
                    onEntryReactionChanged = {},
                    onEntrySinceYearChanged = {},
                    onEntryNoteChanged = {},
                    onEntryDialogDismissed = {},
                    onEntrySave = {},
                    onEntryDelete = {},
                    onConfirmSectionEmpty = {},
                    onClearSectionConfirmation = {},
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenPrivacy = {},
            onOpenReminderSetup = {},
        )
    }
}
