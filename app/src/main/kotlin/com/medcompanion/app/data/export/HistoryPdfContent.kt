package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.R
import com.medcompanion.app.domain.history.DoseRecord
import com.medcompanion.app.domain.history.HistoryReport
import com.medcompanion.app.domain.history.MedicationRecord
import com.medcompanion.app.domain.history.SymptomRecord
import com.medcompanion.app.feature.medications.labelRes
import com.medcompanion.app.feature.symptoms.severityLabelRes
import java.time.Instant
import java.util.Date

/** One paragraph of the exported document. */
sealed interface PdfBlock {
    val text: String

    data class Title(
        override val text: String,
    ) : PdfBlock

    data class Heading(
        override val text: String,
    ) : PdfBlock

    data class Body(
        override val text: String,
    ) : PdfBlock

    data class Footer(
        override val text: String,
    ) : PdfBlock
}

/**
 * Builds the text of the exported document, in order, with no dependency on
 * the PDF library, so the wording can be unit tested on its own.
 *
 * It only formats what the patient recorded: no interpretation, no advice
 * (TODO(clinical-review): the wording of the summary, the disclaimer and the
 * footer, listed in `docs/CLINICAL_REVIEW.md`).
 */
internal class HistoryPdfContent(
    private val context: Context,
) {
    fun blocks(
        report: HistoryReport,
        generatedAt: Instant,
    ): List<PdfBlock> =
        buildList {
            add(PdfBlock.Title(context.getString(R.string.history_pdf_title)))
            add(PdfBlock.Body(context.getString(R.string.history_pdf_generated, formatDateTime(generatedAt))))
            add(
                PdfBlock.Body(
                    context.getString(
                        R.string.history_pdf_period,
                        formatDate(report.periodStart),
                        formatDate(report.periodEnd),
                    ),
                ),
            )

            add(PdfBlock.Heading(context.getString(R.string.history_summary_title)))
            add(PdfBlock.Body(context.getString(R.string.history_summary_taken, report.summary.taken)))
            add(PdfBlock.Body(context.getString(R.string.history_summary_missed, report.summary.missed)))
            add(PdfBlock.Body(context.getString(R.string.history_summary_skipped, report.summary.skipped)))
            add(PdfBlock.Body(context.getString(R.string.history_summary_pending, report.summary.pending)))
            add(PdfBlock.Body(summaryPercentLine(report)))

            addSection(
                heading = context.getString(R.string.history_pdf_doses_title),
                lines = report.doses.map { doseLine(it) },
            )
            addSection(
                heading = context.getString(R.string.history_pdf_symptoms_title),
                lines = report.symptoms.map { symptomLine(it) },
            )
            addSection(
                heading = context.getString(R.string.history_pdf_medications_title),
                lines = report.medications.map { medicationLine(it) },
            )

            add(PdfBlock.Footer(context.getString(R.string.history_pdf_footer)))
        }

    private fun summaryPercentLine(report: HistoryReport): String =
        report.summary.takenPercent?.let { percent ->
            context.getString(R.string.history_summary_percent, percent)
        } ?: context.getString(R.string.history_summary_none)

    private fun MutableList<PdfBlock>.addSection(
        heading: String,
        lines: List<String>,
    ) {
        add(PdfBlock.Heading(heading))
        if (lines.isEmpty()) {
            add(PdfBlock.Body(context.getString(R.string.history_pdf_none)))
        } else {
            lines.forEach { line -> add(PdfBlock.Body(line)) }
        }
    }

    private fun doseLine(record: DoseRecord): String =
        join(
            formatDateTime(record.scheduledAt),
            medicationWithAmount(record),
            context.getString(record.status.labelRes()),
        )

    private fun medicationWithAmount(record: DoseRecord): String {
        val name = record.medicationName.ifBlank { context.getString(R.string.history_pdf_unknown_medication) }
        return if (record.doseAmount.isBlank()) name else "$name (${record.doseAmount})"
    }

    private fun symptomLine(record: SymptomRecord): String {
        val severity = context.getString(severityLabelRes(record.severity))
        val head = join(formatDateTime(record.loggedAt), record.name, severity)
        return record.note?.takeIf { it.isNotBlank() }?.let { note ->
            "$head\n${context.getString(R.string.history_pdf_note, note)}"
        } ?: head
    }

    private fun medicationLine(record: MedicationRecord): String =
        join(
            record.name,
            record.strength.takeIf { it.isNotBlank() },
            context.getString(record.status.labelRes()),
            formatDate(record.startDate),
        )

    private fun join(vararg parts: String?): String = parts.filterNotNull().joinToString(SEPARATOR)

    private fun formatDateTime(instant: Instant): String {
        val date = Date.from(instant)
        return "${dateFormat().format(date)} ${timeFormat().format(date)}"
    }

    private fun formatDate(instant: Instant): String = dateFormat().format(Date.from(instant))

    private fun dateFormat() =
        android.text.format.DateFormat
            .getDateFormat(context)

    private fun timeFormat() =
        android.text.format.DateFormat
            .getTimeFormat(context)

    private companion object {
        /** Joins the parts of one line; a dash is not a localized word. */
        const val SEPARATOR = " \u2014 "
    }
}
