package com.medcompanion.app.feature.medications.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.schedule.MedicationDraft
import com.medcompanion.app.domain.schedule.validateDraft
import com.medcompanion.app.ui.navigation.ARG_MEDICATION_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Add/edit medication flow. All writes go through the repositories
 * (AGENTS.md > Part C, T3).
 *
 * Date convention: the start date is stored at the start of that day and the
 * end date at the start of the following day — a half-open interval
 * `[startDate, endDate)` consistent with D-005.
 */
@HiltViewModel
class MedicationEditViewModel
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val reminderEngine: ReminderEngine,
        private val clock: AppClock,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val medicationId: Long? = savedStateHandle.get<Long>(ARG_MEDICATION_ID)

        private var existingScheduleId: Long? = null
        private var existingCreatedAt: Instant? = null

        private val _uiState =
            MutableStateFlow(
                MedicationEditUiState(
                    isLoading = medicationId != null,
                    medicationId = medicationId,
                    startDate = clock.today(),
                ),
            )
        val uiState: StateFlow<MedicationEditUiState> = _uiState.asStateFlow()

        init {
            if (medicationId != null) loadExisting()
        }

        private fun loadExisting() {
            val id = medicationId ?: return
            viewModelScope.launch {
                val medication = medicationRepository.getById(id)
                if (medication == null) {
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    return@launch
                }
                val schedule = scheduleRepository.getByMedication(id).firstOrNull()
                existingScheduleId = schedule?.id
                existingCreatedAt = medication.createdAt
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = medication.name,
                        form = medication.form,
                        strength = medication.strength,
                        instructions = medication.instructions,
                        source = medication.source,
                        startDate = medication.startDate.atZone(clock.zone()).toLocalDate(),
                        // Half-open storage: the end instant sits at the start of
                        // the day AFTER the picked date, so step back one second
                        // to show the date the user originally chose.
                        endDate =
                            medication
                                .endDate
                                ?.minusSeconds(1)
                                ?.atZone(clock.zone())
                                ?.toLocalDate(),
                        scheduleType = schedule?.type ?: it.scheduleType,
                        times = schedule?.times ?: it.times,
                        intervalHours = schedule?.intervalHours?.toString() ?: it.intervalHours,
                        weekdays = schedule?.weekdays ?: it.weekdays,
                        doseAmount = schedule?.doseAmount ?: it.doseAmount,
                        foodRelation = schedule?.foodRelation ?: it.foodRelation,
                    )
                }
            }
        }

        fun onNameChange(value: String) = updateState { it.copy(name = value) }

        fun onFormChange(value: MedicationForm) = updateState { it.copy(form = value) }

        fun onStrengthChange(value: String) = updateState { it.copy(strength = value) }

        fun onInstructionsChange(value: String) = updateState { it.copy(instructions = value) }

        fun onSourceChange(value: MedicationSource) = updateState { it.copy(source = value) }

        fun onStartDateChange(value: LocalDate) = updateState { it.copy(startDate = value) }

        fun onEndDateChange(value: LocalDate?) = updateState { it.copy(endDate = value) }

        fun onScheduleTypeChange(value: ScheduleType) = updateState { it.copy(scheduleType = value) }

        fun onTimeAdded(value: LocalTime) =
            updateState { state ->
                state.copy(times = (state.times + value).distinct().sorted())
            }

        fun onTimeRemoved(value: LocalTime) = updateState { it.copy(times = it.times - value) }

        /** Replaces [old] with [new] in place; a duplicate collapses to one entry. */
        fun onTimeEdited(
            old: LocalTime,
            new: LocalTime,
        ) = updateState { state ->
            state.copy(times = (state.times - old + new).distinct().sorted())
        }

        fun onIntervalHoursChange(value: String) =
            updateState { it.copy(intervalHours = value.filter { c -> c.isDigit() }.take(3)) }

        fun onWeekdayToggled(value: DayOfWeek) =
            updateState { state ->
                state.copy(
                    weekdays =
                        if (value in state.weekdays) {
                            state.weekdays - value
                        } else {
                            state.weekdays + value
                        },
                )
            }

        fun onDoseAmountChange(value: String) = updateState { it.copy(doseAmount = value) }

        fun onFoodRelationChange(value: FoodRelation) = updateState { it.copy(foodRelation = value) }

        fun save() {
            val state = _uiState.value
            val draft =
                MedicationDraft(
                    name = state.name.trim(),
                    startDate = state.startDate,
                    endDate = state.endDate,
                    scheduleType = state.scheduleType,
                    times = state.times,
                    intervalHours = state.intervalHours.trim().toIntOrNull(),
                    weekdays = state.weekdays,
                    doseAmount = state.doseAmount.trim(),
                )
            val errors = validateDraft(draft)
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(errors = errors, saveFailed = false) }
                return
            }

            _uiState.update { it.copy(errors = emptySet(), saveFailed = false, isSaving = true) }
            viewModelScope.launch {
                try {
                    val zone = clock.zone()
                    val medication =
                        Medication(
                            id = state.medicationId ?: 0L,
                            name = draft.name,
                            form = state.form,
                            strength = state.strength.trim(),
                            instructions = state.instructions.trim(),
                            source = state.source,
                            startDate = draft.startDate.atStartOfDay(zone).toInstant(),
                            endDate =
                                draft.endDate
                                    ?.plusDays(1)
                                    ?.atStartOfDay(zone)
                                    ?.toInstant(),
                            status = MedicationStatus.ACTIVE,
                            stopReason = null,
                            createdAt = existingCreatedAt ?: clock.now(),
                        )
                    val schedule =
                        Schedule(
                            id = existingScheduleId ?: 0L,
                            medicationId = medication.id,
                            type = draft.scheduleType,
                            times = draft.times,
                            intervalHours = draft.intervalHours,
                            weekdays = draft.weekdays,
                            doseAmount = draft.doseAmount,
                            foodRelation = state.foodRelation,
                        )

                    if (state.medicationId == null) {
                        val newId = medicationRepository.add(medication)
                        scheduleRepository.add(schedule.copy(medicationId = newId))
                    } else {
                        medicationRepository.update(medication)
                        if (existingScheduleId != null) {
                            scheduleRepository.update(schedule)
                        } else {
                            scheduleRepository.add(schedule)
                        }
                    }
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                    // Regenerate doses and reschedule alarms for the new or
                    // edited schedule (T5); refresh never throws.
                    reminderEngine.refresh()
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                }
            }
        }

        /** Any field edit clears previous validation errors (revalidated on save). */
        private fun updateState(transform: (MedicationEditUiState) -> MedicationEditUiState) {
            _uiState.update { state ->
                transform(state).let { if (it != state) it.copy(errors = emptySet()) else it }
            }
        }
    }
