package com.medcompanion.app.feature.symptoms

import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.repository.SymptomRepository
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SymptomsViewModelTest {
    private val clock = FakeAppClock() // fixed at 2026-10-17T12:00:00Z, UTC
    private val repository: SymptomRepository = mockk(relaxed = true)
    private val stored = MutableStateFlow<List<SymptomLog>>(emptyList())
    private val saved = slot<SymptomLog>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.observeAll() } returns stored
        coEvery { repository.add(capture(saved)) } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SymptomsViewModel(repository, clock)

    @Test
    fun `saving stores the trimmed name, the severity, the note and the clock time`() {
        val viewModel = viewModel()
        viewModel.onNameChange("  صداع  ")
        viewModel.onSeverityChange(4)
        viewModel.onNoteChange("  بعد الغداء  ")

        viewModel.save()

        assertEquals("صداع", saved.captured.name)
        assertEquals(4, saved.captured.severity)
        assertEquals("بعد الغداء", saved.captured.note)
        assertEquals(clock.now(), saved.captured.loggedAt)
    }

    @Test
    fun `the form starts empty with no severity chosen`() {
        val state = viewModel().uiState.value

        assertEquals("", state.form.name)
        assertNull(state.form.severity)
        assertEquals("", state.form.note)
        assertFalse(state.form.canSave)
    }

    @Test
    fun `a blank name cannot be saved`() {
        val viewModel = viewModel()
        viewModel.onNameChange("   ")
        viewModel.onSeverityChange(3)

        viewModel.save()

        assertFalse(viewModel.uiState.value.form.canSave)
        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `a symptom without a chosen severity cannot be saved`() {
        val viewModel = viewModel()
        viewModel.onNameChange("صداع")

        viewModel.save()

        assertFalse(viewModel.uiState.value.form.canSave)
        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `a blank note is stored as null`() {
        val viewModel = viewModel()
        viewModel.onNameChange("صداع")
        viewModel.onSeverityChange(1)
        viewModel.onNoteChange("   ")

        viewModel.save()

        assertNull(saved.captured.note)
    }

    @Test
    fun `a successful save clears the form`() {
        val viewModel = viewModel()
        viewModel.onNameChange("صداع")
        viewModel.onSeverityChange(2)
        viewModel.onNoteChange("ملاحظة")

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals(SymptomFormState(), state.form)
        assertFalse(state.isSaving)
        assertNull(state.error)
    }

    @Test
    fun `a save failure keeps the form and shows an error`() {
        coEvery { repository.add(any()) } throws IllegalStateException("disk full")
        val viewModel = viewModel()
        viewModel.onNameChange("صداع")
        viewModel.onSeverityChange(2)

        viewModel.save()

        val state = viewModel.uiState.value
        assertEquals("صداع", state.form.name)
        assertEquals(2, state.form.severity)
        assertEquals(SymptomsError.SAVE_FAILED, state.error)
        assertFalse(state.isSaving)
    }

    @Test
    fun `dismissing the error clears it`() {
        coEvery { repository.add(any()) } throws IllegalStateException("disk full")
        val viewModel = viewModel()
        viewModel.onNameChange("صداع")
        viewModel.onSeverityChange(2)
        viewModel.save()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `stored symptoms are listed in the order the repository sends them`() {
        val newest = log(id = 2, name = "غثيان", at = Instant.parse("2026-10-17T11:00:00Z"))
        val older = log(id = 1, name = "صداع", at = Instant.parse("2026-10-17T08:00:00Z"))
        stored.value = listOf(newest, older)

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf(2L, 1L), state.entries.map { it.id })
        assertEquals(3, state.entries.first().severity)
    }

    @Test
    fun `deleting removes the symptom that was shown`() {
        stored.value = listOf(log(id = 5, name = "غثيان", at = clock.now()))
        val viewModel = viewModel()

        viewModel.delete(
            viewModel.uiState.value.entries
                .single(),
        )

        coVerify { repository.delete(withArg { assertEquals(5L, it.id) }) }
    }

    @Test
    fun `a delete failure shows an error`() {
        stored.value = listOf(log(id = 5, name = "غثيان", at = clock.now()))
        coEvery { repository.delete(any()) } throws IllegalStateException("locked")
        val viewModel = viewModel()

        viewModel.delete(
            viewModel.uiState.value.entries
                .single(),
        )

        assertEquals(SymptomsError.DELETE_FAILED, viewModel.uiState.value.error)
    }

    @Test
    fun `a severity outside the 1 to 5 range is ignored`() {
        val viewModel = viewModel()

        viewModel.onSeverityChange(0)
        assertNull(viewModel.uiState.value.form.severity)

        viewModel.onSeverityChange(6)
        assertNull(viewModel.uiState.value.form.severity)

        viewModel.onSeverityChange(5)
        assertEquals(5, viewModel.uiState.value.form.severity)
    }

    @Test
    fun `an urgent symptom name shows the emergency card`() {
        val viewModel = viewModel()

        viewModel.onNameChange("ألم في الصدر")

        assertTrue(viewModel.uiState.value.showEmergencyWarning)
    }

    @Test
    fun `an ordinary symptom name does not show the emergency card`() {
        val viewModel = viewModel()

        viewModel.onNameChange("صداع")

        assertFalse(viewModel.uiState.value.showEmergencyWarning)
    }

    @Test
    fun `clearing the form after a save hides the emergency card`() {
        val viewModel = viewModel()
        viewModel.onNameChange("ألم في الصدر")
        viewModel.onSeverityChange(5)

        viewModel.save()

        assertFalse(viewModel.uiState.value.showEmergencyWarning)
    }

    private fun log(
        id: Long,
        name: String,
        at: Instant,
        severity: Int = 3,
    ) = SymptomLog(id = id, loggedAt = at, name = name, severity = severity, note = null)
}
