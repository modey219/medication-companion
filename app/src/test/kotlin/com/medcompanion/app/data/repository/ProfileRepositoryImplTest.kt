package com.medcompanion.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.data.local.MedCompanionDatabase
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionState
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Health profile (T12): the patient's basic facts, their medical history, and
 * how complete each section is. The section state is derived, so these tests
 * pin down that "never asked" is never shown as "nothing there".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class ProfileRepositoryImplTest {
    private lateinit var database: MedCompanionDatabase
    private lateinit var repository: ProfileRepositoryImpl
    private val clock = FakeAppClock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MedCompanionDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            ProfileRepositoryImpl(
                profileEntryDao = database.profileEntryDao(),
                patientBasicsDao = database.patientBasicsDao(),
                medicalHistoryEntryDao = database.medicalHistoryEntryDao(),
                profileSectionStateDao = database.profileSectionStateDao(),
                medicationDao = database.medicationDao(),
                clock = clock,
            )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `basic facts round trip and an empty set removes them`() =
        runTest {
            repository.saveBasics(
                PatientBasics(
                    birthYear = 1958,
                    sex = Sex.FEMALE,
                    pregnantOrBreastfeeding = null,
                    weightKg = 64,
                ),
            )

            val saved = repository.observeBasics().first()
            assertEquals(1958, saved?.birthYear)
            assertEquals(Sex.FEMALE, saved?.sex)
            assertEquals(64, saved?.weightKg)

            repository.saveBasics(PatientBasics())

            assertNull(repository.observeBasics().first())
        }

    @Test
    fun `history entries can be added, edited and deleted`() =
        runTest {
            val id =
                repository.addHistory(
                    MedicalHistoryEntry(
                        type = MedicalHistoryType.SURGERY,
                        label = "الزائدة الدودية",
                        year = 2008,
                    ),
                )

            assertEquals(1, repository.observeHistory().first().size)

            val stored = repository.observeHistory().first().single()
            repository.updateHistory(stored.copy(year = 2009, note = "بدون مضاعفات"))

            val updated = repository.observeHistory().first().single()
            assertEquals(id, updated.id)
            assertEquals(2009, updated.year)
            assertEquals("بدون مضاعفات", updated.note)

            repository.deleteHistory(updated)

            assertEquals(0, repository.observeHistory().first().size)
        }

    @Test
    fun `a section nobody was asked about is not asked`() =
        runTest {
            val sections = repository.observeSections().first()

            assertEquals(
                ProfileSectionStatus.NOT_ASKED,
                sections.getValue(ProfileSection.ALLERGIES).status,
            )
            assertEquals(
                ProfileSectionStatus.NOT_ASKED,
                sections.getValue(ProfileSection.MEDICATIONS).status,
            )
        }

    @Test
    fun `the patient's own confirmation shows as none confirmed`() =
        runTest {
            repository.confirmSectionEmpty(ProfileSection.CONDITIONS)

            val section = repository.observeSections().first().getValue(ProfileSection.CONDITIONS)
            assertEquals(ProfileSectionStatus.NONE_CONFIRMED, section.status)
            assertEquals(clock.now(), section.confirmedAt)
            assertNotNull(database.profileSectionStateDao().getBySection(ProfileSection.CONDITIONS))

            repository.clearSectionConfirmation(ProfileSection.CONDITIONS)

            assertEquals(
                ProfileSectionStatus.NOT_ASKED,
                sectionState(ProfileSection.CONDITIONS).status,
            )
        }

    @Test
    fun `entries win over an earlier none confirmation`() =
        runTest {
            repository.confirmSectionEmpty(ProfileSection.ALLERGIES)
            repository.add(
                ProfileEntry(type = ProfileEntryType.ALLERGY, label = "البنسلين", severity = 3),
            )

            val section = repository.observeSections().first().getValue(ProfileSection.ALLERGIES)
            assertEquals(ProfileSectionStatus.LISTED, section.status)
            assertNull(section.confirmedAt)
        }

    @Test
    fun `the medications section follows the medications table`() =
        runTest {
            database.medicationDao().insert(medication())

            assertEquals(
                ProfileSectionStatus.LISTED,
                sectionState(ProfileSection.MEDICATIONS).status,
            )

            database.medicationDao().deleteAll()

            assertEquals(
                ProfileSectionStatus.NOT_ASKED,
                sectionState(ProfileSection.MEDICATIONS).status,
            )
        }

    @Test
    fun `an entry keeps the detail fields the patient entered`() =
        runTest {
            repository.add(
                ProfileEntry(
                    type = ProfileEntryType.CONDITION,
                    label = "الربو",
                    severity = 2,
                    reaction = "ضيق في النفس",
                    sinceYear = 2015,
                    note = null,
                ),
            )

            val stored = repository.observeAll().first().single()
            assertEquals(2, stored.severity)
            assertEquals("ضيق في النفس", stored.reaction)
            assertEquals(2015, stored.sinceYear)
        }

    private suspend fun sectionState(section: ProfileSection): ProfileSectionState {
        val sections = repository.observeSections().first()
        return sections.getValue(section)
    }

    private fun medication() =
        MedicationEntity(
            name = "Metformin",
            form = MedicationForm.TABLET,
            strength = "500 mg",
            instructions = "",
            source = MedicationSource.DOCTOR,
            startDate = Instant.parse("2026-10-17T08:00:00Z"),
            endDate = null,
            status = MedicationStatus.ACTIVE,
            stopReason = null,
            createdAt = Instant.parse("2026-10-17T08:00:00Z"),
        )
}
