package com.medcompanion.app.feature.medications.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.schedule.DraftError
import com.medcompanion.app.feature.medications.labelRes
import com.medcompanion.app.ui.components.formatDate
import com.medcompanion.app.ui.components.formatTime
import com.medcompanion.app.ui.preview.ScreenPreviews
import com.medcompanion.app.ui.theme.MedCompanionTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Add and edit a medication. One scrollable form: identity, dates, schedule,
 * dose and food relation, with mechanical validation only — never medical
 * advice (AGENTS.md > Medical safety rules).
 */
@Composable
fun MedicationEditScreen(
    onSaved: () -> Unit,
    viewModel: MedicationEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    MedicationEditContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onFormChange = viewModel::onFormChange,
        onStrengthChange = viewModel::onStrengthChange,
        onInstructionsChange = viewModel::onInstructionsChange,
        onSourceChange = viewModel::onSourceChange,
        onStartDateChange = viewModel::onStartDateChange,
        onEndDateChange = viewModel::onEndDateChange,
        onScheduleTypeChange = viewModel::onScheduleTypeChange,
        onTimeAdded = viewModel::onTimeAdded,
        onTimeRemoved = viewModel::onTimeRemoved,
        onTimeEdited = viewModel::onTimeEdited,
        onIntervalHoursChange = viewModel::onIntervalHoursChange,
        onWeekdayToggled = viewModel::onWeekdayToggled,
        onDoseAmountChange = viewModel::onDoseAmountChange,
        onFoodRelationChange = viewModel::onFoodRelationChange,
        onSave = viewModel::save,
    )
}

@Composable
internal fun MedicationEditContent(
    state: MedicationEditUiState,
    onNameChange: (String) -> Unit,
    onFormChange: (MedicationForm) -> Unit,
    onStrengthChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onSourceChange: (MedicationSource) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate?) -> Unit,
    onScheduleTypeChange: (ScheduleType) -> Unit,
    onTimeAdded: (LocalTime) -> Unit,
    onTimeRemoved: (LocalTime) -> Unit,
    onTimeEdited: (LocalTime, LocalTime) -> Unit,
    onIntervalHoursChange: (String) -> Unit,
    onWeekdayToggled: (DayOfWeek) -> Unit,
    onDoseAmountChange: (String) -> Unit,
    onFoodRelationChange: (FoodRelation) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)

        state.loadFailed ->
            MessageState(
                text = stringResource(R.string.error_load_failed),
                modifier = modifier,
            )

        else ->
            EditForm(
                state = state,
                onNameChange = onNameChange,
                onFormChange = onFormChange,
                onStrengthChange = onStrengthChange,
                onInstructionsChange = onInstructionsChange,
                onSourceChange = onSourceChange,
                onStartDateChange = onStartDateChange,
                onEndDateChange = onEndDateChange,
                onScheduleTypeChange = onScheduleTypeChange,
                onTimeAdded = onTimeAdded,
                onTimeRemoved = onTimeRemoved,
                onTimeEdited = onTimeEdited,
                onIntervalHoursChange = onIntervalHoursChange,
                onWeekdayToggled = onWeekdayToggled,
                onDoseAmountChange = onDoseAmountChange,
                onFoodRelationChange = onFoodRelationChange,
                onSave = onSave,
                modifier = modifier,
            )
    }
}

@Composable
private fun EditForm(
    state: MedicationEditUiState,
    onNameChange: (String) -> Unit,
    onFormChange: (MedicationForm) -> Unit,
    onStrengthChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onSourceChange: (MedicationSource) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate?) -> Unit,
    onScheduleTypeChange: (ScheduleType) -> Unit,
    onTimeAdded: (LocalTime) -> Unit,
    onTimeRemoved: (LocalTime) -> Unit,
    onTimeEdited: (LocalTime, LocalTime) -> Unit,
    onIntervalHoursChange: (String) -> Unit,
    onWeekdayToggled: (DayOfWeek) -> Unit,
    onDoseAmountChange: (String) -> Unit,
    onFoodRelationChange: (FoodRelation) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.field_name)) },
            isError = state.hasError(DraftError.NAME_REQUIRED),
            supportingText = {
                if (state.hasError(DraftError.NAME_REQUIRED)) {
                    Text(stringResource(R.string.error_name_required))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownField(
            label = stringResource(R.string.field_form),
            selectedLabel = stringResource(state.form.labelRes()),
            options = MedicationForm.entries.map { it to stringResource(it.labelRes()) },
            onSelected = onFormChange,
        )

        OutlinedTextField(
            value = state.strength,
            onValueChange = onStrengthChange,
            label = { Text(stringResource(R.string.field_strength)) },
            placeholder = { Text(stringResource(R.string.field_strength_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.instructions,
            onValueChange = onInstructionsChange,
            label = { Text(stringResource(R.string.field_instructions)) },
            placeholder = { Text(stringResource(R.string.field_instructions_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownField(
            label = stringResource(R.string.field_source),
            selectedLabel = stringResource(state.source.labelRes()),
            options = MedicationSource.entries.map { it to stringResource(it.labelRes()) },
            onSelected = onSourceChange,
        )

        SectionTitle(stringResource(R.string.field_start_date))

        DateField(
            label = stringResource(R.string.field_start_date),
            date = state.startDate,
            onDateChange = { picked -> onStartDateChange(picked ?: state.startDate) },
        )

        DateField(
            label = stringResource(R.string.field_end_date),
            date = state.endDate,
            onDateChange = onEndDateChange,
            allowClear = true,
            isError = state.hasError(DraftError.END_BEFORE_START),
            errorText = stringResource(R.string.error_end_before_start),
        )

        SectionTitle(stringResource(R.string.field_schedule_type))

        ScheduleType.entries.forEach { type ->
            SelectableRow(
                selected = state.scheduleType == type,
                text = stringResource(type.labelRes()),
                onClick = { onScheduleTypeChange(type) },
            )
        }

        when (state.scheduleType) {
            ScheduleType.DAILY_TIMES, ScheduleType.WEEKDAYS ->
                TimesSection(
                    times = state.times,
                    requiredError =
                        when {
                            state.hasError(DraftError.TIMES_DUPLICATE) ->
                                stringResource(R.string.error_times_duplicate)

                            state.hasError(DraftError.TIMES_REQUIRED) ->
                                stringResource(R.string.error_times_required)

                            else -> null
                        },
                    onTimeAdded = onTimeAdded,
                    onTimeRemoved = onTimeRemoved,
                    onTimeEdited = onTimeEdited,
                )

            ScheduleType.EVERY_N_HOURS ->
                OutlinedTextField(
                    value = state.intervalHours,
                    onValueChange = onIntervalHoursChange,
                    label = { Text(stringResource(R.string.field_interval_hours)) },
                    isError =
                        state.hasError(DraftError.INTERVAL_REQUIRED) ||
                            state.hasError(DraftError.INTERVAL_OUT_OF_RANGE),
                    supportingText = {
                        when {
                            state.hasError(DraftError.INTERVAL_REQUIRED) ->
                                Text(stringResource(R.string.error_interval_required))

                            state.hasError(DraftError.INTERVAL_OUT_OF_RANGE) ->
                                Text(stringResource(R.string.error_interval_range))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

            ScheduleType.AS_NEEDED -> Unit
        }

        if (state.scheduleType == ScheduleType.WEEKDAYS) {
            SectionTitle(stringResource(R.string.field_weekdays))
            WeekdayChips(
                selected = state.weekdays,
                onToggle = onWeekdayToggled,
            )
            if (state.hasError(DraftError.WEEKDAYS_REQUIRED)) {
                ErrorText(stringResource(R.string.error_weekdays_required))
            }
        }

        SectionTitle(stringResource(R.string.field_dose_amount))

        OutlinedTextField(
            value = state.doseAmount,
            onValueChange = onDoseAmountChange,
            label = { Text(stringResource(R.string.field_dose_amount)) },
            placeholder = { Text(stringResource(R.string.field_dose_amount_hint)) },
            isError = state.hasError(DraftError.DOSE_AMOUNT_REQUIRED),
            supportingText = {
                if (state.hasError(DraftError.DOSE_AMOUNT_REQUIRED)) {
                    Text(stringResource(R.string.error_dose_amount_required))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.field_food_relation))

        FoodRelation.entries.forEach { relation ->
            SelectableRow(
                selected = state.foodRelation == relation,
                text = stringResource(relation.labelRes()),
                onClick = { onFoodRelationChange(relation) },
            )
        }

        if (state.saveFailed) {
            ErrorText(stringResource(R.string.error_save_failed))
        }

        Button(
            onClick = onSave,
            enabled = !state.isSaving,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun TimesSection(
    times: List<LocalTime>,
    requiredError: String?,
    onTimeAdded: (LocalTime) -> Unit,
    onTimeRemoved: (LocalTime) -> Unit,
    onTimeEdited: (LocalTime, LocalTime) -> Unit,
) {
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    // Epoch second of the time being edited; -1 means "add a new time".
    var pickerTimeSeconds by rememberSaveable { mutableLongStateOf(-1L) }
    val editingTime = if (pickerTimeSeconds >= 0L) LocalTime.ofSecondOfDay(pickerTimeSeconds) else null

    SectionTitle(stringResource(R.string.field_times))

    times.forEach { time ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = time.formatTime(LocalContext.current),
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(
                            onClickLabel = stringResource(R.string.action_edit),
                            role = Role.Button,
                            onClick = {
                                pickerTimeSeconds = time.toSecondOfDay().toLong()
                                pickerVisible = true
                            },
                        ),
            )
            TextButton(onClick = { onTimeRemoved(time) }) {
                Text(stringResource(R.string.action_remove))
            }
        }
    }

    OutlinedButton(
        onClick = {
            pickerTimeSeconds = -1L
            pickerVisible = true
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.action_add_time))
    }

    if (requiredError != null) {
        ErrorText(requiredError)
    }

    if (pickerVisible) {
        TimePickerDialog(
            initialTime = editingTime,
            onDismiss = { pickerVisible = false },
            onConfirm = { time ->
                if (editingTime == null) {
                    onTimeAdded(time)
                } else {
                    onTimeEdited(editingTime, time)
                }
                pickerVisible = false
            },
        )
    }
}

@Composable
private fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    // Week starts on Saturday, matching the default Arabic locale.
    val order =
        listOf(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.chunked(4).forEach { rowDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowDays.forEach { day ->
                    FilterChip(
                        selected = day in selected,
                        onClick = { onToggle(day) },
                        label = { Text(stringResource(day.labelRes())) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    allowClear: Boolean = false,
    isError: Boolean = false,
    errorText: String = "",
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = date?.formatDate(context) ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (allowClear && date != null) {
            TextButton(
                onClick = { onDateChange(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_remove))
            }
        }
        if (isError) {
            ErrorText(errorText)
        }
    }

    if (showPicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    (date ?: LocalDate.now())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateChange(
                                Instant
                                    .ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    initialTime: LocalTime? = null,
) {
    val context = LocalContext.current
    val timePickerState =
        rememberTimePickerState(
            initialHour = initialTime?.hour ?: 0,
            initialMinute = initialTime?.minute ?: 0,
            is24Hour =
                android.text.format.DateFormat
                    .is24HourFormat(context),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        text = { TimePicker(state = timePickerState) },
    )
}

@Composable
private fun <T> DropdownField(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
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
                .semantics { heading() },
    )
}

@Composable
private fun SelectableRow(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ErrorText(text)
    }
}

@ScreenPreviews
@Composable
private fun MedicationEditScreenPreview() {
    MedCompanionTheme(dynamicColor = false) {
        MedicationEditContent(
            state =
                MedicationEditUiState(
                    name = "باراسيتامول",
                    strength = "500 ملغ",
                    startDate = LocalDate.of(2026, 10, 17),
                    scheduleType = ScheduleType.DAILY_TIMES,
                    times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                    doseAmount = "قرص واحد",
                    foodRelation = FoodRelation.AFTER,
                ),
            onNameChange = {},
            onFormChange = {},
            onStrengthChange = {},
            onInstructionsChange = {},
            onSourceChange = {},
            onStartDateChange = {},
            onEndDateChange = {},
            onScheduleTypeChange = {},
            onTimeAdded = {},
            onTimeRemoved = {},
            onTimeEdited = { _, _ -> },
            onIntervalHoursChange = {},
            onWeekdayToggled = {},
            onDoseAmountChange = {},
            onFoodRelationChange = {},
            onSave = {},
        )
    }
}
