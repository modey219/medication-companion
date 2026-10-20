package com.medcompanion.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.R
import com.medcompanion.app.domain.export.FullData
import com.medcompanion.app.domain.export.MedicationWithSchedules
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.feature.medications.labelRes
import com.medcompanion.app.feature.profile.labelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime
import java.util.Date

/**
 * "Export my data" as a document (T8a). The wording is what a doctor or a
 * pharmacist reads, so it is tested on its own, without the PDF library:
 * heading order, one line per record, and the patient's own words untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DataPdfContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val content = DataPdfContent(context)

    private val generatedAt = Instant.parse("2026-10-17T12:00:00Z")

    @Test
    fun `the document starts with the title and the generation line`() {
        val blocks = blocks(fullData())

        assertTrue(blocks.first() is PdfBlock.Title)
        assertEquals(context.getString(R.string.data_pdf_title), blocks.first().text)
        assertEquals(
            context.getString(R.string.history_pdf_generated, dateTimeText(generatedAt)),
            blocks[1].text,
        )
    }

    @Test
    fun `the counts line adds up the medications and the symptoms`() {
        val texts = texts(fullData())

        assertTrue(texts.contains(context.getString(R.string.data_pdf_counts, 1, 1)))
    }

    @Test
    fun `the profile, medication, dose and symptom sections are all present`() {
        val texts = texts(fullData())

        assertTrue(texts.contains(context.getString(R.string.data_pdf_basics_title)))
        assertTrue(texts.contains(context.getString(R.string.profile_allergy_title)))
        assertTrue(texts.contains(context.getString(R.string.profile_condition_title)))
        assertTrue(texts.contains(context.getString(R.string.data_pdf_history_title)))
        assertTrue(texts.contains(context.getString(R.string.data_pdf_medications_title)))
        assertTrue(texts.contains(context.getString(R.string.history_pdf_doses_title)))
        assertTrue(texts.contains(context.getString(R.string.history_pdf_symptoms_title)))
    }

    @Test
    fun `the basic facts and the medical history are written line by line`() {
        val texts = texts(fullData())

        assertTrue(texts.any { it.contains(context.getString(R.string.data_pdf_birth_year, 1960)) })
        assertTrue(texts.any { it.contains(context.getString(R.string.data_pdf_weight, 70)) })
        assertTrue(
            texts.any {
                it.contains(context.getString(MedicalHistoryType.HOSPITALIZATION.labelRes())) &&
                    it.contains("التهاب رئوي")
            },
        )
    }

    @Test
    fun `an empty section says so instead of staying blank`() {
        val texts =
            texts(
                fullData(
                    patientBasics = null,
                    profileEntries = emptyList(),
                    medicalHistory = emptyList(),
                    medications = emptyList(),
                    doses = emptyList(),
                    symptoms = emptyList(),
                ),
            )

        assertEquals(6, texts.count { it == context.getString(R.string.history_pdf_none) })
        assertTrue(texts.contains(context.getString(R.string.data_pdf_basics_empty)))
    }

    @Test
    fun `the patient's own words are kept for the allergies and conditions`() {
        val texts = texts(fullData())

        assertTrue(texts.any { it.contains("البنسلين") })
        assertTrue(texts.any { it.contains("السكري") })
    }

    @Test
    fun `every recorded dose becomes one line with its medication and amount`() {
        val texts = texts(fullData())

        assertTrue(texts.any { it.contains("ميتفورمين") && it.contains("حبة واحدة") })
        assertTrue(texts.any { it.contains(context.getString(DoseStatus.TAKEN.labelRes())) })
    }

    @Test
    fun `every recorded symptom becomes one line with its severity`() {
        val texts = texts(fullData())

        assertTrue(texts.any { it.contains("صداع") && it.contains("بعد الغداء") })
    }

    @Test
    fun `an interval schedule names the interval instead of times`() {
        val texts =
            texts(
                fullData(
                    medications =
                        listOf(
                            MedicationWithSchedules(
                                medication = medication(),
                                schedules = listOf(schedule(type = ScheduleType.EVERY_N_HOURS, intervalHours = 8)),
                            ),
                        ),
                ),
            )

        assertTrue(texts.any { it.contains(context.getString(R.string.data_pdf_every_hours, 8)) })
    }

    @Test
    fun `the document ends with the safety footer`() {
        val blocks = blocks(fullData())

        assertTrue(blocks.last() is PdfBlock.Footer)
        assertEquals(context.getString(R.string.history_pdf_footer), blocks.last().text)
    }

    private fun blocks(data: FullData): List<PdfBlock> = content.blocks(data)

    private fun texts(data: FullData): List<String> = blocks(data).map { it.text }

    private fun dateText(instant: Instant): String =
        android.text.format.DateFormat
            .getDateFormat(context)
            .format(Date.from(instant))

    private fun dateTimeText(instant: Instant): String {
        val date = Date.from(instant)
        return "${dateText(instant)} ${android.text.format.DateFormat.getTimeFormat(context).format(date)}"
    }

    private fun fullData(
        patientBasics: PatientBasics? = PatientBasics(birthYear = 1960, weightKg = 70),
        profileEntries: List<ProfileEntry> = listOf(allergy(), condition()),
        medicalHistory: List<MedicalHistoryEntry> = listOf(history()),
        medications: List<MedicationWithSchedules> = listOf(medicationWithSchedule()),
        doses: List<DoseEvent> = listOf(dose()),
        symptoms: List<SymptomLog> = listOf(symptom()),
    ) = FullData(
        generatedAt = generatedAt,
        settings = AppSettings(),
        patientBasics = patientBasics,
        profileEntries = profileEntries,
        medicalHistory = medicalHistory,
        sectionStates = emptyList(),
        medications = medications,
        doses = doses,
        symptoms = symptoms,
    )

    private fun history() =
        MedicalHistoryEntry(
            id = 1,
            type = MedicalHistoryType.HOSPITALIZATION,
            label = "التهاب رئوي",
            year = 2019,
        )

    private fun allergy() = ProfileEntry(type = ProfileEntryType.ALLERGY, label = "البنسلين")

    private fun condition() = ProfileEntry(type = ProfileEntryType.CONDITION, label = "السكري")

    private fun medicationWithSchedule() =
        MedicationWithSchedules(medication = medication(), schedules = listOf(schedule()))

    private fun medication() =
        Medication(
            id = 5,
            name = "ميتفورمين",
            form = MedicationForm.TABLET,
            strength = "500 ملغ",
            instructions = "",
            source = MedicationSource.SELF,
            startDate = generatedAt,
            createdAt = generatedAt,
        )

    private fun schedule(
        type: ScheduleType = ScheduleType.DAILY_TIMES,
        intervalHours: Int? = null,
    ) = Schedule(
        id = 7,
        medicationId = 5,
        type = type,
        times = if (type == ScheduleType.DAILY_TIMES) listOf(LocalTime.of(8, 0)) else emptyList(),
        intervalHours = intervalHours,
        doseAmount = "حبة واحدة",
    )

    private fun dose() =
        DoseEvent(
            id = 1,
            medicationId = 5,
            scheduledAt = generatedAt,
            status = DoseStatus.TAKEN,
            actedAt = generatedAt,
        )

    private fun symptom() =
        SymptomLog(
            id = 1,
            loggedAt = generatedAt,
            name = "صداع",
            severity = 3,
            note = "بعد الغداء",
        )
}
