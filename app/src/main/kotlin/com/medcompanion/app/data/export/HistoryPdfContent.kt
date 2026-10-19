package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.R
import com.medcompanion.app.domain.history.DoseRecord
import com.medcompanion.app.domain.history.HistoryReport
import com.medcompanion.app.domain.history.MedicationRecord
import com.medcompanion.app.domain.history.SymptomRecord
import com.medcompanion.app.feature.medications.labelRes
import java.time.Instant

/**
 * The History report as a list of [PdfBlock]s (T7). Dates, amounts and status
 * words come from [DocumentLines]; the wording of the summary and the footer
 * is TODO(clinical-review) (docs/CLINICAL_REVIEW.md).
 */
internal class HistoryPdfContent(
    private val context: Context,
) {
    private val lines = DocumentLines(context)

    fun blocks(
        report: HistoryReport,
        generatedAt: Instant,
    ): List<PdfBlock> =
        buildList {
            add(PdfBlock.Title(context.getString(R.string.history_pdf_title)))
            add(PdfBlock.Body(context.getString(R.string.history_pdf_generated, lines.dateTime(generatedAt))))
            add(
                PdfBlock.Body(
                    context.getString(
                        R.string.history_pdf_period,
                        lines.date(report.periodStart),
                        lines.date(report.periodEnd),
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
                entries = report.doses.map { doseLine(it) },
            )
            addSection(
                heading = context.getString(R.string.history_pdf_symptoms_title),
                entries = report.symptoms.map { symptomLine(it) },
            )
            addSection(
                heading = context.getString(R.string.history_pdf_medications_title),
                entries = report.medications.map { medicationLine(it) },
            )

            add(PdfBlock.Footer(context.getString(R.string.history_pdf_footer)))
        }

    private fun summaryPercentLine(report: HistoryReport): String =
        report.summary.takenPercent?.let { percent ->
            context.getString(R.string.history_summary_percent, percent)
        } ?: context.getString(R.string.history_summary_none)

    private fun MutableList<PdfBlock>.addSection(
        heading: String,
        entries: List<String>,
    ) {
        add(PdfBlock.Heading(heading))
        if (entries.isEmpty()) {
            add(PdfBlock.Body(context.getString(R.string.history_pdf_none)))
        } else {
            entries.forEach { line -> add(PdfBlock.Body(line)) }
        }
    }

    private fun doseLine(record: DoseRecord): String =
        lines.doseLine(
            scheduledAt = record.scheduledAt,
            medicationName = record.medicationName,
            doseAmount = record.doseAmount,
            statusWord = context.getString(record.status.labelRes()),
        )

    private fun symptomLine(record: SymptomRecord): String =
        lines.symptomLine(
            loggedAt = record.loggedAt,
            name = record.name,
            severity = record.severity,
            note = record.note,
        )

    private fun medicationLine(record: MedicationRecord): String =
        lines.join(
            record.name,
            record.strength.takeIf { it.isNotBlank() },
            context.getString(record.status.labelRes()),
            lines.date(record.startDate),
        )
}
