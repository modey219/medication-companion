package com.medcompanion.app.feature.medications

import com.medcompanion.app.core.time.FakeAppClock
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
import io.mockk.coVerify
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationsViewModelTest {
    private val clock = FakeAppClock()
    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)
    private val reminderEngine: ReminderEngine = mockk(relaxed = true)
    private val medications = MutableStateFlow<List<Medication>>(emptyList())
    private val schedules = MutableStateFlow<Map<Long, List<Schedule>>>(emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { medicationRepository.observeAll() } returns medications
        every { scheduleRepository.observeByMedication(any()) } answers {
            MutableStateFlow(schedules.value[firstArg()] ?: emptyList())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        MedicationsViewModel(
            medicationRepository = medicationRepository,
            scheduleRepository = scheduleRepository,
            reminderEngine = reminderEngine,
            clock = clock,
        )

    @Test
    fun `initial state is loading and empty list is reported as empty`() =
        runTest {
            val vm = viewModel()

            val state = vm.uiState.first { !it.isLoading }

            assertTrue(state.isEmpty)
            assertEquals(emptyList<MedicationListItem>(), state.active)
        }

    @Test
    fun `medications are grouped by status`() =
        runTest {
            medications.value =
                listOf(
                    medication(id = 1, name = "Active one", status = MedicationStatus.ACTIVE),
                    medication(id = 2, name = "Finished", status = MedicationStatus.COMPLETED),
                    medication(id = 3, name = "Stopped", status = MedicationStatus.STOPPED),
                    medication(id = 4, name = "Active two", status = MedicationStatus.ACTIVE),
                )

            val state = viewModel().uiState.first { !it.isLoading }

            assertEquals(listOf(1L, 4L), state.active.map { it.id })
            assertEquals(listOf(2L), state.completed.map { it.id })
            assertEquals(listOf(3L), state.stopped.map { it.id })
            assertFalse(state.isEmpty)
        }

    @Test
    fun `list items carry schedule summary data`() =
        runTest {
            medications.value = listOf(medication(id = 1, status = MedicationStatus.ACTIVE))
            schedules.value =
                mapOf(
                    1L to
                        listOf(
                            Schedule(
                                id = 10L,
                                medicationId = 1L,
                                type = ScheduleType.WEEKDAYS,
                                times = listOf(LocalTime.of(9, 0)),
                                intervalHours = null,
                                weekdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY),
                                doseAmount = "1 tablet",
                                foodRelation = FoodRelation.AFTER,
                            ),
                        ),
                )

            val item =
                viewModel()
                    .uiState
                    .first { !it.isLoading }
                    .active
                    .single()

            assertEquals(ScheduleType.WEEKDAYS, item.scheduleType)
            assertEquals(listOf(LocalTime.of(9, 0)), item.times)
            assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY), item.weekdays)
            assertEquals(FoodRelation.AFTER, item.foodRelation)
        }

    @Test
    fun `stock flags reflect expiry and low quantity`() =
        runTest {
            medications.value =
                listOf(
                    medication(
                        id = 1,
                        status = MedicationStatus.ACTIVE,
                        expiryDate = Instant.parse("2026-10-01T00:00:00Z"),
                        remainingDoses = 2,
                    ),
                    medication(
                        id = 2,
                        status = MedicationStatus.ACTIVE,
                        expiryDate = Instant.parse("2027-01-01T00:00:00Z"),
                        remainingDoses = 90,
                    ),
                    medication(id = 3, status = MedicationStatus.ACTIVE),
                )

            val items =
                viewModel()
                    .uiState
                    .first { !it.isLoading }
                    .active
                    .associateBy { it.id }

            val expiredLow = items.getValue(1L)
            assertTrue(expiredLow.isExpired)
            assertTrue(expiredLow.isLowStock)
            assertEquals(LocalDate.of(2026, 10, 1), expiredLow.expiryDate)
            assertEquals(2, expiredLow.remainingDoses)

            val healthy = items.getValue(2L)
            assertFalse(healthy.isExpired)
            assertFalse(healthy.isLowStock)

            val untracked = items.getValue(3L)
            assertFalse(untracked.isExpired)
            assertFalse(untracked.isLowStock)
            assertNull(untracked.expiryDate)
            assertNull(untracked.remainingDoses)
        }

    @Test
    fun `stop passes trimmed reason and the injected clock`() =
        runTest {
            val vm = viewModel()

            vm.stop(medicationId = 1, reason = "  no longer needed  ")

            coVerify {
                medicationRepository.stop(
                    id = 1,
                    reason = "no longer needed",
                    endDate = Instant.parse("2026-10-17T12:00:00Z"),
                )
            }
        }

    @Test
    fun `blank or missing stop reason becomes null`() =
        runTest {
            val vm = viewModel()

            vm.stop(medicationId = 1, reason = "   ")
            vm.stop(medicationId = 2, reason = null)

            coVerify { medicationRepository.stop(1, null, any()) }
            coVerify { medicationRepository.stop(2, null, any()) }
        }

    @Test
    fun `stop reason is kept when provided`() =
        runTest {
            val vm = viewModel()

            vm.stop(medicationId = 5, reason = "side effects")

            coVerify { medicationRepository.stop(5, "side effects", any()) }
        }

    @Test
    fun `stopping a medication clears its future dose reminders`() =
        runTest {
            val vm = viewModel()

            vm.stop(medicationId = 5, reason = "side effects")

            coVerify { reminderEngine.refresh() }
        }

    private fun medication(
        id: Long,
        name: String = "Medication $id",
        status: MedicationStatus,
        expiryDate: Instant? = null,
        remainingDoses: Int? = null,
    ) = Medication(
        id = id,
        name = name,
        form = MedicationForm.TABLET,
        strength = "500 mg",
        instructions = "",
        source = MedicationSource.SELF,
        startDate = Instant.parse("2026-10-01T00:00:00Z"),
        endDate = null,
        status = status,
        stopReason = null,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        expiryDate = expiryDate,
        openedAt = null,
        remainingDoses = remainingDoses,
    )
}
