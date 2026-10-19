package com.medcompanion.app.data.maintenance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.core.locale.LanguageApplier
import com.medcompanion.app.data.local.MedCompanionDatabase
import com.medcompanion.app.data.local.entity.DoseEventEntity
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.data.local.entity.ProfileEntryEntity
import com.medcompanion.app.data.local.entity.ScheduleEntity
import com.medcompanion.app.data.local.entity.SymptomLogEntity
import com.medcompanion.app.data.reminders.DoseNotificationFactory
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.reminder.DoseAlarmScheduler
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.domain.settings.FakeSettingsRepository
import io.mockk.mockk
import io.mockk.verify
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
import java.time.Instant

/**
 * "Delete all my data" (T8a, D-014). The test runs on a real in-memory
 * database: it checks that every table is empty afterwards, that the reminders
 * of the deleted doses were cancelled first, and that the consent record is
 * the only thing the settings keep.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DataMaintenanceImplTest {
    private lateinit var database: MedCompanionDatabase

    private val settings =
        FakeSettingsRepository(
            AppSettings(language = AppLanguage.ENGLISH, emergencyNumber = "997", onboardingCompleted = true),
        )

    private val alarms: DoseAlarmScheduler = mockk(relaxed = true)

    private val notifications: DoseNotificationFactory = mockk(relaxed = true)

    private val applied = mutableListOf<AppLanguage>()

    private lateinit var maintenance: DataMaintenanceImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MedCompanionDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        maintenance =
            DataMaintenanceImpl(
                database = database,
                doseEventDao = database.doseEventDao(),
                alarmScheduler = alarms,
                notificationFactory = notifications,
                settingsRepository = settings,
                languageApplier = LanguageApplier { applied += it },
            )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `every table is empty after deleting all the data`() =
        runTest {
            val medicationId = seed()

            maintenance.deleteAllData()

            assertTrue(
                database
                    .medicationDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            assertTrue(database.scheduleDao().getByMedication(medicationId).isEmpty())
            assertTrue(
                database
                    .doseEventDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            assertTrue(
                database
                    .symptomLogDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
            assertTrue(
                database
                    .profileEntryDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `the reminders of the deleted doses are cancelled`() =
        runTest {
            val medicationId = database.medicationDao().insert(medication())
            database.doseEventDao().insertIgnoringDuplicates(dose(medicationId, scheduledAt = NOW))
            database.doseEventDao().insertIgnoringDuplicates(dose(medicationId, scheduledAt = NOW.plusSeconds(3600)))

            maintenance.deleteAllData()

            verify(exactly = 2) { alarms.cancel(any()) }
            verify(exactly = 2) { notifications.cancel(any()) }
        }

    @Test
    fun `the settings go back to their defaults but the consent stays`() =
        runTest {
            maintenance.deleteAllData()

            val current = settings.current
            assertEquals(AppLanguage.SYSTEM, current.language)
            assertEquals("", current.emergencyNumber)
            assertTrue(current.onboardingCompleted)
        }

    @Test
    fun `the app language goes back to the device language`() =
        runTest {
            maintenance.deleteAllData()

            assertEquals(listOf(AppLanguage.SYSTEM), applied)
        }

    @Test
    fun `nothing to delete is not an error`() =
        runTest {
            maintenance.deleteAllData()

            assertTrue(
                database
                    .doseEventDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    private suspend fun seed(): Long {
        val medicationId = database.medicationDao().insert(medication())
        database.scheduleDao().insert(
            ScheduleEntity(
                medicationId = medicationId,
                type = ScheduleType.DAILY_TIMES,
                times = emptyList(),
                intervalHours = null,
                weekdays = emptySet(),
                doseAmount = "1 tablet",
                foodRelation = FoodRelation.ANY,
            ),
        )
        database.doseEventDao().insertIgnoringDuplicates(dose(medicationId, scheduledAt = NOW))
        database.symptomLogDao().insert(
            SymptomLogEntity(loggedAt = NOW, name = "Headache", severity = 3, note = null),
        )
        database.profileEntryDao().insert(
            ProfileEntryEntity(type = ProfileEntryType.ALLERGY, label = "Penicillin", note = null),
        )
        return medicationId
    }

    private fun medication() =
        MedicationEntity(
            name = "Metformin",
            form = MedicationForm.TABLET,
            strength = "500 mg",
            instructions = "With food",
            source = MedicationSource.DOCTOR,
            startDate = NOW,
            endDate = null,
            status = MedicationStatus.ACTIVE,
            stopReason = null,
            createdAt = NOW,
        )

    private fun dose(
        medicationId: Long,
        scheduledAt: Instant,
    ) = DoseEventEntity(
        medicationId = medicationId,
        scheduledAt = scheduledAt,
        status = DoseStatus.PENDING,
        actedAt = null,
        note = null,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-17T08:00:00Z")
    }
}
