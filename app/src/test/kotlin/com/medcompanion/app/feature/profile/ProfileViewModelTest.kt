package com.medcompanion.app.feature.profile

import com.medcompanion.app.core.locale.LanguageApplier
import com.medcompanion.app.data.export.DataJsonExporter
import com.medcompanion.app.data.export.DataPdfExporter
import com.medcompanion.app.domain.maintenance.DataMaintenance
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.repository.ProfileRepository
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.domain.settings.FakeSettingsRepository
import com.medcompanion.app.domain.settings.ReminderPreferences
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
 * Profile tab (T8a): the settings (language, emergency number) and the data
 * actions. The export itself is tested through its own two documents; here the
 * ViewModel is checked, including that the emergency number is only written
 * when the patient saves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val settings = FakeSettingsRepository(AppSettings(language = AppLanguage.ARABIC))
    private val applied = mutableListOf<AppLanguage>()
    private val profileRepository: ProfileRepository = mockk(relaxed = true)
    private val stored = MutableStateFlow<List<ProfileEntry>>(emptyList())
    private val jsonExporter: DataJsonExporter = mockk(relaxed = true)
    private val pdfExporter: DataPdfExporter = mockk(relaxed = true)
    private val maintenance: DataMaintenance = mockk(relaxed = true)
    private val added = slot<ProfileEntry>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { profileRepository.observeAll() } returns stored
        coEvery { profileRepository.add(capture(added)) } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        ProfileViewModel(
            settingsRepository = settings,
            languageApplier = LanguageApplier { applied += it },
            profileRepository = profileRepository,
            jsonExporter = jsonExporter,
            pdfExporter = pdfExporter,
            dataMaintenance = maintenance,
        )

    @Test
    fun `the screen shows the stored settings and the stored entries`() {
        stored.value =
            listOf(
                ProfileEntry(id = 1, type = ProfileEntryType.ALLERGY, label = "البنسلين"),
                ProfileEntry(id = 2, type = ProfileEntryType.CONDITION, label = "السكري"),
            )

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(AppLanguage.ARABIC, state.language)
        assertEquals(1, state.allergies.size)
        assertEquals(1, state.conditions.size)
    }

    @Test
    fun `choosing a language stores it and applies it to the app`() {
        val viewModel = viewModel()

        viewModel.onLanguageSelected(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, settings.current.language)
        assertEquals(listOf(AppLanguage.ENGLISH), applied)
    }

    @Test
    fun `an empty emergency number is kept as the patient left it`() {
        val viewModel = viewModel()

        viewModel.onEmergencyNumberChange("")
        viewModel.onEmergencyNumberSave()

        assertEquals("", settings.current.emergencyNumber)
    }

    @Test
    fun `the emergency number is written only when the patient saves it`() {
        val viewModel = viewModel()

        viewModel.onEmergencyNumberChange("997")
        assertEquals("", settings.current.emergencyNumber)

        viewModel.onEmergencyNumberSave()

        assertEquals("997", settings.current.emergencyNumber)
        assertEquals(ProfileMessage.EMERGENCY_NUMBER_SAVED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isEmergencyNumberDirty)
    }

    @Test
    fun `the export hands the share sheet a uri and a type`() {
        coEvery { jsonExporter.export() } returns "content://share/data.json"
        val viewModel = viewModel()

        viewModel.onExportJson()

        assertEquals(ProfileShare("content://share/data.json", "application/json"), viewModel.uiState.value.share)
        assertFalse(viewModel.uiState.value.isExporting)
    }

    @Test
    fun `the PDF export uses the pdf type`() {
        coEvery { pdfExporter.export() } returns "content://share/data.pdf"
        val viewModel = viewModel()

        viewModel.onExportPdf()

        assertEquals(
            "application/pdf",
            viewModel.uiState.value.share
                ?.mimeType,
        )
    }

    @Test
    fun `a failed export shows a message instead of a share sheet`() {
        coEvery { jsonExporter.export() } throws IllegalStateException("disk full")
        val viewModel = viewModel()

        viewModel.onExportJson()

        assertNull(viewModel.uiState.value.share)
        assertEquals(ProfileMessage.EXPORT_FAILED, viewModel.uiState.value.message)
    }

    @Test
    fun `deleting everything asks first and then removes the data`() {
        val viewModel = viewModel()

        viewModel.onDeleteAllRequested()
        assertTrue(viewModel.uiState.value.isDeleteConfirmVisible)

        viewModel.onDeleteAllConfirmed()

        coVerify(exactly = 1) { maintenance.deleteAllData() }
        assertFalse(viewModel.uiState.value.isDeleteConfirmVisible)
        assertEquals(ProfileMessage.DATA_DELETED, viewModel.uiState.value.message)
    }

    @Test
    fun `a new entry is stored with the trimmed label and no empty note`() {
        val viewModel = viewModel()

        viewModel.onAddEntry(ProfileEntryType.ALLERGY)
        viewModel.onEntryLabelChanged("  البنسلين  ")
        viewModel.onEntryNoteChanged("   ")
        viewModel.onEntrySave()

        assertEquals("البنسلين", added.captured.label)
        assertNull(added.captured.note)
        assertEquals(ProfileEntryType.ALLERGY, added.captured.type)
        assertNull(viewModel.uiState.value.entryDialog)
    }

    @Test
    fun `an entry without a label is not saved`() {
        val viewModel = viewModel()

        viewModel.onAddEntry(ProfileEntryType.CONDITION)
        viewModel.onEntryLabelChanged("   ")
        viewModel.onEntrySave()

        assertFalse(
            viewModel.uiState.value.entryDialog
                ?.canSave ?: true,
        )
        coVerify(exactly = 0) { profileRepository.add(any()) }
    }

    @Test
    fun `editing an entry keeps its id`() {
        val viewModel = viewModel()

        viewModel.onEditEntry(
            ProfileEntry(id = 7, type = ProfileEntryType.CONDITION, label = "السكري", note = "النوع الثاني"),
        )
        viewModel.onEntrySave()

        coVerify { profileRepository.update(match { it.id == 7L && it.label == "السكري" }) }
    }

    @Test
    fun `deleting an entry calls the repository`() {
        val entry = ProfileEntry(id = 3, type = ProfileEntryType.CONDITION, label = "الربو")
        val viewModel = viewModel()

        viewModel.onEntryDelete(entry)

        coVerify(exactly = 1) { profileRepository.delete(entry) }
    }

    @Test
    fun `the low-stock stepper saves immediately and stays in range`() {
        val viewModel = viewModel()

        viewModel.onLowStockThresholdChange(+1)
        assertEquals(4, settings.current.lowStockThreshold)

        repeat(20) { viewModel.onLowStockThresholdChange(+1) }

        assertEquals(ReminderPreferences.LOW_STOCK_THRESHOLD_RANGE.last, settings.current.lowStockThreshold)
        assertEquals(ProfileMessage.STOCK_THRESHOLD_SAVED, viewModel.uiState.value.message)
        assertEquals(settings.current.lowStockThreshold, viewModel.uiState.value.lowStockThreshold)
    }

    @Test
    fun `the pharmacy number is written only when the patient saves it`() {
        val viewModel = viewModel()

        viewModel.onPharmacyNumberChange("  01234567  ")
        assertEquals("", settings.current.pharmacyNumber)
        assertTrue(viewModel.uiState.value.isPharmacyNumberDirty)

        viewModel.onPharmacyNumberSave()

        assertEquals("01234567", settings.current.pharmacyNumber)
        assertEquals(ProfileMessage.PHARMACY_NUMBER_SAVED, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isPharmacyNumberDirty)
    }
}
