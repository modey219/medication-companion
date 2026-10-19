package com.medcompanion.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medcompanion.app.core.di.PeriodicTicker
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.data.export.HistoryPdfExporter
import com.medcompanion.app.domain.history.AdherenceSummary
import com.medcompanion.app.domain.history.DoseRecord
import com.medcompanion.app.domain.history.HistoryReport
import com.medcompanion.app.domain.history.MedicationRecord
import com.medcompanion.app.domain.history.SymptomRecord
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.repository.SymptomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * History tab: the timeline of doses, symptoms and started medications for the
 * chosen window, the adherence summary and the PDF export (AGENTS.md > Part C,
 * T7).
 *
 * Both period windows end now and start at local midnight (`days - 1` days
 * back), so "7 days" means today plus the six days before it.
 *
 * The export writes a file in the app cache and hands a content Uri to the
 * share sheet; nothing leaves the device until the patient picks an app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val doseRepository: DoseEventRepository,
        private val symptomRepository: SymptomRepository,
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val exporter: HistoryPdfExporter,
        private val clock: AppClock,
        @PeriodicTicker private val ticker: Flow<Instant>,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HistoryUiState())
        val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

        private val period = MutableStateFlow(HistoryPeriod.LAST_7_DAYS)

        /** Built alongside the timeline; the export sends the same numbers. */
        private var report: HistoryReport? = null

        init {
            viewModelScope.launch {
                combine(period, ticker) { selected, now -> selected to now }
                    .flatMapLatest { (selected, now) -> windowFlow(selected, now) }
                    .collect { snapshot ->
                        report = snapshot.report
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                dayGroups = snapshot.groups,
                                summary = snapshot.summary,
                            )
                        }
                    }
            }
        }

        fun onPeriodChange(newPeriod: HistoryPeriod) {
            if (period.value == newPeriod) return
            _uiState.update { it.copy(period = newPeriod, isLoading = true, error = null) }
            period.value = newPeriod
        }

        fun dismissError() = _uiState.update { it.copy(error = null) }

        /** Builds the PDF for the current period and offers it to the share sheet. */
        fun export() {
            val current = report ?: return
            if (_uiState.value.isExporting) return

            viewModelScope.launch {
                _uiState.update { it.copy(isExporting = true, error = null) }
                try {
                    val uri = withContext(Dispatchers.IO) { exporter.export(current, clock.now()) }
                    _uiState.update { it.copy(isExporting = false, shareUri = uri) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(isExporting = false, error = HistoryError.EXPORT_FAILED) }
                }
            }
        }

        /** Called once the chooser was shown, so it is not launched twice. */
        fun onShareLaunched() = _uiState.update { it.copy(shareUri = null) }

        /** Called when no app could take the file; the patient can try again. */
        fun onShareFailed() = _uiState.update { it.copy(shareUri = null, error = HistoryError.EXPORT_FAILED) }

        private fun windowFlow(
            period: HistoryPeriod,
            now: Instant,
        ): Flow<HistorySnapshot> {
            val zone = clock.zone()
            val start =
                now
                    .atZone(zone)
                    .toLocalDate()
                    .minusDays(period.days - 1)
                    .atStartOfDay(zone)
                    .toInstant()
            return combine(
                doseRepository.observeBetween(start, now),
                symptomRepository.observeBetween(start, now),
                medicationRepository.observeAll(),
                scheduleRepository.observeAll(),
            ) { doses, symptoms, medications, schedules ->
                buildSnapshot(
                    start = start,
                    end = now,
                    doses = doses,
                    symptoms = symptoms,
                    medications = medications,
                    doseAmounts = schedules.associate { it.medicationId to it.doseAmount },
                )
            }
        }

        private fun buildSnapshot(
            start: Instant,
            end: Instant,
            doses: List<DoseEvent>,
            symptoms: List<SymptomLog>,
            medications: List<Medication>,
            doseAmounts: Map<Long, String>,
        ): HistorySnapshot {
            val zone = clock.zone()
            val medicationsById = medications.associateBy { it.id }

            val entries =
                (
                    doses.mapNotNull { dose ->
                        val medication = medicationsById[dose.medicationId] ?: return@mapNotNull null
                        HistoryEntry.DoseLogged(
                            medicationName = medication.name,
                            doseAmount = doseAmounts[dose.medicationId].orEmpty(),
                            status = dose.status,
                            at = dose.scheduledAt,
                        )
                    } +
                        symptoms.map { symptom ->
                            HistoryEntry.SymptomLogged(
                                name = symptom.name,
                                severity = symptom.severity,
                                note = symptom.note,
                                at = symptom.loggedAt,
                            )
                        } +
                        medications
                            .filter { it.startDate >= start && it.startDate < end }
                            .map { medication ->
                                HistoryEntry.MedicationStarted(
                                    name = medication.name,
                                    strength = medication.strength,
                                    at = medication.startDate,
                                )
                            }
                ).sortedByDescending { it.at }

            val groups =
                entries
                    .groupBy { it.at.atZone(zone).toLocalDate() }
                    .map { (date, dayEntries) -> HistoryDayGroup(date = date, entries = dayEntries) }
                    .sortedByDescending { it.date }

            val summary = summarize(doses)
            return HistorySnapshot(
                groups = groups,
                summary = summary,
                report =
                    HistoryReport(
                        periodStart = start,
                        periodEnd = end,
                        summary = summary,
                        doses =
                            doses.sortedByDescending { it.scheduledAt }.map { dose ->
                                DoseRecord(
                                    medicationName = medicationsById[dose.medicationId]?.name.orEmpty(),
                                    doseAmount = doseAmounts[dose.medicationId].orEmpty(),
                                    scheduledAt = dose.scheduledAt,
                                    status = dose.status,
                                )
                            },
                        symptoms =
                            symptoms.sortedByDescending { it.loggedAt }.map { symptom ->
                                SymptomRecord(
                                    name = symptom.name,
                                    severity = symptom.severity,
                                    loggedAt = symptom.loggedAt,
                                    note = symptom.note,
                                )
                            },
                        medications =
                            medications
                                .filter { it.startDate >= start && it.startDate < end }
                                .sortedByDescending { it.startDate }
                                .map { medication ->
                                    MedicationRecord(
                                        name = medication.name,
                                        strength = medication.strength,
                                        startDate = medication.startDate,
                                        status = medication.status,
                                    )
                                },
                    ),
            )
        }

        private fun summarize(doses: List<DoseEvent>): AdherenceSummary =
            AdherenceSummary(
                taken = doses.count { it.status == DoseStatus.TAKEN },
                missed = doses.count { it.status == DoseStatus.MISSED },
                skipped = doses.count { it.status == DoseStatus.SKIPPED },
                pending = doses.count { it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED },
            )
    }

private data class HistorySnapshot(
    val groups: List<HistoryDayGroup>,
    val summary: AdherenceSummary,
    val report: HistoryReport,
)
