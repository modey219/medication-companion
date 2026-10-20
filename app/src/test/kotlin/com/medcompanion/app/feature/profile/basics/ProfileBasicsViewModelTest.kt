package com.medcompanion.app.feature.profile.basics

import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.Sex
import com.medcompanion.app.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "Basic information" (T12): the birth year, sex, pregnancy flag and weight are
 * only written when the patient saves, so a background read can never overwrite
 * what they are typing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileBasicsViewModelTest {
    private val clock = FakeAppClock()
    private val profileRepository: ProfileRepository = mockk(relaxed = true)
    private val stored = MutableStateFlow<PatientBasics?>(null)
    private val saved = slot<PatientBasics>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { profileRepository.observeBasics() } returns stored
        coEvery { profileRepository.saveBasics(capture(saved)) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProfileBasicsViewModel(profileRepository = profileRepository, clock = clock)

    @Test
    fun `the stored facts fill the fields and give the age`() {
        stored.value = PatientBasics(birthYear = 1958, sex = Sex.FEMALE, weightKg = 64)

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals("1958", state.birthYear)
        assertEquals(Sex.FEMALE, state.sex)
        assertEquals("64", state.weightKg)
        assertEquals(clock.today().year - 1958, state.ageYears)
    }

    @Test
    fun `a background read never overwrites what the patient is typing`() {
        val viewModel = viewModel()

        viewModel.onBirthYearChange("1990")
        stored.value = PatientBasics(birthYear = 1900, sex = Sex.MALE)

        assertEquals("1990", viewModel.uiState.value.birthYear)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `saving writes the entered facts and clears the dirty flag`() {
        val viewModel = viewModel()

        viewModel.onBirthYearChange("1958")
        viewModel.onSexSelected(Sex.FEMALE)
        viewModel.onWeightChange("64")
        viewModel.onSave()

        assertEquals(1958, saved.captured.birthYear)
        assertEquals(Sex.FEMALE, saved.captured.sex)
        assertEquals(64, saved.captured.weightKg)
        assertFalse(viewModel.uiState.value.isDirty)
        assertEquals(ProfileBasicsMessage.SAVED, viewModel.uiState.value.message)
    }

    @Test
    fun `an impossible year cannot be saved`() {
        val viewModel = viewModel()

        viewModel.onBirthYearChange("1800")

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onSave()
        coVerify(exactly = 0) { profileRepository.saveBasics(any()) }
    }

    @Test
    fun `a future year cannot be saved`() {
        val viewModel = viewModel()

        viewModel.onBirthYearChange((clock.today().year + 1).toString())

        assertFalse(viewModel.uiState.value.canSave)
        assertNull(viewModel.uiState.value.ageYears)
    }

    @Test
    fun `an out-of-range weight cannot be saved`() {
        val viewModel = viewModel()

        viewModel.onWeightChange("5")

        assertFalse(viewModel.uiState.value.isWeightValid)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `leaving every field empty removes the stored facts`() {
        stored.value = PatientBasics(birthYear = 1958, weightKg = 64)
        val viewModel = viewModel()

        viewModel.onBirthYearChange("")
        viewModel.onWeightChange("")
        viewModel.onSave()

        assertTrue(saved.captured.isEmpty)
    }
}
