package com.medcompanion.app.domain.reminder

import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure generation rules (D-005: activity is the half-open instant range
 * [startDate, endDate); instants at or before `notBefore` never appear).
 */
class DoseGeneratorTest {
    private val windowStart = LocalDate.of(2026, 10, 17) // a Saturday
    private val defaultNotBefore = Instant.parse("2026-10-17T00:00:00Z")

    private fun generate(
        medications: List<Medication>,
        schedules: List<Schedule>,
        windowStart: LocalDate = this.windowStart,
        windowDays: Long = 7,
        zone: ZoneId = ZoneOffset.UTC,
        notBefore: Instant = defaultNotBefore,
    ) = DoseGenerator.generate(medications, schedules, windowStart, windowDays, zone, notBefore)

    @Test
    fun `daily times produce one dose per time per day in the window`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))),
            )

        assertEquals(14, events.size)
        assertEquals(Instant.parse("2026-10-17T08:00:00Z"), events.first().scheduledAt)
        assertEquals(Instant.parse("2026-10-23T20:00:00Z"), events.last().scheduledAt)
        assertTrue(events.all { it.medicationId == 1L })
    }

    @Test
    fun `instants at or before notBefore are never generated`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))),
                notBefore = Instant.parse("2026-10-17T12:00:00Z"),
            )

        assertEquals(13, events.size)
        assertEquals(Instant.parse("2026-10-17T20:00:00Z"), events.first().scheduledAt)
    }

    @Test
    fun `weekdays schedules generate only on the selected days`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules =
                    listOf(
                        schedule(
                            medicationId = 1,
                            type = ScheduleType.WEEKDAYS,
                            times = listOf(LocalTime.of(9, 0)),
                            weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        ),
                    ),
            )

        assertEquals(
            listOf(
                Instant.parse("2026-10-17T09:00:00Z"), // Saturday
                Instant.parse("2026-10-19T09:00:00Z"), // Monday
                Instant.parse("2026-10-21T09:00:00Z"), // Wednesday
            ),
            events.map { it.scheduledAt },
        )
    }

    @Test
    fun `every n hours is stepped absolutely from the first day of the medication`() {
        val events =
            generate(
                medications = listOf(medication(id = 1, startDate = Instant.parse("2026-10-17T00:00:00Z"))),
                schedules = listOf(schedule(medicationId = 1, type = ScheduleType.EVERY_N_HOURS, intervalHours = 7)),
            )

        // 0, 7, 14, 21 then 28h (next day 04:00): absolute stepping, not wall-clock.
        assertEquals(
            listOf(
                Instant.parse("2026-10-17T00:00:00Z"),
                Instant.parse("2026-10-17T07:00:00Z"),
                Instant.parse("2026-10-17T14:00:00Z"),
                Instant.parse("2026-10-17T21:00:00Z"),
                Instant.parse("2026-10-18T04:00:00Z"),
            ),
            events.take(5).map { it.scheduledAt },
        )
        assertEquals(4, events.count { it.scheduledAt < Instant.parse("2026-10-18T00:00:00Z") })
    }

    @Test
    fun `every n hours skips steps before the window`() {
        val events =
            generate(
                medications = listOf(medication(id = 1, startDate = Instant.parse("2026-10-01T00:00:00Z"))),
                schedules = listOf(schedule(medicationId = 1, type = ScheduleType.EVERY_N_HOURS, intervalHours = 8)),
                windowStart = LocalDate.of(2026, 10, 17),
                windowDays = 1,
                notBefore = Instant.parse("2026-10-17T12:30:00Z"),
            )

        assertEquals(listOf(Instant.parse("2026-10-17T16:00:00Z")), events.map { it.scheduledAt })
    }

    @Test
    fun `as needed schedules generate nothing`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, type = ScheduleType.AS_NEEDED)),
            )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `inactive medications generate nothing`() {
        val events =
            generate(
                medications =
                    listOf(
                        medication(id = 1, status = MedicationStatus.STOPPED),
                        medication(id = 2, status = MedicationStatus.COMPLETED),
                    ),
                schedules =
                    listOf(
                        schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0))),
                        schedule(medicationId = 2, times = listOf(LocalTime.of(8, 0))),
                    ),
            )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a medication without a schedule generates nothing`() {
        val events = generate(medications = listOf(medication(id = 1)), schedules = emptyList())

        assertTrue(events.isEmpty())
    }

    @Test
    fun `instants at or after the end date are excluded`() {
        val events =
            generate(
                medications =
                    listOf(
                        medication(
                            id = 1,
                            endDate = Instant.parse("2026-10-19T00:00:00Z"),
                        ),
                    ),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))),
            )

        assertEquals(4, events.size) // 17th and 18th only
        assertEquals(Instant.parse("2026-10-18T20:00:00Z"), events.last().scheduledAt)
    }

    @Test
    fun `instants before the medication start date are excluded`() {
        val events =
            generate(
                medications = listOf(medication(id = 1, startDate = Instant.parse("2026-10-19T00:00:00Z"))),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0)))),
            )

        assertEquals(5, events.size) // 19th to 23rd
        assertEquals(Instant.parse("2026-10-19T08:00:00Z"), events.first().scheduledAt)
    }

    @Test
    fun `times are interpreted in the given zone`() {
        val riyadh = ZoneId.of("Asia/Riyadh") // UTC+3, no DST
        val utcEvents =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0)))),
            )
        val riyadhEvents =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0)))),
                zone = riyadh,
                notBefore = Instant.parse("2026-10-16T21:00:00Z"),
            )

        assertEquals(Instant.parse("2026-10-17T08:00:00Z"), utcEvents.first().scheduledAt)
        assertEquals(Instant.parse("2026-10-17T05:00:00Z"), riyadhEvents.first().scheduledAt)
    }

    @Test
    fun `window boundaries follow the local zone across midnight`() {
        val riyadh = ZoneId.of("Asia/Riyadh")
        val events =
            generate(
                medications = listOf(medication(id = 1, startDate = Instant.parse("2026-10-16T21:00:00Z"))),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(0, 30)))),
                zone = riyadh,
                notBefore = Instant.parse("2026-10-16T21:00:00Z"),
            )

        // 00:30 in Riyadh on the 17th is 21:30 UTC on the 16th.
        assertEquals(Instant.parse("2026-10-16T21:30:00Z"), events.first().scheduledAt)
        assertEquals(7, events.size)
        assertEquals(Instant.parse("2026-10-22T21:30:00Z"), events.last().scheduledAt)
    }

    @Test
    fun `a midnight dose belongs to its local day`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules =
                    listOf(
                        schedule(
                            medicationId = 1,
                            times = listOf(LocalTime.of(0, 15), LocalTime.of(23, 59)),
                        ),
                    ),
            )

        assertEquals(14, events.size)
        assertEquals(Instant.parse("2026-10-17T00:15:00Z"), events.first().scheduledAt)
        assertEquals(Instant.parse("2026-10-23T23:59:00Z"), events.last().scheduledAt)
    }

    @Test
    fun `generation is deterministic so repeated runs stay idempotent`() {
        val medications = listOf(medication(id = 1))
        val schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))))

        val first = generate(medications, schedules)
        val second = generate(medications, schedules)

        assertEquals(first, second)
    }

    @Test
    fun `two medications keep their own schedules`() {
        val events =
            generate(
                medications = listOf(medication(id = 1), medication(id = 2)),
                schedules =
                    listOf(
                        schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0))),
                        schedule(medicationId = 2, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
                    ),
            )

        assertEquals(7, events.count { it.medicationId == 1L })
        assertEquals(14, events.count { it.medicationId == 2L })
    }

    @Test
    fun `an empty window produces nothing`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0)))),
                windowDays = 0,
            )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `invalid hourly intervals produce nothing`() {
        val events =
            generate(
                medications = listOf(medication(id = 1)),
                schedules = listOf(schedule(medicationId = 1, type = ScheduleType.EVERY_N_HOURS, intervalHours = 99)),
            )

        assertTrue(events.isEmpty())
    }

    private fun medication(
        id: Long,
        startDate: Instant = Instant.parse("2026-10-01T00:00:00Z"),
        endDate: Instant? = null,
        status: MedicationStatus = MedicationStatus.ACTIVE,
    ) = Medication(
        id = id,
        name = "Medication $id",
        form = MedicationForm.TABLET,
        strength = "500 mg",
        instructions = "",
        source = MedicationSource.SELF,
        startDate = startDate,
        endDate = endDate,
        status = status,
        stopReason = null,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun schedule(
        medicationId: Long,
        type: ScheduleType = ScheduleType.DAILY_TIMES,
        times: List<LocalTime> = emptyList(),
        intervalHours: Int? = null,
        weekdays: Set<DayOfWeek> = emptySet(),
    ) = Schedule(
        id = 100L + medicationId,
        medicationId = medicationId,
        type = type,
        times = times,
        intervalHours = intervalHours,
        weekdays = weekdays,
        doseAmount = "1 tablet",
        foodRelation = FoodRelation.ANY,
    )
}
