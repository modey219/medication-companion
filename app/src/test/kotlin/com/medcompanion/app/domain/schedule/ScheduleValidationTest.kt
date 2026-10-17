package com.medcompanion.app.domain.schedule

import com.medcompanion.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleValidationTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 17)

    private fun draft(
        name: String = "Medication",
        startDate: LocalDate = today,
        endDate: LocalDate? = null,
        scheduleType: ScheduleType = ScheduleType.DAILY_TIMES,
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        intervalHours: Int? = null,
        weekdays: Set<DayOfWeek> = emptySet(),
        doseAmount: String = "1 tablet",
    ) = MedicationDraft(
        name = name,
        startDate = startDate,
        endDate = endDate,
        scheduleType = scheduleType,
        times = times,
        intervalHours = intervalHours,
        weekdays = weekdays,
        doseAmount = doseAmount,
    )

    // --- Shared rules ---

    @Test
    fun `valid daily times draft passes`() {
        assertEquals(emptySet<DraftError>(), validateDraft(draft()))
    }

    @Test
    fun `blank name is rejected`() {
        assertTrue(DraftError.NAME_REQUIRED in validateDraft(draft(name = "   ")))
    }

    @Test
    fun `blank dose amount is rejected`() {
        assertTrue(DraftError.DOSE_AMOUNT_REQUIRED in validateDraft(draft(doseAmount = "")))
    }

    @Test
    fun `end date before start date is rejected`() {
        assertTrue(
            DraftError.END_BEFORE_START in
                validateDraft(draft(startDate = today, endDate = today.minusDays(1))),
        )
    }

    @Test
    fun `end date equal to start date is allowed`() {
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(draft(startDate = today, endDate = today)),
        )
    }

    @Test
    fun `null end date is allowed`() {
        assertEquals(emptySet<DraftError>(), validateDraft(draft(endDate = null)))
    }

    // --- Daily times ---

    @Test
    fun `daily times requires at least one time`() {
        val errors = validateDraft(draft(scheduleType = ScheduleType.DAILY_TIMES, times = emptyList()))
        assertEquals(setOf(DraftError.TIMES_REQUIRED), errors)
    }

    @Test
    fun `daily times rejects duplicate times`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(8, 0))
        val errors = validateDraft(draft(scheduleType = ScheduleType.DAILY_TIMES, times = times))
        assertEquals(setOf(DraftError.TIMES_DUPLICATE), errors)
    }

    @Test
    fun `daily times accepts several distinct times`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 30), LocalTime.of(22, 0))
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(draft(scheduleType = ScheduleType.DAILY_TIMES, times = times)),
        )
    }

    // --- Every N hours ---

    @Test
    fun `every n hours requires an interval`() {
        val errors =
            validateDraft(
                draft(scheduleType = ScheduleType.EVERY_N_HOURS, times = emptyList(), intervalHours = null),
            )
        assertEquals(setOf(DraftError.INTERVAL_REQUIRED), errors)
    }

    @Test
    fun `every n hours accepts interval bounds 1 and 24`() {
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(draft(scheduleType = ScheduleType.EVERY_N_HOURS, times = emptyList(), intervalHours = 1)),
        )
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(draft(scheduleType = ScheduleType.EVERY_N_HOURS, times = emptyList(), intervalHours = 24)),
        )
    }

    @Test
    fun `every n hours rejects interval below 1 and above 24`() {
        assertTrue(
            DraftError.INTERVAL_OUT_OF_RANGE in
                validateDraft(draft(scheduleType = ScheduleType.EVERY_N_HOURS, times = emptyList(), intervalHours = 0)),
        )
        assertTrue(
            DraftError.INTERVAL_OUT_OF_RANGE in
                validateDraft(
                    draft(scheduleType = ScheduleType.EVERY_N_HOURS, times = emptyList(), intervalHours = 25),
                ),
        )
    }

    @Test
    fun `every n hours ignores times and weekdays`() {
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(
                draft(
                    scheduleType = ScheduleType.EVERY_N_HOURS,
                    times = emptyList(),
                    intervalHours = 6,
                    weekdays = emptySet(),
                ),
            ),
        )
    }

    // --- Weekdays ---

    @Test
    fun `weekdays requires at least one day and one time`() {
        val errors =
            validateDraft(
                draft(
                    scheduleType = ScheduleType.WEEKDAYS,
                    times = emptyList(),
                    weekdays = emptySet(),
                ),
            )
        assertEquals(setOf(DraftError.WEEKDAYS_REQUIRED, DraftError.TIMES_REQUIRED), errors)
    }

    @Test
    fun `weekdays with day but no time still needs a time`() {
        val errors =
            validateDraft(
                draft(
                    scheduleType = ScheduleType.WEEKDAYS,
                    times = emptyList(),
                    weekdays = setOf(DayOfWeek.SUNDAY),
                ),
            )
        assertEquals(setOf(DraftError.TIMES_REQUIRED), errors)
    }

    @Test
    fun `weekdays with day and time passes`() {
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(
                draft(
                    scheduleType = ScheduleType.WEEKDAYS,
                    times = listOf(LocalTime.of(9, 0)),
                    weekdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY),
                ),
            ),
        )
    }

    // --- As needed ---

    @Test
    fun `as needed ignores times interval and weekdays`() {
        assertEquals(
            emptySet<DraftError>(),
            validateDraft(
                draft(
                    scheduleType = ScheduleType.AS_NEEDED,
                    times = emptyList(),
                    intervalHours = null,
                    weekdays = emptySet(),
                ),
            ),
        )
    }

    @Test
    fun `as needed still requires name and dose amount`() {
        val errors =
            validateDraft(
                draft(
                    scheduleType = ScheduleType.AS_NEEDED,
                    times = emptyList(),
                    name = "",
                    doseAmount = "",
                ),
            )
        assertEquals(setOf(DraftError.NAME_REQUIRED, DraftError.DOSE_AMOUNT_REQUIRED), errors)
    }

    // --- Multiple errors at once ---

    @Test
    fun `all applicable errors are reported together`() {
        val errors =
            validateDraft(
                draft(
                    name = "",
                    startDate = today,
                    endDate = today.minusDays(1),
                    scheduleType = ScheduleType.WEEKDAYS,
                    times = emptyList(),
                    weekdays = emptySet(),
                    doseAmount = "",
                ),
            )
        assertEquals(
            setOf(
                DraftError.NAME_REQUIRED,
                DraftError.DOSE_AMOUNT_REQUIRED,
                DraftError.END_BEFORE_START,
                DraftError.WEEKDAYS_REQUIRED,
                DraftError.TIMES_REQUIRED,
            ),
            errors,
        )
    }
}
