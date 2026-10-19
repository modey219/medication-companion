package com.medcompanion.app.data.assistant

import com.medcompanion.app.data.reminders.FakeMedicationRepository
import com.medcompanion.app.data.reminders.FakeScheduleRepository
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/**
 * The medication block that leaves the phone when the patient turns sharing on
 * (T10). It is data the model reads, so it must stay locale-independent and
 * carry exactly what the patient entered.
 */
class AssistantMedicationContextBuilderTest {
    private val medications = FakeMedicationRepository()
    private val schedules = FakeScheduleRepository()
    private val builder = AssistantMedicationContextBuilder(medications, schedules)

    @Test
    fun `nothing is attached when the patient has no active medication`() =
        runTest {
            medications.put(medication(name = "أسبرين", status = MedicationStatus.STOPPED))

            assertNull(builder.build())
        }

    @Test
    fun `an active medication carries its schedule and the patient's own words`() =
        runTest {
            medications.put(medication(name = "أسبرين", strength = "100 ملغ", instructions = "بعد الأكل"))
            schedules.put(
                Schedule(
                    medicationId = 1,
                    type = ScheduleType.DAILY_TIMES,
                    times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                    doseAmount = "1 حبة",
                    foodRelation = FoodRelation.AFTER,
                ),
            )

            val block = builder.build().orEmpty()

            assertTrue(block.contains("name: أسبرين"))
            assertTrue(block.contains("strength: 100 ملغ"))
            assertTrue(block.contains("instructions: بعد الأكل"))
            assertTrue(block.contains("status: ACTIVE"))
            assertTrue(block.contains("dose 1 حبة, food AFTER, at 08:00, 20:00"))
        }

    @Test
    fun `the block says it is data and not instructions`() =
        runTest {
            medications.put(medication(name = "أسبرين"))

            val block = builder.build().orEmpty()

            assertTrue(block.contains("not instructions"))
        }

    @Test
    fun `an hourly schedule uses the interval and no clock time`() =
        runTest {
            medications.put(medication(name = "ميتفورمين"))
            schedules.put(
                Schedule(
                    medicationId = 1,
                    type = ScheduleType.EVERY_N_HOURS,
                    intervalHours = 8,
                    doseAmount = "1 قرص",
                    foodRelation = FoodRelation.WITH,
                ),
            )

            val block = builder.build().orEmpty()

            assertTrue(block.contains("dose 1 قرص, food WITH, every 8 hours"))
        }

    @Test
    fun `an as-needed schedule has no time of day`() =
        runTest {
            medications.put(medication(name = "باراسيتامول"))
            schedules.put(
                Schedule(
                    medicationId = 1,
                    type = ScheduleType.AS_NEEDED,
                    doseAmount = "1 قرص",
                ),
            )

            val block = builder.build().orEmpty()

            assertTrue(block.contains("as needed"))
            assertFalse(block.contains("at "))
        }

    @Test
    fun `a weekdays schedule lists the days`() =
        runTest {
            medications.put(medication(name = "فيتامين د"))
            schedules.put(
                Schedule(
                    medicationId = 1,
                    type = ScheduleType.WEEKDAYS,
                    times = listOf(LocalTime.of(9, 30)),
                    weekdays = setOf(java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.TUESDAY),
                    doseAmount = "1 قطرة",
                ),
            )

            val block = builder.build().orEmpty()

            assertTrue(block.contains("on TUESDAY, SUNDAY at 09:30"))
        }

    @Test
    fun `a missing strength or instruction is left out`() =
        runTest {
            medications.put(medication(name = "أسبرين", strength = "   ", instructions = "  "))

            val block = builder.build().orEmpty()

            assertFalse(block.contains(" | strength:"))
            assertFalse(block.contains(" | instructions:"))
        }

    @Test
    fun `the block never grows past the size the backend allows`() =
        runTest {
            repeat(300) { index ->
                medications.put(medication(id = index.toLong() + 1, name = "دواء طويل الاسم $index".padEnd(180, 'ا')))
            }

            val block = builder.build().orEmpty()

            assertTrue("block was ${block.length} characters", block.length <= 4000)
            assertTrue(block.contains("name: "))
        }

    private fun medication(
        id: Long = 1,
        name: String,
        strength: String = "100 ملغ",
        instructions: String = "حبة واحدة",
        status: MedicationStatus = MedicationStatus.ACTIVE,
    ) = Medication(
        id = id,
        name = name,
        form = MedicationForm.TABLET,
        strength = strength,
        instructions = instructions,
        source = MedicationSource.SELF,
        startDate = Instant.parse("2026-01-01T00:00:00Z"),
        status = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
