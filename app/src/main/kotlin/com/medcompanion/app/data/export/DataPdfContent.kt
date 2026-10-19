package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.R
import com.medcompanion.app.domain.export.FullData
import com.medcompanion.app.domain.export.MedicationWithSchedules
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.feature.medications.labelRes
import java.time.LocalTime

/**
 * "Export my data" as a document (T8a): the profile facts, the medications
 * with their schedules, every recorded dose and every symptom. It describes
 * what the patient entered and nothing else; it never grades or advises.
 */
internal class DataPdfContent(
    private val context: Context,
) {
    private val lines = DocumentLines(context)

    fun blocks(data: FullData): List<PdfBlock> =
        buildList {
            add(PdfBlock.Title(context.getString(R.string.data_pdf_title)))
            add(PdfBlock.Body(context.getString(R.string.history_pdf_generated, lines.dateTime(data.generatedAt))))
            add(PdfBlock.Body(context.getString(R.string.data_pdf_counts, data.medications.size, data.symptoms.size)))

            addSection(
                heading = context.getString(R.string.profile_allergy_title),
                entries = data.profileEntries.ofType(ProfileEntryType.ALLERGY).map { profileLine(it) },
            )
            addSection(
                heading = context.getString(R.string.profile_condition_title),
                entries = data.profileEntries.ofType(ProfileEntryType.CONDITION).map { profileLine(it) },
            )

            addSection(
                heading = context.getString(R.string.data_pdf_medications_title),
                entries = data.medications.flatMap { medicationLines(it) },
            )
            addSection(
                heading = context.getString(R.string.history_pdf_doses_title),
                entries =
                    data.doses.map { dose ->
                        val schedule =
                            data.medications
                                .firstOrNull { it.medication.id == dose.medicationId }
                        lines.doseLine(
                            scheduledAt = dose.scheduledAt,
                            medicationName = schedule?.medication?.name.orEmpty(),
                            doseAmount = schedule?.schedules?.firstOrNull()?.doseAmount,
                            statusWord = lines.doseStatusWord(dose.status),
                        )
                    },
            )
            addSection(
                heading = context.getString(R.string.history_pdf_symptoms_title),
                entries =
                    data.symptoms.map { symptom ->
                        lines.symptomLine(
                            loggedAt = symptom.loggedAt,
                            name = symptom.name,
                            severity = symptom.severity,
                            note = symptom.note,
                        )
                    },
            )

            add(PdfBlock.Footer(context.getString(R.string.history_pdf_footer)))
        }

    private fun MutableList<PdfBlock>.addSection(
        heading: String,
        entries: List<String>,
    ) {
        add(PdfBlock.Heading(heading))
        if (entries.isEmpty()) {
            add(PdfBlock.Body(context.getString(R.string.history_pdf_none)))
        } else {
            entries.forEach { add(PdfBlock.Body(it)) }
        }
    }

    private fun List<ProfileEntry>.ofType(type: ProfileEntryType): List<ProfileEntry> = filter { it.type == type }

    private fun profileLine(entry: ProfileEntry): String =
        lines.join(entry.label, entry.note?.takeIf { it.isNotBlank() })

    private fun medicationLines(item: MedicationWithSchedules): List<String> {
        val medication = item.medication
        val form = context.getString(medication.form.labelRes())
        val head =
            lines.join(
                medication.name,
                form,
                medication.strength.takeIf { it.isNotBlank() },
                context.getString(medication.status.labelRes()),
                lines.date(medication.startDate),
            )
        return listOf(head) + item.schedules.map { scheduleLine(it) }
    }

    private fun scheduleLine(schedule: Schedule): String {
        val type = context.getString(schedule.type.labelRes())
        val whenText =
            when {
                schedule.times.isNotEmpty() -> schedule.times.joinToString(", ") { time(it) }
                schedule.intervalHours != null ->
                    context.getString(
                        R.string.data_pdf_every_hours,
                        schedule.intervalHours,
                    )
                else -> null
            }
        return lines.join(
            type,
            whenText,
            schedule.doseAmount.takeIf { it.isNotBlank() },
            context.getString(schedule.foodRelation.labelRes()),
        )
    }

    private fun time(time: LocalTime): String {
        val calendar =
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, time.hour)
                set(java.util.Calendar.MINUTE, time.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
        return android.text.format.DateFormat
            .getTimeFormat(context)
            .format(calendar.time)
    }
}
