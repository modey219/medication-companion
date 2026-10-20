package com.medcompanion.app.feature.profile.history

import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
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
 * Medical history (T12): what the patient had, added and corrected by them.
 * The screen only records and shows it back; these tests pin down the dialog
 * rules and that a blank label is never stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicalHistoryViewModelTest {
    private val profileRepository: ProfileRepository = mockk(relaxed = true)
    private val stored = MutableStateFlow<List<MedicalHistoryEntry>>(emptyList())
    private val added = slot<MedicalHistoryEntry>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { profileRepository.observeHistory() } returns stored
        coEvery { profileRepository.addHistory(capture(added)) } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MedicalHistoryViewModel(profileRepository = profileRepository)

    @Test
    fun `the stored history is shown`() {
        stored.value =
            listOf(
                MedicalHistoryEntry(id = 1, type = MedicalHistoryType.SURGERY, label = "الزائدة", year = 2008),
            )

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(1, state.entries.size)
    }

    @Test
    fun `a new item is stored with the trimmed text and no empty note`() {
        val viewModel = viewModel()

        viewModel.onAdd()
        assertEquals(
            MedicalHistoryType.PAST_ILLNESS,
            viewModel.uiState.value.dialog
                ?.type,
        )

        viewModel.onLabelChanged("  التهاب رئوي  ")
        viewModel.onYearChanged("2019")
        viewModel.onNoteChanged("   ")
        viewModel.onSave()

        assertEquals("التهاب رئوي", added.captured.label)
        assertEquals(2019, added.captured.year)
        assertNull(added.captured.note)
        assertEquals(MedicalHistoryType.PAST_ILLNESS, added.captured.type)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `the type the patient chose is stored`() {
        val viewModel = viewModel()

        viewModel.onAdd()
        viewModel.onTypeSelected(MedicalHistoryType.FAMILY_HISTORY)
        viewModel.onLabelChanged("السكري في العائلة")
        viewModel.onSave()

        assertEquals(MedicalHistoryType.FAMILY_HISTORY, added.captured.type)
    }

    @Test
    fun `an item without a label is not saved`() {
        val viewModel = viewModel()

        viewModel.onAdd()
        viewModel.onLabelChanged("   ")
        viewModel.onSave()

        assertFalse(
            viewModel.uiState.value.dialog
                ?.canSave ?: true,
        )
        coVerify(exactly = 0) { profileRepository.addHistory(any()) }
    }

    @Test
    fun `an impossible year cannot be saved`() {
        val viewModel = viewModel()

        viewModel.onAdd()
        viewModel.onLabelChanged("التهاب رئوي")
        viewModel.onYearChanged("1800")

        assertFalse(
            viewModel.uiState.value.dialog
                ?.isYearValid ?: true,
        )
        assertFalse(
            viewModel.uiState.value.dialog
                ?.canSave ?: true,
        )
        coVerify(exactly = 0) { profileRepository.addHistory(any()) }
    }

    @Test
    fun `editing an item keeps its id and fills the fields`() {
        val viewModel = viewModel()
        val entry =
            MedicalHistoryEntry(
                id = 7,
                type = MedicalHistoryType.HOSPITALIZATION,
                label = "التهاب رئوي",
                year = 2019,
                note = "أسبوع",
            )

        viewModel.onEdit(entry)

        val dialog = viewModel.uiState.value.dialog
        assertTrue(dialog?.isEditing == true)
        assertEquals(7L, dialog?.entryId)
        assertEquals(MedicalHistoryType.HOSPITALIZATION, dialog?.type)
        assertEquals("2019", dialog?.year)
        assertEquals("أسبوع", dialog?.note)

        viewModel.onSave()

        coVerify {
            profileRepository.updateHistory(match { it.id == 7L && it.label == "التهاب رئوي" })
        }
    }

    @Test
    fun `deleting an item calls the repository`() {
        val entry = MedicalHistoryEntry(id = 3, type = MedicalHistoryType.SURGERY, label = "الزائدة")
        val viewModel = viewModel()

        viewModel.onDelete(entry)

        coVerify(exactly = 1) { profileRepository.deleteHistory(entry) }
    }

    @Test
    fun `a failed save is reported instead of crashing`() {
        coEvery { profileRepository.addHistory(any()) } throws IllegalStateException("disk full")
        val viewModel = viewModel()

        viewModel.onAdd()
        viewModel.onLabelChanged("التهاب رئوي")
        viewModel.onSave()

        assertEquals(MedicalHistoryMessage.SAVE_FAILED, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a failed delete is reported instead of crashing`() {
        coEvery { profileRepository.deleteHistory(any()) } throws IllegalStateException("disk full")
        val viewModel = viewModel()

        viewModel.onDelete(
            MedicalHistoryEntry(id = 3, type = MedicalHistoryType.SURGERY, label = "الزائدة"),
        )

        assertEquals(MedicalHistoryMessage.DELETE_FAILED, viewModel.uiState.value.message)
    }
}
