package com.medcompanion.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.di.PeriodicTicker
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Today tab: today's doses grouped by time with Taken / Snooze / Skip actions.
 *
 * Actions go through [ReminderEngine.performAction] so alarms and
 * notifications are cancelled in the same step (T5). Missed-dose transitions
 * (grace period) are the engine's job, not the screen's.
 *
 * A tick every 30 seconds recomputes overdue flags so the screen stays
 * correct while it is open, without any database change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val doseRepository: DoseEventRepository,
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val reminderEngine: ReminderEngine,
        private val permissionChecker: ReminderPermissionChecker,
        private val settingsRepository: SettingsRepository,
        private val clock: AppClock,
        @PeriodicTicker private val ticker: Flow<Instant>,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TodayUiState())
        val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                ticker
                    .flatMapLatest { now ->
                        val zone = clock.zone()
                        val today = now.atZone(zone).toLocalDate()
                        combine(
                            doseRepository.observeBetween(
                                today.atStartOfDay(zone).toInstant(),
                                today.plusDays(1).atStartOfDay(zone).toInstant(),
                            ),
                            medicationRepository.observeAll(),
                            scheduleRepository.observeAll(),
                            doseRepository.observeNextAfter(now),
                            settingsRepository.observe(),
                        ) { doses, medications, schedules, nextDose, settings ->
                            buildState(doses, medications, schedules, nextDose, settings, now)
                        }
                    }.collect { incoming ->
                        // An in-flight action error survives data refreshes; only
                        // a successful action or dismissError() clears it.
                        _uiState.update { current -> incoming.copy(error = current.error) }
                    }
            }
        }

        fun onTaken(doseId: Long) = act(doseId, DoseStatus.TAKEN)

        fun onSnoozed(doseId: Long) = act(doseId, DoseStatus.SNOOZED)

        fun onSkipped(doseId: Long) = act(doseId, DoseStatus.SKIPPED)

        fun dismissError() = _uiState.update { it.copy(error = null) }

        /**
         * Called when the screen is resumed (and after returning from the
         * reminder setup screen): re-reads permissions for the banner.
         */
        fun onScreenResumed() =
            _uiState.update {
                it.copy(reminderSetupIncomplete = !permissionChecker.isReminderSetupComplete())
            }

        private fun act(
            doseId: Long,
            status: DoseStatus,
        ) {
            viewModelScope.launch {
                try {
                    reminderEngine.performAction(doseId, status)
                    _uiState.update { it.copy(error = null) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = TodayError.UPDATE_FAILED) }
                }
            }
        }

        private fun buildState(
            doses: List<DoseEvent>,
            medications: List<Medication>,
            schedules: List<Schedule>,
            nextDose: DoseEvent?,
            settings: AppSettings,
            now: Instant,
        ): TodayUiState {
            val zone = clock.zone()
            val medicationsById = medications.associateBy { it.id }
            val scheduleByMedication = schedules.associateBy { it.medicationId }

            val rows =
                doses.mapNotNull { dose ->
                    val medication = medicationsById[dose.medicationId] ?: return@mapNotNull null
                    val schedule = scheduleByMedication[dose.medicationId]
                    DoseRow(
                        id = dose.id,
                        medicationId = dose.medicationId,
                        name = medication.name,
                        strength = medication.strength,
                        doseAmount = schedule?.doseAmount.orEmpty(),
                        foodRelation = schedule?.foodRelation,
                        status = dose.status,
                        scheduledAt = dose.scheduledAt,
                        isOverdue = dose.status == DoseStatus.PENDING && dose.scheduledAt < now,
                        isActionable = dose.status == DoseStatus.PENDING || dose.status == DoseStatus.SNOOZED,
                        snoozedUntil =
                            if (dose.status == DoseStatus.SNOOZED) {
                                dose.actedAt?.plusSeconds(settings.snoozeMinutes * SECONDS_PER_MINUTE)
                            } else {
                                null
                            },
                    )
                }

            val groups =
                rows
                    .groupBy { it.scheduledAt.atZone(zone).toLocalTime() }
                    .map { (time, groupRows) -> DoseGroup(time = time, doses = groupRows.sortedBy { it.name }) }
                    .sortedBy { it.time }

            return TodayUiState(
                isLoading = false,
                groups = groups,
                hasMedications = medications.isNotEmpty(),
                reminderSetupIncomplete = !permissionChecker.isReminderSetupComplete(),
                nextDoseAt = nextDose?.scheduledAt,
            )
        }

        private companion object {
            const val SECONDS_PER_MINUTE = 60L
        }
    }
