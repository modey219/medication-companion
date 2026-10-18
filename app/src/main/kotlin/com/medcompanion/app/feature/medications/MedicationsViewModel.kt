package com.medcompanion.app.feature.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * My medications tab: lists active, completed and stopped medications and
 * performs the stop action. Add/edit lives in [MedicationEditViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicationsViewModel
    @Inject
    constructor(
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val reminderEngine: ReminderEngine,
        private val clock: AppClock,
    ) : ViewModel() {
        val uiState: StateFlow<MedicationsUiState> =
            medicationRepository
                .observeAll()
                .flatMapLatest { medications ->
                    if (medications.isEmpty()) {
                        flowOf(medications to emptyMap<Long, Schedule>())
                    } else {
                        combine(
                            medications.map { medication ->
                                scheduleRepository
                                    .observeByMedication(medication.id)
                                    .map { schedule -> medication.id to schedule.firstOrNull() }
                            },
                        ) { schedulePairs ->
                            medications to schedulePairs.toMap()
                        }
                    }
                }.map { (medications, schedules) ->
                    val items = medications.map { it.toListItem(schedules[it.id]) }
                    MedicationsUiState(
                        isLoading = false,
                        active = items.filter { it.status == MedicationStatus.ACTIVE },
                        completed = items.filter { it.status == MedicationStatus.COMPLETED },
                        stopped = items.filter { it.status == MedicationStatus.STOPPED },
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = MedicationsUiState(),
                )

        /**
         * Stops a medication now; the reason is optional. Stopping deletes the
         * medication's future dose events and cancels their alarms through the
         * engine refresh (past history is kept).
         */
        fun stop(
            medicationId: Long,
            reason: String?,
        ) {
            viewModelScope.launch {
                medicationRepository.stop(
                    id = medicationId,
                    reason = reason?.trim()?.ifEmpty { null },
                    endDate = clock.now(),
                )
                reminderEngine.refresh()
            }
        }

        private fun Medication.toListItem(schedule: Schedule?): MedicationListItem =
            MedicationListItem(
                id = id,
                name = name,
                strength = strength,
                form = form,
                status = status,
                startDate = startDate,
                endDate = endDate,
                stopReason = stopReason,
                scheduleType = schedule?.type,
                times = schedule?.times.orEmpty(),
                intervalHours = schedule?.intervalHours,
                weekdays = schedule?.weekdays.orEmpty(),
                foodRelation = schedule?.foodRelation ?: FoodRelation.ANY,
            )
    }
