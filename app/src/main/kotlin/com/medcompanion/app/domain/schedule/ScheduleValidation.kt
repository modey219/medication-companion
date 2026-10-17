package com.medcompanion.app.domain.schedule

import com.medcompanion.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Everything the add/edit form collects, before it becomes a Medication + Schedule. */
data class MedicationDraft(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val scheduleType: ScheduleType,
    val times: List<LocalTime>,
    val intervalHours: Int?,
    val weekdays: Set<DayOfWeek>,
    val doseAmount: String,
)

/** Validation problems the UI must show as field errors. Never a medical judgement. */
enum class DraftError {
    NAME_REQUIRED,
    DOSE_AMOUNT_REQUIRED,
    END_BEFORE_START,
    TIMES_REQUIRED,
    TIMES_DUPLICATE,
    INTERVAL_REQUIRED,
    INTERVAL_OUT_OF_RANGE,
    WEEKDAYS_REQUIRED,
}

private const val MIN_INTERVAL_HOURS = 1
private const val MAX_INTERVAL_HOURS = 24

/**
 * Pure validation for the medication form. Rules are mechanical only
 * (required fields, ordering, ranges) — never whether a drug or dose is
 * medically appropriate. See AGENTS.md > Medical safety rules.
 */
fun validateDraft(draft: MedicationDraft): Set<DraftError> {
    val errors = mutableSetOf<DraftError>()

    if (draft.name.isBlank()) errors += DraftError.NAME_REQUIRED
    if (draft.doseAmount.isBlank()) errors += DraftError.DOSE_AMOUNT_REQUIRED
    if (draft.endDate != null && draft.endDate < draft.startDate) errors += DraftError.END_BEFORE_START

    when (draft.scheduleType) {
        ScheduleType.DAILY_TIMES -> errors += validateTimes(draft.times)

        ScheduleType.EVERY_N_HOURS -> {
            val interval = draft.intervalHours
            when {
                interval == null -> errors += DraftError.INTERVAL_REQUIRED
                interval < MIN_INTERVAL_HOURS || interval > MAX_INTERVAL_HOURS -> {
                    errors += DraftError.INTERVAL_OUT_OF_RANGE
                }
            }
        }

        ScheduleType.WEEKDAYS -> {
            if (draft.weekdays.isEmpty()) errors += DraftError.WEEKDAYS_REQUIRED
            errors += validateTimes(draft.times)
        }

        ScheduleType.AS_NEEDED -> Unit
    }

    return errors
}

private fun validateTimes(times: List<LocalTime>): Set<DraftError> =
    buildSet {
        if (times.isEmpty()) add(DraftError.TIMES_REQUIRED)
        if (times.distinct().size != times.size) add(DraftError.TIMES_DUPLICATE)
    }
