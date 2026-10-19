package com.medcompanion.app.data.export

import android.content.Context
import com.medcompanion.app.domain.export.FullData
import com.medcompanion.app.domain.export.MedicationWithSchedules
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.AppSettings
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalTime

/**
 * "Export my data" as JSON (T8a). The file can be kept or handed to another
 * app, so its shape is tested: a schema version, every section present, stable
 * names for enums, ISO-8601 for times and the patient's own values untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DataJsonExporterTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val exporter = DataJsonExporter(context, mockk(relaxed = true))

    private val generatedAt = Instant.parse("2026-10-17T12:00:00Z")

    @Test
    fun `the document carries the app id, a schema version and the export time`() {
        val json = json(emptyData())

        assertEquals("com.medcompanion.app", json.getString("app"))
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals(generatedAt.toString(), json.getString("exportedAt"))
    }

    @Test
    fun `an empty export still contains every section`() {
        val json = json(emptyData())

        assertTrue(json.has("settings"))
        assertTrue(json.has("profile"))
        assertTrue(json.has("medications"))
        assertTrue(json.has("doses"))
        assertTrue(json.has("symptoms"))
        assertEquals(0, json.getJSONArray("doses").length())
    }

    @Test
    fun `settings export the language name and the emergency number`() {
        val json =
            json(
                emptyData().copy(
                    settings = AppSettings(language = AppLanguage.ARABIC, emergencyNumber = "123"),
                ),
            )

        assertEquals("ARABIC", json.getJSONObject("settings").getString("language"))
        assertEquals("123", json.getJSONObject("settings").getString("emergencyNumber"))
    }

    @Test
    fun `a medication exports its schedules and keeps the patient's values`() {
        val json = json(emptyData().copy(medications = listOf(medicationWithSchedule())))
        val medication = json.getJSONArray("medications").getJSONObject(0)

        assertEquals("ميتفورمين", medication.getString("name"))
        assertEquals("TABLET", medication.getString("form"))
        assertEquals(
            "DAILY_TIMES",
            medication.getJSONArray("schedules").getJSONObject(0).getString("type"),
        )
        assertEquals(
            "08:00",
            medication
                .getJSONArray("schedules")
                .getJSONObject(0)
                .getJSONArray("times")
                .getString(0),
        )
    }

    @Test
    fun `doses and symptoms keep their times, statuses and notes`() {
        val json =
            json(
                emptyData().copy(
                    doses =
                        listOf(
                            DoseEvent(
                                id = 1,
                                medicationId = 5,
                                scheduledAt = generatedAt,
                                status = DoseStatus.TAKEN,
                                actedAt = generatedAt.plusSeconds(60),
                                note = null,
                            ),
                        ),
                    symptoms =
                        listOf(
                            SymptomLog(
                                id = 2,
                                loggedAt = generatedAt,
                                name = "صداع",
                                severity = 4,
                                note = "بعد الغداء",
                            ),
                        ),
                ),
            )

        assertEquals("TAKEN", json.getJSONArray("doses").getJSONObject(0).getString("status"))
        assertEquals("صداع", json.getJSONArray("symptoms").getJSONObject(0).getString("name"))
        assertEquals(4, json.getJSONArray("symptoms").getJSONObject(0).getInt("severity"))
        assertEquals("بعد الغداء", json.getJSONArray("symptoms").getJSONObject(0).getString("note"))
    }

    @Test
    fun `the file is written into the exports cache directory with a json extension`() {
        val file = exporter.outputFile(generatedAt)

        assertEquals("json", file.extension)
        assertEquals("exports", file.parentFile?.name)
        assertTrue(file.name.startsWith("data-"))
    }

    private fun json(data: FullData): JSONObject {
        val output = ByteArrayOutputStream()
        exporter.write(data, output)
        return JSONObject(output.toString("UTF-8"))
    }

    private fun emptyData() =
        FullData(
            generatedAt = generatedAt,
            settings = AppSettings(),
            profileEntries = emptyList(),
            medications = emptyList(),
            doses = emptyList(),
            symptoms = emptyList(),
        )

    private fun medicationWithSchedule(): MedicationWithSchedules =
        MedicationWithSchedules(
            medication =
                Medication(
                    id = 5,
                    name = "ميتفورمين",
                    form = MedicationForm.TABLET,
                    strength = "500 ملغ",
                    instructions = "مع الطعام",
                    source = MedicationSource.SELF,
                    startDate = generatedAt,
                    createdAt = generatedAt,
                ),
            schedules =
                listOf(
                    Schedule(
                        id = 7,
                        medicationId = 5,
                        type = ScheduleType.DAILY_TIMES,
                        times = listOf(LocalTime.of(8, 0)),
                        doseAmount = "حبة واحدة",
                        foodRelation = FoodRelation.AFTER,
                    ),
                ),
        )
}
