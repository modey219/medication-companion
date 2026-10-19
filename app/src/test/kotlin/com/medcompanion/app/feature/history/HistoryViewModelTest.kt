package com.medcompanion.app.feature.history

import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.data.export.HistoryPdfExporter
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.repository.SymptomRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds

/**
 * The History window, the timeline grouping and the PDF hand-off (T7).
 *
 * The 7-day window is "today plus the six days before it", starting at local
 * midnight, so the tests pin the boundary instants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val clock = FakeAppClock() // fixed at 2026-10-17T12:00:00Z, UTC
    private val doseRepository: DoseEventRepository = mockk(relaxed = true)
    private val symptomRepository: SymptomRepository = mockk(relaxed = true)
    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)
    private val exporter: HistoryPdfExporter = mockk(relaxed = true)
    private val ticker = MutableStateFlow(clock.now())

    /** Every window start the ViewModel asked the repository for. */
    private val windowStarts = mutableListOf<Instant>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        HistoryViewModel(
            doseRepository = doseRepository,
            symptomRepository = symptomRepository,
            medicationRepository = medicationRepository,
            scheduleRepository = scheduleRepository,
            exporter = exporter,
            clock = clock,
            ticker = ticker,
        )

    private fun stub(
        doses: List<DoseEvent> = fixtureDoses,
        symptoms: List<SymptomLog> = fixtureSymptoms,
        medications: List<Medication> = fixtureMedications,
        schedules: List<Schedule> = fixtureSchedules,
    ) {
        every { doseRepository.observeBetween(any(), any()) } answers {
            windowStarts += firstArg<Instant>()
            MutableStateFlow(
                doses.filter {
                    it.scheduledAt >= firstArg<Instant>() && it.scheduledAt <= secondArg()
                },
            )
        }
        every { symptomRepository.observeBetween(any(), any()) } answers {
            MutableStateFlow(
                symptoms.filter {
                    it.loggedAt >= firstArg<Instant>() && it.loggedAt <= secondArg()
                },
            )
        }
        every { medicationRepository.observeAll() } returns MutableStateFlow(medications)
        every { scheduleRepository.observeAll() } returns MutableStateFlow(schedules)
    }

    private suspend fun readyVm(
        doses: List<DoseEvent> = fixtureDoses,
        symptoms: List<SymptomLog> = fixtureSymptoms,
        medications: List<Medication> = fixtureMedications,
        schedules: List<Schedule> = fixtureSchedules,
    ): HistoryViewModel {
        stub(doses = doses, symptoms = symptoms, medications = medications, schedules = schedules)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        return viewModel
    }

    // --- The window ---

    @Test
    fun `the seven day window starts at local midnight six days ago`() =
        runTest(timeout = 10.seconds) {
            readyVm()

            assertEquals(listOf(Instant.parse("2026-10-11T00:00:00Z")), windowStarts.distinct())
        }

    @Test
    fun `switching to thirty days starts the window twenty nine days ago`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()

            viewModel.onPeriodChange(HistoryPeriod.LAST_30_DAYS)
            viewModel.uiState.first { it.period == HistoryPeriod.LAST_30_DAYS && !it.isLoading }

            assertEquals(Instant.parse("2026-09-18T00:00:00Z"), windowStarts.last())
        }

    // --- The timeline ---

    @Test
    fun `the timeline is grouped by local day, newest day first`() =
        runTest(timeout = 10.seconds) {
            val state = readyVm().uiState.value

            assertEquals(
                listOf(
                    LocalDate.of(2026, 10, 17),
                    LocalDate.of(2026, 10, 16),
                    LocalDate.of(2026, 10, 15),
                    LocalDate.of(2026, 10, 14),
                    LocalDate.of(2026, 10, 11),
                ),
                state.dayGroups.map { it.date },
            )
        }

    @Test
    fun `entries inside a day are newest first`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()

            val day =
                viewModel.uiState.value.dayGroups
                    .first { it.date == LocalDate.of(2026, 10, 16) }

            assertEquals(
                listOf(
                    "2026-10-16T20:00:00Z",
                    "2026-10-16T18:00:00Z",
                    "2026-10-16T08:00:00Z",
                ),
                day.entries.map { it.at.toString() },
            )
            assertTrue(day.entries.any { it is HistoryEntry.MedicationStarted })
        }

    // --- The summary ---

    @Test
    fun `the summary counts what the patient recorded`() =
        runTest(timeout = 10.seconds) {
            val summary = readyVm().uiState.value.summary

            assertEquals(3, summary.taken)
            assertEquals(1, summary.missed)
            assertEquals(1, summary.skipped)
            assertEquals(2, summary.pending)
            assertEquals(60, summary.takenPercent)
        }

    @Test
    fun `nothing acted on yet shows no percentage`() =
        runTest(timeout = 10.seconds) {
            val viewModel =
                readyVm(doses = listOf(dose(id = 1, at = "2026-10-17T08:00:00Z", status = DoseStatus.PENDING)))

            val summary = viewModel.uiState.value.summary

            assertEquals(1, summary.pending)
            assertEquals(0, summary.acted)
            assertNull(summary.takenPercent)
        }

    @Test
    fun `a dose of a medication that is no longer known counts in the summary but not the timeline`() =
        runTest(timeout = 10.seconds) {
            val viewModel =
                readyVm(
                    doses =
                        listOf(
                            dose(
                                id = 9,
                                at = "2026-10-17T07:00:00Z",
                                status = DoseStatus.TAKEN,
                                medicationId = 99,
                            ),
                        ),
                    symptoms = emptyList(),
                    medications = listOf(medication(id = 1)),
                )

            val state = viewModel.uiState.value

            assertEquals(1, state.summary.taken)
            assertTrue(state.dayGroups.isEmpty())
        }

    // --- Started medications ---

    @Test
    fun `a medication appears as started only when its start date is in the window`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()
            assertFalse(startedNames(viewModel).contains(ACTIVE_MEDICATION_NAME))

            viewModel.onPeriodChange(HistoryPeriod.LAST_30_DAYS)
            viewModel.uiState.first { it.period == HistoryPeriod.LAST_30_DAYS && !it.isLoading }

            assertTrue(startedNames(viewModel).contains(ACTIVE_MEDICATION_NAME))
        }

    @Test
    fun `a started medication that began in the window is shown with its start day`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()

            val sixteenth =
                viewModel.uiState.value.dayGroups
                    .first { it.date == LocalDate.of(2026, 10, 16) }
            val started = sixteenth.entries.filterIsInstance<HistoryEntry.MedicationStarted>()

            assertEquals(listOf(SECOND_MEDICATION_NAME), started.map { it.name })
            assertEquals(Instant.parse("2026-10-16T18:00:00Z"), started.single().at)
        }

    // --- Dose details ---

    @Test
    fun `a dose line carries the amount from the schedule`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()

            val today =
                viewModel.uiState.value.dayGroups
                    .first { it.date == LocalDate.of(2026, 10, 17) }
            val dose = today.entries.filterIsInstance<HistoryEntry.DoseLogged>().first()

            assertEquals("حبة واحدة", dose.doseAmount)
            assertEquals(DoseStatus.TAKEN, dose.status)
            assertEquals(ACTIVE_MEDICATION_NAME, dose.medicationName)
        }

    // --- Export ---

    @Test
    fun `export offers the file to the share sheet and clears it once launched`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()
            every { exporter.export(any(), any()) } returns SHARE_URI

            viewModel.export()

            val state = viewModel.uiState.first { it.shareUri != null }
            assertEquals(SHARE_URI, state.shareUri)
            assertFalse(state.isExporting)
            assertNull(state.error)

            viewModel.onShareLaunched()
            assertNull(viewModel.uiState.value.shareUri)
        }

    @Test
    fun `a failed export reports an error and offers no file`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()
            every { exporter.export(any(), any()) } throws IllegalStateException("disk full")

            viewModel.export()

            val state = viewModel.uiState.first { it.error != null }
            assertEquals(HistoryError.EXPORT_FAILED, state.error)
            assertNull(state.shareUri)
            assertFalse(state.isExporting)
        }

    @Test
    fun `a share that no app could take reports an error and can be dismissed`() =
        runTest(timeout = 10.seconds) {
            val viewModel = readyVm()
            every { exporter.export(any(), any()) } returns SHARE_URI
            viewModel.export()
            viewModel.uiState.first { it.shareUri != null }

            viewModel.onShareFailed()
            assertEquals(HistoryError.EXPORT_FAILED, viewModel.uiState.value.error)

            viewModel.dismissError()
            assertNull(viewModel.uiState.value.error)
        }

    private fun startedNames(viewModel: HistoryViewModel): List<String> =
        viewModel.uiState.value.dayGroups
            .flatMap { it.entries }
            .filterIsInstance<HistoryEntry.MedicationStarted>()
            .map { it.name }

    private companion object {
        const val ACTIVE_MEDICATION_NAME = "ميتفورمين"
        const val SECOND_MEDICATION_NAME = "فيتامين د"
        const val SHARE_URI = "content://com.medcompanion.app.fileprovider/exports/history-1.pdf"

        val fixtureDoses =
            listOf(
                dose(id = 1, at = "2026-10-17T08:00:00Z", status = DoseStatus.TAKEN),
                dose(id = 2, at = "2026-10-16T20:00:00Z", status = DoseStatus.MISSED),
                dose(id = 3, at = "2026-10-16T08:00:00Z", status = DoseStatus.SKIPPED),
                dose(id = 4, at = "2026-10-15T08:00:00Z", status = DoseStatus.SNOOZED),
                dose(id = 5, at = "2026-10-15T09:00:00Z", status = DoseStatus.PENDING),
                dose(id = 6, at = "2026-10-10T23:59:00Z", status = DoseStatus.TAKEN),
                dose(id = 7, at = "2026-10-11T00:00:00Z", status = DoseStatus.TAKEN),
                dose(id = 8, at = "2026-10-14T08:00:00Z", status = DoseStatus.TAKEN),
            )

        val fixtureSymptoms = listOf(symptom(id = 1, at = "2026-10-17T09:30:00Z"))

        val fixtureMedications =
            listOf(
                medication(id = 1, name = ACTIVE_MEDICATION_NAME),
                medication(id = 2, name = SECOND_MEDICATION_NAME, startDate = "2026-10-16T18:00:00Z"),
            )

        val fixtureSchedules = listOf(schedule(medicationId = 1))
    }
}

private fun dose(
    id: Long,
    at: String,
    status: DoseStatus = DoseStatus.PENDING,
    medicationId: Long = 1,
): DoseEvent =
    DoseEvent(
        id = id,
        medicationId = medicationId,
        scheduledAt = Instant.parse(at),
        status = status,
    )

private fun symptom(
    id: Long,
    at: String,
    name: String = "صداع",
    severity: Int = 3,
    note: String? = null,
): SymptomLog = SymptomLog(id = id, loggedAt = Instant.parse(at), name = name, severity = severity, note = note)

private fun medication(
    id: Long,
    name: String = "ميتفورمين",
    startDate: String = "2026-10-01T08:00:00Z",
): Medication =
    Medication(
        id = id,
        name = name,
        form = MedicationForm.TABLET,
        strength = "500 ملغ",
        instructions = "",
        source = MedicationSource.SELF,
        startDate = Instant.parse(startDate),
        createdAt = Instant.parse(startDate),
    )

private fun schedule(
    medicationId: Long = 1,
    doseAmount: String = "حبة واحدة",
) = Schedule(
    id = 1,
    medicationId = medicationId,
    type = ScheduleType.DAILY_TIMES,
    times = listOf(LocalTime.of(8, 0)),
    doseAmount = doseAmount,
    foodRelation = FoodRelation.ANY,
)
