package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.domain.export.FullData
import com.medcompanion.app.domain.export.MedicationWithSchedules
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileSectionState
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.SymptomLog
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Export my data" as JSON (T8a): every value the patient entered, in a
 * documented shape, so it stays readable outside the app.
 *
 * Instants and times are ISO-8601 strings and enums are their stable names on
 * purpose: the file is for machines and for a person who moves their data
 * elsewhere, so it must not depend on the phone's locale or digit system.
 */
@Singleton
class DataJsonExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val collector: FullDataCollector,
    ) {
        /** Collects the data and writes it; returns the shareable content Uri. */
        suspend fun export(): String {
            val data = collector.collect()
            val file = outputFile(data.generatedAt)
            file.outputStream().use { write(data, it) }
            return exportUri(context, file)
        }

        /** The file the export writes; `cacheDir/exports/data-<millis>.json`. */
        internal fun outputFile(generatedAt: Instant): File = exportFile(context, FILE_PREFIX, EXTENSION, generatedAt)

        /** Writes the document; the caller owns [output]. */
        fun write(
            data: FullData,
            output: OutputStream,
        ) {
            val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
            writer.write(toJson(data).toString(INDENT))
            writer.flush()
        }

        private fun toJson(data: FullData): JSONObject =
            JSONObject()
                .put("app", APP_ID)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("exportedAt", data.generatedAt.toString())
                .put(
                    "settings",
                    JSONObject()
                        .put("language", data.settings.language.name)
                        .put("emergencyNumber", data.settings.emergencyNumber),
                ).put("basics", data.patientBasics?.let(::basicsJson) ?: JSONObject.NULL)
                .put("profile", data.profileEntries.toJsonArray(::profileJson))
                .put("medicalHistory", data.medicalHistory.toJsonArray(::historyJson))
                .put("profileSections", data.sectionStates.toJsonArray(::sectionJson))
                .put("medications", data.medications.toJsonArray(::medicationJson))
                .put("doses", data.doses.toJsonArray(::doseJson))
                .put("symptoms", data.symptoms.toJsonArray(::symptomJson))

        private fun basicsJson(basics: PatientBasics): JSONObject =
            JSONObject()
                .put("birthYear", basics.birthYear ?: JSONObject.NULL)
                .put("sex", basics.sex?.name ?: JSONObject.NULL)
                .put("pregnantOrBreastfeeding", basics.pregnantOrBreastfeeding ?: JSONObject.NULL)
                .put("weightKg", basics.weightKg ?: JSONObject.NULL)

        private fun profileJson(entry: ProfileEntry): JSONObject =
            JSONObject()
                .put("type", entry.type.name)
                .put("label", entry.label)
                .put("severity", entry.severity ?: JSONObject.NULL)
                .put("reaction", entry.reaction ?: JSONObject.NULL)
                .put("sinceYear", entry.sinceYear ?: JSONObject.NULL)
                .put("note", entry.note.orEmpty())

        private fun historyJson(entry: MedicalHistoryEntry): JSONObject =
            JSONObject()
                .put("type", entry.type.name)
                .put("label", entry.label)
                .put("year", entry.year ?: JSONObject.NULL)
                .put("note", entry.note ?: JSONObject.NULL)

        private fun sectionJson(state: ProfileSectionState): JSONObject =
            JSONObject()
                .put("section", state.section.name)
                .put("status", state.status.name)
                .put("confirmedAt", state.confirmedAt?.toString() ?: JSONObject.NULL)

        private fun medicationJson(item: MedicationWithSchedules): JSONObject {
            val medication = item.medication
            return JSONObject()
                .put("name", medication.name)
                .put("form", medication.form.name)
                .put("strength", medication.strength)
                .put("instructions", medication.instructions)
                .put("source", medication.source.name)
                .put("status", medication.status.name)
                .put("startDate", medication.startDate.toString())
                .put("endDate", medication.endDate?.toString() ?: JSONObject.NULL)
                .put("stopReason", medication.stopReason ?: JSONObject.NULL)
                .put("createdAt", medication.createdAt.toString())
                .put("expiryDate", medication.expiryDate?.toString() ?: JSONObject.NULL)
                .put("openedAt", medication.openedAt?.toString() ?: JSONObject.NULL)
                .put("remainingDoses", medication.remainingDoses ?: JSONObject.NULL)
                .put("schedules", item.schedules.toJsonArray(::scheduleJson))
        }

        private fun scheduleJson(schedule: Schedule): JSONObject =
            JSONObject()
                .put("type", schedule.type.name)
                .put("times", JSONArray(schedule.times.map { it.toString() }))
                .put("intervalHours", schedule.intervalHours ?: JSONObject.NULL)
                .put(
                    "weekdays",
                    JSONArray(schedule.weekdays.sortedBy { it.value }.map { it.name }),
                ).put("doseAmount", schedule.doseAmount)
                .put("foodRelation", schedule.foodRelation.name)

        private fun doseJson(dose: DoseEvent): JSONObject =
            JSONObject()
                .put("scheduledAt", dose.scheduledAt.toString())
                .put("status", dose.status.name)
                .put("actedAt", dose.actedAt?.toString() ?: JSONObject.NULL)
                .put("note", dose.note ?: JSONObject.NULL)

        private fun symptomJson(symptom: SymptomLog): JSONObject =
            JSONObject()
                .put("loggedAt", symptom.loggedAt.toString())
                .put("name", symptom.name)
                .put("severity", symptom.severity)
                .put("note", symptom.note ?: JSONObject.NULL)

        private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray =
            JSONArray().apply { this@toJsonArray.forEach { put(toJson(it)) } }

        private companion object {
            const val APP_ID = "com.medcompanion.app"

            /**
             * Version 2 added the health profile: `basics`, the new profile
             * entry fields, `medicalHistory` and `profileSections`.
             */
            const val SCHEMA_VERSION = 2
            const val FILE_PREFIX = "data-"
            const val EXTENSION = "json"
            const val INDENT = 2
        }
    }
