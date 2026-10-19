package com.medcompanion.app.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.R
import com.medcompanion.app.domain.history.AdherenceSummary
import com.medcompanion.app.domain.history.DoseRecord
import com.medcompanion.app.domain.history.HistoryReport
import com.medcompanion.app.domain.history.MedicationRecord
import com.medcompanion.app.domain.history.SymptomRecord
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.MedicationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Date

/**
 * The text of the exported document (T7). The wording is the part a clinician
 * reviews and a doctor reads, so it is tested on its own, without the PDF
 * library: blocks in order, one line per record, and a neutral line instead of
 * a percentage when nothing was acted on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class HistoryPdfContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val content = HistoryPdfContent(context)

    private val generatedAt = Instant.parse("2026-10-17T12:00:00Z")

    private val windowStart = Instant.parse("2025-12-01T00:00:00Z")

    @Test
    fun `the document starts with the title and the generation line`() {
        val blocks = blocks(report(doses = 1))

        assertTrue(blocks.first() is PdfBlock.Title)
        assertEquals(context.getString(R.string.history_pdf_title), blocks.first().text)
        assertEquals(
            context.getString(R.string.history_pdf_generated, dateTimeText(generatedAt)),
            blocks[1].text,
        )
    }

    @Test
    fun `the period line names both ends of the window`() {
        val bodies = blocks(report(doses = 1)).filterIsInstance<PdfBlock.Body>()

        val expected =
            context.getString(
                R.string.history_pdf_period,
                dateText(windowStart),
                dateText(generatedAt),
            )

        assertTrue(bodies.any { it.text == expected })
    }

    @Test
    fun `the summary writes every count and the taken share`() {
        val texts =
            texts(report(doses = 1, summary = AdherenceSummary(taken = 3, missed = 1, skipped = 1, pending = 1)))

        assertTrue(texts.contains(context.getString(R.string.history_summary_taken, 3)))
        assertTrue(texts.contains(context.getString(R.string.history_summary_missed, 1)))
        assertTrue(texts.contains(context.getString(R.string.history_summary_skipped, 1)))
        assertTrue(texts.contains(context.getString(R.string.history_summary_pending, 1)))
        assertTrue(texts.contains(context.getString(R.string.history_summary_percent, 60)))
    }

    @Test
    fun `a window with nothing acted on shows the neutral line instead of a share`() {
        val texts = texts(report(doses = 0, summary = AdherenceSummary(pending = 2)))

        assertTrue(texts.contains(context.getString(R.string.history_summary_none)))
        assertFalse(texts.any { it.contains("%") })
    }

    @Test
    fun `an empty report says none in all three sections`() {
        val texts = texts(report(doses = 0, symptoms = 0, medications = 0))

        assertEquals(3, texts.count { it == context.getString(R.string.history_pdf_none) })
    }

    @Test
    fun `every section heading appears once`() {
        val texts = texts(report(doses = 1))

        assertEquals(1, texts.count { it == context.getString(R.string.history_summary_title) })
        assertEquals(1, texts.count { it == context.getString(R.string.history_pdf_doses_title) })
        assertEquals(1, texts.count { it == context.getString(R.string.history_pdf_symptoms_title) })
        assertEquals(1, texts.count { it == context.getString(R.string.history_pdf_medications_title) })
    }

    @Test
    fun `a dose line carries the medicine, the amount and the status word`() {
        val texts = texts(report(doses = 1))

        assertTrue(
            texts.any {
                it.contains("ميتفورمين (حبة واحدة)") && it.endsWith(context.getString(R.string.dose_taken))
            },
        )
    }

    @Test
    fun `a dose with no amount keeps the medicine name alone`() {
        val texts =
            texts(
                report(
                    doses = 1,
                    doseAmount = "",
                ),
            )

        assertTrue(texts.any { it.contains("ميتفورمين") && !it.contains("(") })
    }

    @Test
    fun `a dose of a medicine that is not known is written as unknown`() {
        val texts = texts(report(doses = 1, medicationName = ""))

        assertTrue(texts.any { it.contains(context.getString(R.string.history_pdf_unknown_medication)) })
    }

    @Test
    fun `a symptom line carries the note and the severity word`() {
        val texts = texts(report(doses = 0, symptoms = 1))

        assertTrue(
            texts.any {
                it.contains("بعد الغداء") && it.contains(context.getString(R.string.symptom_severity_3))
            },
        )
    }

    @Test
    fun `a started medicine carries its strength, its state and its start date`() {
        val texts = texts(report(doses = 0, symptoms = 0, medications = 1))

        assertTrue(
            texts.any {
                it.contains("1000 وحدة") && it.contains(context.getString(R.string.medication_status_active))
            },
        )
    }

    @Test
    fun `a long report writes one line per record`() {
        val texts = texts(report(doses = 40, symptoms = 0, medications = 0))

        assertEquals(40, texts.count { it.contains("ميتفورمين") })
        assertEquals(1, texts.count { it == context.getString(R.string.history_pdf_doses_title) })
    }

    @Test
    fun `the document ends with the safety footer`() {
        val blocks = blocks(report(doses = 1))

        assertTrue(blocks.last() is PdfBlock.Footer)
        assertEquals(context.getString(R.string.history_pdf_footer), blocks.last().text)
    }

    private fun blocks(report: HistoryReport): List<PdfBlock> = content.blocks(report, generatedAt)

    private fun texts(report: HistoryReport): List<String> = blocks(report).map { it.text }

    private fun dateText(instant: Instant): String =
        android.text.format.DateFormat
            .getDateFormat(context)
            .format(Date.from(instant))

    private fun dateTimeText(instant: Instant): String {
        val date = Date.from(instant)
        return "${dateText(instant)} ${android.text.format.DateFormat.getTimeFormat(context).format(date)}"
    }

    private fun report(
        doses: Int,
        symptoms: Int = 1,
        medications: Int = 1,
        summary: AdherenceSummary = AdherenceSummary(taken = 1),
        doseAmount: String = "حبة واحدة",
        medicationName: String = "ميتفورمين",
    ): HistoryReport =
        HistoryReport(
            periodStart = windowStart,
            periodEnd = generatedAt,
            summary = summary,
            doses =
                (1..doses).map { index ->
                    DoseRecord(
                        medicationName = medicationName,
                        doseAmount = doseAmount,
                        scheduledAt = generatedAt.minusSeconds(index * 3600L),
                        status = DoseStatus.TAKEN,
                    )
                },
            symptoms =
                (1..symptoms).map {
                    SymptomRecord(
                        name = "صداع",
                        severity = 3,
                        loggedAt = generatedAt.minusSeconds(600),
                        note = "بعد الغداء",
                    )
                },
            medications =
                (1..medications).map {
                    MedicationRecord(
                        name = "فيتامين د",
                        strength = "1000 وحدة",
                        startDate = Instant.parse("2026-10-16T18:00:00Z"),
                        status = MedicationStatus.ACTIVE,
                    )
                },
        )
}
