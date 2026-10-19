package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.feature.medications.labelRes
import com.medcompanion.app.feature.symptoms.severityLabelRes
import java.time.Instant

/**
 * The wording of one line of an exported document. Both documents (the History
 * report and "my data") build their lines here, so a doctor sees the same
 * wording and there is a single place to review it.
 */
internal class DocumentLines(
    private val context: Context,
) {
    fun dateTime(instant: Instant): String = "${date(instant)} ${time(instant)}"

    fun date(instant: Instant): String =
        android.text.format.DateFormat
            .getDateFormat(context)
            .format(java.util.Date.from(instant))

    /** Joins the parts of one line; a dash is not a localized word. */
    fun join(vararg parts: String?): String = parts.filterNotNull().joinToString(SEPARATOR)

    fun medicationWithAmount(
        name: String,
        doseAmount: String?,
    ): String {
        val label = name.ifBlank { context.getString(R.string.history_pdf_unknown_medication) }
        val amount = doseAmount?.takeIf { it.isNotBlank() } ?: return label
        return "$label ($amount)"
    }

    fun doseLine(
        scheduledAt: Instant,
        medicationName: String,
        doseAmount: String?,
        statusWord: String,
    ): String =
        join(
            dateTime(scheduledAt),
            medicationWithAmount(medicationName, doseAmount),
            statusWord,
        )

    fun symptomLine(
        loggedAt: Instant,
        name: String,
        severity: Int,
        note: String?,
    ): String {
        val head = join(dateTime(loggedAt), name, context.getString(severityLabelRes(severity)))
        return note?.takeIf { it.isNotBlank() }?.let { text ->
            "$head\n${context.getString(R.string.history_pdf_note, text)}"
        } ?: head
    }

    fun medicationLine(
        name: String,
        form: String,
        strength: String,
        status: MedicationStatus,
    ): String =
        join(
            name,
            form.takeIf { it.isNotBlank() },
            strength.takeIf { it.isNotBlank() },
            context.getString(status.labelRes()),
        )

    fun doseStatusWord(status: DoseStatus): String = context.getString(status.labelRes())

    private fun time(instant: Instant): String =
        android.text.format.DateFormat
            .getTimeFormat(context)
            .format(java.util.Date.from(instant))

    private companion object {
        const val SEPARATOR = " \u2014 "
    }
}
