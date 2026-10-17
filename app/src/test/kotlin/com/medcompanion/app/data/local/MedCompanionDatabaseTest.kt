package com.medcompanion.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.data.local.entity.DoseEventEntity
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.data.local.entity.ProfileEntryEntity
import com.medcompanion.app.data.local.entity.ScheduleEntity
import com.medcompanion.app.data.local.entity.SymptomLogEntity
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * Room tests run on the JVM with Robolectric so they can run in CI without an
 * emulator. They cover round-trips, the idempotent dose constraint, cascade
 * deletes and Flow reactivity (AGENTS.md > Data model).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MedCompanionDatabaseTest {
    private lateinit var database: MedCompanionDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MedCompanionDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `medication round trips through the database`() =
        runTest {
            val id = database.medicationDao().insert(medication())

            val loaded = database.medicationDao().getById(id)

            assertEquals("Metformin", loaded?.name)
            assertEquals(MedicationForm.TABLET, loaded?.form)
            assertEquals(MedicationSource.DOCTOR, loaded?.source)
            assertEquals(MedicationStatus.ACTIVE, loaded?.status)
            assertEquals(BASE_INSTANT, loaded?.startDate)
            assertEquals(BASE_INSTANT, loaded?.createdAt)
        }

    @Test
    fun `schedule round trips times and weekdays`() =
        runTest {
            val medicationId = database.medicationDao().insert(medication())
            val schedule =
                ScheduleEntity(
                    medicationId = medicationId,
                    type = ScheduleType.WEEKDAYS,
                    times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                    intervalHours = null,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                    doseAmount = "1 tablet",
                    foodRelation = FoodRelation.AFTER,
                )

            database.scheduleDao().insert(schedule)

            val loaded = database.scheduleDao().getByMedication(medicationId).single()
            assertEquals(ScheduleType.WEEKDAYS, loaded.type)
            assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), loaded.times)
            assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), loaded.weekdays)
            assertEquals(FoodRelation.AFTER, loaded.foodRelation)
        }

    @Test
    fun `dose generation is idempotent`() =
        runTest {
            val medicationId = database.medicationDao().insert(medication())
            val dose = dose(medicationId = medicationId)

            val firstId = database.doseEventDao().insertIgnoringDuplicates(dose)
            val duplicateId = database.doseEventDao().insertIgnoringDuplicates(dose)

            assertTrue(firstId > 0)
            assertEquals(-1L, duplicateId)
            assertEquals(1, database.doseEventDao().getBetween(BASE_INSTANT, LATER_INSTANT).size)
        }

    @Test
    fun `insertAllIgnoringDuplicates skips existing rows`() =
        runTest {
            val medicationId = database.medicationDao().insert(medication())
            val doses =
                listOf(
                    dose(medicationId = medicationId, scheduledAt = BASE_INSTANT),
                    dose(medicationId = medicationId, scheduledAt = BASE_INSTANT.plusSeconds(3600)),
                )
            database.doseEventDao().insertAllIgnoringDuplicates(doses)

            val results = database.doseEventDao().insertAllIgnoringDuplicates(doses)

            assertEquals(listOf(-1L, -1L), results)
            assertEquals(2, database.doseEventDao().getBetween(BASE_INSTANT, LATER_INSTANT).size)
        }

    @Test
    fun `deleting a medication cascades to schedules and doses`() =
        runTest {
            val medication = medication()
            val medicationId = database.medicationDao().insert(medication)
            database.scheduleDao().insert(
                ScheduleEntity(
                    medicationId = medicationId,
                    type = ScheduleType.DAILY_TIMES,
                    times = listOf(LocalTime.of(9, 0)),
                    intervalHours = null,
                    weekdays = emptySet(),
                    doseAmount = "1 tablet",
                    foodRelation = FoodRelation.ANY,
                ),
            )
            database.doseEventDao().insertIgnoringDuplicates(dose(medicationId = medicationId))

            database.medicationDao().delete(medication.copy(id = medicationId))

            assertTrue(database.scheduleDao().getByMedication(medicationId).isEmpty())
            assertTrue(database.doseEventDao().getBetween(BASE_INSTANT, LATER_INSTANT).isEmpty())
        }

    @Test
    fun `observeBetween emits doses after insert`() =
        runTest {
            val medicationId = database.medicationDao().insert(medication())
            val flow = database.doseEventDao().observeBetween(BASE_INSTANT, LATER_INSTANT)

            assertTrue(flow.first().isEmpty())

            database.doseEventDao().insertIgnoringDuplicates(dose(medicationId = medicationId))

            assertEquals(1, flow.first().size)
        }

    @Test
    fun `symptom log and profile entry round trip`() =
        runTest {
            database.symptomLogDao().insert(
                SymptomLogEntity(
                    loggedAt = BASE_INSTANT,
                    name = "Headache",
                    severity = 3,
                    note = "after lunch",
                ),
            )
            database.profileEntryDao().insert(
                ProfileEntryEntity(type = ProfileEntryType.ALLERGY, label = "Penicillin", note = null),
            )

            assertEquals(
                3,
                database
                    .symptomLogDao()
                    .observeAll()
                    .first()
                    .single()
                    .severity,
            )
            assertEquals(
                "Penicillin",
                database
                    .profileEntryDao()
                    .observeByType(ProfileEntryType.ALLERGY)
                    .first()
                    .single()
                    .label,
            )
        }

    private fun medication() =
        MedicationEntity(
            name = "Metformin",
            form = MedicationForm.TABLET,
            strength = "500 mg",
            instructions = "With food",
            source = MedicationSource.DOCTOR,
            startDate = BASE_INSTANT,
            endDate = null,
            status = MedicationStatus.ACTIVE,
            stopReason = null,
            createdAt = BASE_INSTANT,
        )

    private fun dose(
        medicationId: Long,
        scheduledAt: Instant = BASE_INSTANT,
    ) = DoseEventEntity(
        medicationId = medicationId,
        scheduledAt = scheduledAt,
        status = DoseStatus.PENDING,
        actedAt = null,
        note = null,
    )

    private companion object {
        val BASE_INSTANT: Instant = Instant.parse("2026-10-17T08:00:00Z")
        val LATER_INSTANT: Instant = Instant.parse("2026-10-18T08:00:00Z")
    }
}
