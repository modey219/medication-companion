package com.medcompanion.app.data.assistant

import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionState
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.model.Sex
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ProfileRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.repository.SymptomRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The patient context the assistant is personalized with (T13, D-021). The
 * builder must (a) read the profile, medicines, doses and symptoms once, (b)
 * turn missing data into "not asked" instead of a fact, and (c) keep every list
 * bounded and ordered deterministically.
 */
class PatientContextBuilderTest {
    private val profile = mockk<ProfileRepository>()
    private val medications = mockk<MedicationRepository>()
    private val schedules = mockk<ScheduleRepository>()
    private val doses = mockk<DoseEventRepository>()
    private val symptoms = mockk<SymptomRepository>()
    private val clock = FakeAppClock()

    private val builder =
        PatientContextBuilder(profile, medications, schedules, doses, symptoms, clock)

    @Before
    fun setUp() {
        every { profile.observeBasics() } returns flowOf(null)
        every { profile.observeAll() } returns flowOf(emptyList())
        every { profile.observeHistory() } returns flowOf(emptyList())
        every { profile.observeSections() } returns flowOf(emptyMap<ProfileSection, ProfileSectionState>())
        every { medications.observeAll() } returns flowOf(emptyList())
        coEvery { schedules.getByMedication(any()) } returns emptyList()
        every { doses.observeBetween(any(), any()) } returns flowOf(emptyList())
        every { symptoms.observeBetween(any(), any()) } returns flowOf(emptyList())
    }

    @Test
    fun `an empty profile still builds a context`() =
        runTest {
            val context = builder.build()

            assertEquals(clock.now(), context.generatedAt)
            assertNull(context.ageYears)
            assertNull(context.sex)
            assertTrue(context.allergies.isEmpty())
            assertTrue(context.medications.isEmpty())
            assertEquals(0, context.adherenceLast14Days?.recorded)
        }

    @Test
    fun `the age comes from the birth year alone`() =
        runTest {
            every { profile.observeBasics() } returns
                flowOf(PatientBasics(birthYear = 1964, sex = Sex.FEMALE, weightKg = 70))

            val context = builder.build()

            assertEquals(clock.today().year - 1964, context.ageYears)
            assertEquals(Sex.FEMALE, context.sex)
            assertEquals(70, context.weightKg)
        }

    @Test
    fun `a birth year in the future never becomes a negative age`() =
        runTest {
            every { profile.observeBasics() } returns flowOf(PatientBasics(birthYear = 2100))

            assertNull(builder.build().ageYears)
        }

    @Test
    fun `allergies and conditions travel with their detail`() =
        runTest {
            every { profile.observeAll() } returns
                flowOf(
                    listOf(
                        ProfileEntry(
                            id = 2,
                            type = ProfileEntryType.ALLERGY,
                            label = "بنسلين",
                            severity = 2,
                            reaction = "طفح",
                            sinceYear = 2019,
                        ),
                        ProfileEntry(id = 1, type = ProfileEntryType.CONDITION, label = "سكري"),
                    ),
                )

            val context = builder.build()

            assertEquals("بنسلين", context.allergies.single().label)
            assertEquals(2, context.allergies.single().severity)
            assertEquals("طفح", context.allergies.single().reaction)
            assertEquals("سكري", context.conditions.single().label)
            assertNull(context.conditions.single().severity)
        }

    @Test
    fun `the section status is carried through so empty is never none`() =
        runTest {
            every { profile.observeSections() } returns
                flowOf(
                    mapOf(
                        ProfileSection.ALLERGIES to
                            ProfileSectionState(ProfileSection.ALLERGIES, ProfileSectionStatus.NONE_CONFIRMED),
                        ProfileSection.CONDITIONS to
                            ProfileSectionState(ProfileSection.CONDITIONS, ProfileSectionStatus.NOT_ASKED),
                    ),
                )

            val context = builder.build()

            assertEquals(ProfileSectionStatus.NONE_CONFIRMED, context.sections[ProfileSection.ALLERGIES])
            assertEquals(ProfileSectionStatus.NOT_ASKED, context.sections[ProfileSection.CONDITIONS])
        }

    @Test
    fun `an active medication carries the schedule text`() =
        runTest {
            every { medications.observeAll() } returns flowOf(listOf(medication(id = 1, name = "ميتفورمين")))
            coEvery { schedules.getByMedication(1) } returns
                listOf(
                    Schedule(
                        medicationId = 1,
                        type = ScheduleType.DAILY_TIMES,
                        times = listOf(java.time.LocalTime.of(8, 0)),
                        doseAmount = "1 قرص",
                        foodRelation = FoodRelation.WITH,
                    ),
                )

            val medication = builder.build().medications.single()

            assertEquals("ميتفورمين", medication.name)
            assertEquals(MedicationStatus.ACTIVE, medication.status)
            assertEquals(listOf("dose 1 قرص, food WITH, at 08:00"), medication.schedules)
        }

    @Test
    fun `only a medication stopped in the last thirty days is listed as recent`() =
        runTest {
            every { medications.observeAll() } returns
                flowOf(
                    listOf(
                        medication(
                            id = 1,
                            name = "أسبرين",
                            status = MedicationStatus.STOPPED,
                            endDate = clock.now().minusSeconds(5 * 24 * 3600L),
                            stopReason = "وصفة الطبيب",
                        ),
                        medication(
                            id = 2,
                            name = "قديم",
                            status = MedicationStatus.STOPPED,
                            endDate = clock.now().minusSeconds(60 * 24 * 3600L),
                        ),
                    ),
                )

            val context = builder.build()

            val stopped = context.stoppedLast30Days.single()
            assertEquals("أسبرين", stopped.name)
            assertEquals("وصفة الطبيب", stopped.stopReason)
            assertTrue(context.medications.isEmpty())
        }

    @Test
    fun `only taken and missed doses count as adherence`() =
        runTest {
            every { doses.observeBetween(any(), any()) } returns
                flowOf(
                    listOf(
                        dose(DoseStatus.TAKEN),
                        dose(DoseStatus.TAKEN),
                        dose(DoseStatus.MISSED),
                        dose(DoseStatus.SKIPPED),
                        dose(DoseStatus.SNOOZED),
                        dose(DoseStatus.PENDING),
                    ),
                )

            val adherence = builder.build().adherenceLast14Days!!

            assertEquals(2, adherence.taken)
            assertEquals(1, adherence.missed)
            assertEquals(6, adherence.recorded)
        }

    @Test
    fun `the dose and symptom windows are the last fourteen days`() =
        runTest {
            val start = slot<Instant>()

            builder.build()

            verify { doses.observeBetween(capture(start), any()) }
            assertEquals(NOW.minusSeconds(14 * 24 * 3600L), start.captured)
            verify { symptoms.observeBetween(NOW.minusSeconds(14 * 24 * 3600L), NOW) }
        }

    @Test
    fun `recent symptoms are newest first and capped`() =
        runTest {
            val logs =
                (1..12).map { index ->
                    SymptomLog(
                        id = index.toLong(),
                        loggedAt = NOW.minusSeconds(index * 3600L),
                        name = "عرض $index",
                        severity = (index % 5) + 1,
                    )
                }
            every { symptoms.observeBetween(any(), any()) } returns flowOf(logs)

            val recent = builder.build().recentSymptoms

            assertEquals(10, recent.size)
            assertEquals("عرض 1", recent.first().name)
            assertTrue(recent.first().loggedAt > recent.last().loggedAt)
        }

    @Test
    fun `long lists are capped`() =
        runTest {
            every { profile.observeAll() } returns
                flowOf(
                    (1..30).map { index ->
                        ProfileEntry(id = index.toLong(), type = ProfileEntryType.ALLERGY, label = "حساسية $index")
                    },
                )
            every { profile.observeHistory() } returns
                flowOf(
                    (1..30).map { index ->
                        MedicalHistoryEntry(
                            id = index.toLong(),
                            type = MedicalHistoryType.PAST_ILLNESS,
                            label = "مرض $index",
                        )
                    },
                )
            every { medications.observeAll() } returns
                flowOf((1..40).map { index -> medication(id = index.toLong(), name = "دواء $index") })

            val context = builder.build()

            assertEquals(20, context.allergies.size)
            assertEquals(20, context.medicalHistory.size)
            assertEquals(30, context.medications.size)
        }

    private fun medication(
        id: Long,
        name: String,
        status: MedicationStatus = MedicationStatus.ACTIVE,
        endDate: Instant? = null,
        stopReason: String? = null,
    ) = Medication(
        id = id,
        name = name,
        form = MedicationForm.TABLET,
        strength = "500 ملغ",
        instructions = "حبة واحدة",
        source = MedicationSource.SELF,
        startDate = NOW,
        endDate = endDate,
        status = status,
        stopReason = stopReason,
        createdAt = NOW,
    )

    private fun dose(status: DoseStatus) = DoseEvent(id = 1, medicationId = 1, scheduledAt = NOW, status = status)

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-17T12:00:00Z")
    }
}
