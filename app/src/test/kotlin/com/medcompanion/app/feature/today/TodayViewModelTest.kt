package com.medcompanion.app.feature.today

import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.settings.FakeSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val clock = FakeAppClock() // fixed at 2026-10-17T12:00:00Z, UTC
    private val doseRepository: DoseEventRepository = mockk(relaxed = true)
    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)
    private val reminderEngine: ReminderEngine = mockk(relaxed = true)
    private val permissionChecker: ReminderPermissionChecker = mockk(relaxed = true)
    private val settings = FakeSettingsRepository()
    private val doses = MutableStateFlow<List<DoseEvent>>(emptyList())
    private val nextDose = MutableStateFlow<DoseEvent?>(null)
    private val medications = MutableStateFlow<List<Medication>>(emptyList())
    private val schedules = MutableStateFlow<List<Schedule>>(emptyList())
    private val ticker = MutableStateFlow(clock.now())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { doseRepository.observeBetween(any(), any()) } returns doses
        every { doseRepository.observeNextAfter(any()) } returns nextDose
        every { medicationRepository.observeAll() } returns medications
        every { scheduleRepository.observeAll() } returns schedules
        every { permissionChecker.isReminderSetupComplete() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        TodayViewModel(
            doseRepository = doseRepository,
            medicationRepository = medicationRepository,
            scheduleRepository = scheduleRepository,
            reminderEngine = reminderEngine,
            permissionChecker = permissionChecker,
            settingsRepository = settings,
            clock = clock,
            ticker = ticker,
        )

    private suspend fun awaitLoaded(vm: TodayViewModel) = vm.uiState.first { !it.isLoading }

    // --- Empty states ---

    @Test
    fun `no doses today shows the empty state`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))

            val state = awaitLoaded(viewModel())

            assertTrue(state.isEmpty)
            assertFalse(state.showAddMedicationCta)
        }

    @Test
    fun `no medications at all offers the add medication call to action`() =
        runTest(timeout = 10.seconds) {
            val state = awaitLoaded(viewModel())

            assertTrue(state.showAddMedicationCta)
        }

    // --- Next-dose hint ---

    @Test
    fun `an empty today still surfaces the next scheduled dose`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            nextDose.value = dose(id = 40, medicationId = 1, at = "2026-10-18T08:00:00Z")

            val state = awaitLoaded(viewModel())

            assertTrue(state.isEmpty)
            assertEquals(Instant.parse("2026-10-18T08:00:00Z"), state.nextDoseAt)
        }

    @Test
    fun `next dose hint is null when nothing is scheduled ahead`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))

            assertNull(awaitLoaded(viewModel()).nextDoseAt)
        }

    @Test
    fun `next dose hint disappears once the dose is taken`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            val upcoming = dose(id = 40, medicationId = 1, at = "2026-10-18T08:00:00Z")
            nextDose.value = upcoming
            val vm = viewModel()
            awaitLoaded(vm)
            assertEquals(Instant.parse("2026-10-18T08:00:00Z"), vm.uiState.value.nextDoseAt)

            nextDose.value = null

            assertNull(vm.uiState.value.nextDoseAt)
        }

    // --- Grouping ---

    @Test
    fun `doses are grouped by time sorted ascending and rows by name`() =
        runTest(timeout = 10.seconds) {
            medications.value =
                listOf(
                    medication(id = 1, name = "Metformin"),
                    medication(id = 2, name = "Aspirin"),
                )
            doses.value =
                listOf(
                    dose(id = 10, medicationId = 1, at = "2026-10-17T08:00:00Z"),
                    dose(id = 11, medicationId = 2, at = "2026-10-17T08:00:00Z"),
                    dose(id = 12, medicationId = 1, at = "2026-10-17T20:00:00Z"),
                )

            val state = awaitLoaded(viewModel())

            assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), state.groups.map { it.time })
            assertEquals(listOf("Aspirin", "Metformin"), state.groups[0].doses.map { it.name })
            assertEquals(1, state.groups[1].doses.size)
        }

    @Test
    fun `dose rows carry schedule details for display`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            schedules.value =
                listOf(
                    schedule(
                        medicationId = 1,
                        doseAmount = "قرص واحد",
                        foodRelation = FoodRelation.AFTER,
                    ),
                )
            doses.value = listOf(dose(id = 10, medicationId = 1, at = "2026-10-17T08:00:00Z"))

            val row =
                awaitLoaded(viewModel())
                    .groups
                    .single()
                    .doses
                    .single()

            assertEquals("قرص واحد", row.doseAmount)
            assertEquals(FoodRelation.AFTER, row.foodRelation)
            assertEquals("Medication 1", row.name)
        }

    // --- Overdue and actionability ---

    @Test
    fun `overdue is computed against the injected clock`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            doses.value =
                listOf(
                    dose(id = 10, medicationId = 1, at = "2026-10-17T08:00:00Z"), // before 12:00
                    dose(id = 11, medicationId = 1, at = "2026-10-17T14:00:00Z"), // after 12:00
                    dose(id = 12, medicationId = 1, at = "2026-10-17T08:00:00Z", status = DoseStatus.TAKEN),
                )

            val rows = awaitLoaded(viewModel()).groups.flatMap { it.doses }.associateBy { it.id }

            assertTrue(rows.getValue(10).isOverdue)
            assertFalse(rows.getValue(11).isOverdue)
            assertFalse(rows.getValue(12).isOverdue)
        }

    @Test
    fun `only pending and snoozed doses are actionable`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            doses.value =
                listOf(
                    dose(id = 10, medicationId = 1, at = "2026-10-17T08:00:00Z", status = DoseStatus.PENDING),
                    dose(id = 11, medicationId = 1, at = "2026-10-17T09:00:00Z", status = DoseStatus.SNOOZED),
                    dose(id = 12, medicationId = 1, at = "2026-10-17T10:00:00Z", status = DoseStatus.TAKEN),
                    dose(id = 13, medicationId = 1, at = "2026-10-17T11:00:00Z", status = DoseStatus.SKIPPED),
                    dose(id = 14, medicationId = 1, at = "2026-10-17T11:30:00Z", status = DoseStatus.MISSED),
                )

            val rows = awaitLoaded(viewModel()).groups.flatMap { it.doses }.associateBy { it.id }

            assertTrue(rows.getValue(10).isActionable)
            assertTrue(rows.getValue(11).isActionable)
            assertFalse(rows.getValue(12).isActionable)
            assertFalse(rows.getValue(13).isActionable)
            assertFalse(rows.getValue(14).isActionable)
        }

    @Test
    fun `snoozed dose shows the snooze end ten minutes after the action`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            doses.value =
                listOf(
                    dose(
                        id = 10,
                        medicationId = 1,
                        at = "2026-10-17T11:50:00Z",
                        status = DoseStatus.SNOOZED,
                        actedAt = Instant.parse("2026-10-17T11:50:00Z"),
                    ),
                )

            val row =
                awaitLoaded(viewModel())
                    .groups
                    .single()
                    .doses
                    .single()

            assertEquals(Instant.parse("2026-10-17T12:00:00Z"), row.snoozedUntil)
        }

    // --- Actions ---

    @Test
    fun `the snooze end follows the snooze length the patient chose`() =
        runTest(timeout = 10.seconds) {
            settings.setSnoozeMinutes(20)
            medications.value = listOf(medication(id = 1))
            doses.value =
                listOf(
                    dose(
                        id = 10,
                        medicationId = 1,
                        at = "2026-10-17T11:50:00Z",
                        status = DoseStatus.SNOOZED,
                        actedAt = Instant.parse("2026-10-17T11:50:00Z"),
                    ),
                )

            val row =
                awaitLoaded(viewModel())
                    .groups
                    .single()
                    .doses
                    .single()

            assertEquals(Instant.parse("2026-10-17T12:10:00Z"), row.snoozedUntil)
        }

    @Test
    fun `actions go through the reminder engine`() =
        runTest(timeout = 10.seconds) {
            val vm = viewModel()
            awaitLoaded(vm)

            vm.onTaken(1L)
            vm.onSkipped(2L)
            vm.onSnoozed(3L)

            coVerify { reminderEngine.performAction(1L, DoseStatus.TAKEN) }
            coVerify { reminderEngine.performAction(2L, DoseStatus.SKIPPED) }
            coVerify { reminderEngine.performAction(3L, DoseStatus.SNOOZED) }
        }

    @Test
    fun `failed action sets an error and dismiss clears it`() =
        runTest(timeout = 10.seconds) {
            coEvery { reminderEngine.performAction(any(), any()) } throws IllegalStateException("disk full")
            val vm = viewModel()
            awaitLoaded(vm)

            vm.onTaken(1L)

            assertEquals(TodayError.UPDATE_FAILED, vm.uiState.value.error)

            vm.dismissError()

            assertNull(vm.uiState.value.error)
        }

    // --- Reminder setup banner (T5) ---

    @Test
    fun `the banner shows while reminder setup is incomplete`() =
        runTest(timeout = 10.seconds) {
            every { permissionChecker.isReminderSetupComplete() } returns false

            assertTrue(awaitLoaded(viewModel()).reminderSetupIncomplete)
        }

    @Test
    fun `resuming the screen re-reads the reminder permissions`() =
        runTest(timeout = 10.seconds) {
            val vm = viewModel()
            assertFalse(awaitLoaded(vm).reminderSetupIncomplete)

            every { permissionChecker.isReminderSetupComplete() } returns false
            vm.onScreenResumed()

            assertTrue(vm.uiState.value.reminderSetupIncomplete)
        }

    // --- Live refresh ---

    @Test
    fun `observation window is today only`() =
        runTest(timeout = 10.seconds) {
            awaitLoaded(viewModel())

            verify {
                doseRepository.observeBetween(
                    Instant.parse("2026-10-17T00:00:00Z"),
                    Instant.parse("2026-10-18T00:00:00Z"),
                )
            }
        }

    @Test
    fun `overdue flag refreshes on the periodic tick`() =
        runTest(timeout = 10.seconds) {
            medications.value = listOf(medication(id = 1))
            doses.value = listOf(dose(id = 10, medicationId = 1, at = "2026-10-17T13:00:00Z"))
            val vm = viewModel()
            assertFalse(
                awaitLoaded(vm)
                    .groups
                    .single()
                    .doses
                    .single()
                    .isOverdue,
            )

            clock.setNow(Instant.parse("2026-10-17T13:30:00Z"))
            ticker.value = clock.now()

            assertTrue(
                vm.uiState.value.groups
                    .single()
                    .doses
                    .single()
                    .isOverdue,
            )
        }

    @Test
    fun `observation window follows day changes on the tick`() =
        runTest(timeout = 10.seconds) {
            awaitLoaded(viewModel())

            clock.setNow(Instant.parse("2026-10-18T00:01:00Z"))
            ticker.value = clock.now()

            verify(atLeast = 1) {
                doseRepository.observeBetween(
                    Instant.parse("2026-10-18T00:00:00Z"),
                    Instant.parse("2026-10-19T00:00:00Z"),
                )
            }
        }

    private fun medication(
        id: Long,
        name: String = "Medication $id",
    ) = Medication(
        id = id,
        name = name,
        form = MedicationForm.TABLET,
        strength = "500 mg",
        instructions = "",
        source = MedicationSource.SELF,
        startDate = Instant.parse("2026-10-01T00:00:00Z"),
        endDate = null,
        status = MedicationStatus.ACTIVE,
        stopReason = null,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun dose(
        id: Long,
        medicationId: Long,
        at: String,
        status: DoseStatus = DoseStatus.PENDING,
        actedAt: Instant? = null,
    ) = DoseEvent(
        id = id,
        medicationId = medicationId,
        scheduledAt = Instant.parse(at),
        status = status,
        actedAt = actedAt,
        note = null,
    )

    private fun schedule(
        medicationId: Long,
        doseAmount: String,
        foodRelation: FoodRelation,
    ) = Schedule(
        id = 100L + medicationId,
        medicationId = medicationId,
        type = ScheduleType.DAILY_TIMES,
        times = listOf(LocalTime.of(8, 0)),
        intervalHours = null,
        weekdays = emptySet(),
        doseAmount = doseAmount,
        foodRelation = foodRelation,
    )
}
