package com.medcompanion.app.feature.medications.edit

import androidx.lifecycle.SavedStateHandle
import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.schedule.DraftError
import com.medcompanion.app.ui.navigation.ARG_MEDICATION_ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class MedicationEditViewModelTest {
    private val clock = FakeAppClock()
    private val medicationRepository: MedicationRepository = mockk(relaxed = true)
    private val scheduleRepository: ScheduleRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(id: Long? = null) =
        MedicationEditViewModel(
            medicationRepository = medicationRepository,
            scheduleRepository = scheduleRepository,
            clock = clock,
            savedStateHandle = SavedStateHandle(id?.let { mapOf(ARG_MEDICATION_ID to it) } ?: emptyMap()),
        )

    // --- Add flow ---

    @Test
    fun `new medication starts on today and not loading`() {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(LocalDate.of(2026, 10, 17), state.startDate)
        assertFalse(state.isEditing)
    }

    @Test
    fun `save with blank name reports validation and writes nothing`() =
        runTest {
            val vm = viewModel()
            vm.onDoseAmountChange("1 tablet")

            vm.save()

            val state = vm.uiState.value
            assertTrue(DraftError.NAME_REQUIRED in state.errors)
            assertFalse(state.isSaved)
            coVerify(exactly = 0) { medicationRepository.add(any()) }
        }

    @Test
    fun `save with empty times in daily mode reports validation`() =
        runTest {
            val vm = viewModel()
            vm.onNameChange("Metformin")
            vm.onDoseAmountChange("1 tablet")
            vm.onTimeRemoved(LocalTime.of(8, 0)) // clear the default slot

            vm.save()

            assertTrue(DraftError.TIMES_REQUIRED in vm.uiState.value.errors)
        }

    @Test
    fun `save writes medication and schedule with half-open dates`() =
        runTest {
            val saved = mutableListOf<Medication>()
            coEvery { medicationRepository.add(capture(saved)) } returns 11L
            val vm = viewModel()
            vm.onNameChange("  Metformin  ")
            vm.onDoseAmountChange("1 tablet")
            vm.onTimeAdded(LocalTime.of(8, 0))
            vm.onStartDateChange(LocalDate.of(2026, 10, 20))
            vm.onEndDateChange(LocalDate.of(2026, 10, 25))
            vm.onFoodRelationChange(FoodRelation.AFTER)

            vm.save()

            assertTrue(vm.uiState.value.isSaved)
            val medication = saved.single()
            assertEquals("Metformin", medication.name)
            assertEquals(
                Instant.parse("2026-10-20T00:00:00Z"),
                medication.startDate,
            )
            // Half-open convention: end date stored as the start of the next day.
            assertEquals(
                Instant.parse("2026-10-26T00:00:00Z"),
                medication.endDate,
            )
            coVerify {
                scheduleRepository.add(
                    match { it.medicationId == 11L && it.foodRelation == FoodRelation.AFTER },
                )
            }
        }

    @Test
    fun `save with out of range interval reports validation`() =
        runTest {
            val vm = viewModel()
            vm.onNameChange("Metformin")
            vm.onDoseAmountChange("1 tablet")
            vm.onScheduleTypeChange(ScheduleType.EVERY_N_HOURS)
            vm.onIntervalHoursChange("25")

            vm.save()

            assertTrue(DraftError.INTERVAL_OUT_OF_RANGE in vm.uiState.value.errors)
        }

    @Test
    fun `field edit clears previous validation errors`() =
        runTest {
            val vm = viewModel()
            vm.save()
            assertTrue(
                vm.uiState.value.errors
                    .isNotEmpty(),
            )

            vm.onNameChange("Metformin")

            assertEquals(emptySet<DraftError>(), vm.uiState.value.errors)
        }

    @Test
    fun `interval input keeps digits only`() {
        val vm = viewModel()

        vm.onIntervalHoursChange("6x_ hours")

        assertEquals("6", vm.uiState.value.intervalHours)
    }

    @Test
    fun `added times are sorted and deduplicated`() {
        val vm = viewModel()

        vm.onTimeAdded(LocalTime.of(20, 0))
        vm.onTimeAdded(LocalTime.of(8, 0))
        vm.onTimeAdded(LocalTime.of(20, 0))

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), vm.uiState.value.times)
    }

    @Test
    fun `weekday toggle adds and removes days`() {
        val vm = viewModel()

        vm.onWeekdayToggled(DayOfWeek.SUNDAY)
        vm.onWeekdayToggled(DayOfWeek.WEDNESDAY)
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), vm.uiState.value.weekdays)

        vm.onWeekdayToggled(DayOfWeek.SUNDAY)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), vm.uiState.value.weekdays)
    }

    // --- Edit flow ---

    @Test
    fun `editing loads existing medication and schedule`() =
        runTest {
            coEvery { medicationRepository.getById(7L) } returns existingMedication()
            coEvery { scheduleRepository.getByMedication(7L) } returns listOf(existingSchedule())

            val state = viewModel(id = 7L).uiState.value

            assertFalse(state.isLoading)
            assertTrue(state.isEditing)
            assertEquals("Metformin", state.name)
            assertEquals(MedicationForm.TABLET, state.form)
            assertEquals("500 mg", state.strength)
            assertEquals(MedicationSource.DOCTOR, state.source)
            assertEquals(LocalDate.of(2026, 10, 1), state.startDate)
            assertEquals(LocalDate.of(2026, 11, 1), state.endDate)
            assertEquals(ScheduleType.WEEKDAYS, state.scheduleType)
            assertEquals(listOf(LocalTime.of(9, 0)), state.times)
            assertEquals("1 tablet", state.doseAmount)
            assertEquals(FoodRelation.AFTER, state.foodRelation)
        }

    @Test
    fun `missing medication reports load failure`() =
        runTest {
            coEvery { medicationRepository.getById(9L) } returns null

            val state = viewModel(id = 9L).uiState.value

            assertTrue(state.loadFailed)
            assertFalse(state.isLoading)
        }

    @Test
    fun `editing updates instead of adding and keeps schedule id`() =
        runTest {
            coEvery { medicationRepository.getById(7L) } returns existingMedication()
            coEvery { scheduleRepository.getByMedication(7L) } returns listOf(existingSchedule())
            val vm = viewModel(id = 7L)
            vm.onStrengthChange("850 mg")

            vm.save()

            assertTrue(vm.uiState.value.isSaved)
            coVerify(exactly = 0) { medicationRepository.add(any()) }
            coVerify { medicationRepository.update(match { it.id == 7L && it.strength == "850 mg" }) }
            coVerify { scheduleRepository.update(match { it.id == 73L && it.medicationId == 7L }) }
            coVerify(exactly = 0) { scheduleRepository.add(any()) }
        }

    @Test
    fun `save failure surfaces saveFailed and clears saving`() =
        runTest {
            coEvery { medicationRepository.add(any()) } throws RuntimeException("disk full")
            val vm = viewModel()
            vm.onNameChange("Metformin")
            vm.onDoseAmountChange("1 tablet")
            vm.onTimeAdded(LocalTime.of(8, 0))

            vm.save()

            assertTrue(vm.uiState.value.saveFailed)
            assertFalse(vm.uiState.value.isSaving)
            assertFalse(vm.uiState.value.isSaved)
        }

    @Test
    fun `null end date is stored as null`() =
        runTest {
            val saved = mutableListOf<Medication>()
            coEvery { medicationRepository.add(capture(saved)) } returns 1L
            val vm = viewModel()
            vm.onNameChange("Metformin")
            vm.onDoseAmountChange("1 tablet")
            vm.onTimeAdded(LocalTime.of(8, 0))

            vm.save()

            assertNull(saved.single().endDate)
            assertEquals(
                Instant.parse("2026-10-17T00:00:00Z"),
                saved.single().startDate,
            )
        }

    private fun existingMedication() =
        Medication(
            id = 7L,
            name = "Metformin",
            form = MedicationForm.TABLET,
            strength = "500 mg",
            instructions = "With food",
            source = MedicationSource.DOCTOR,
            startDate = Instant.parse("2026-10-01T00:00:00Z"),
            endDate = Instant.parse("2026-11-02T00:00:00Z"),
            status = MedicationStatus.ACTIVE,
            stopReason = null,
            createdAt = Instant.parse("2026-09-30T10:00:00Z"),
        )

    private fun existingSchedule() =
        Schedule(
            id = 73L,
            medicationId = 7L,
            type = ScheduleType.WEEKDAYS,
            times = listOf(LocalTime.of(9, 0)),
            intervalHours = null,
            weekdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY),
            doseAmount = "1 tablet",
            foodRelation = FoodRelation.AFTER,
        )
}
