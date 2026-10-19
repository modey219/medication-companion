package com.medcompanion.app.data.assistant

import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType

/**
 * Renders one schedule as the locale-independent text both assistant blocks use
 * (T10 and T13). The model reads it, so it must not depend on the phone's
 * language or digit style: enum values are their stable names and the times are
 * the 24-hour ones the patient entered. Everything the patient typed inside a
 * field is passed verbatim.
 *
 * TODO(clinical-review): the field names the model sees, and whether the
 * schedules should travel at all.
 */
internal object MedicationScheduleText {
    fun of(schedule: Schedule): String {
        val amount =
            schedule.doseAmount
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "dose $it, " }
                .orEmpty()
        val food = "food ${schedule.foodRelation.name}, "
        return when (schedule.type) {
            ScheduleType.DAILY_TIMES ->
                "$amount${food}at ${schedule.times.joinToString(", ") { it.toString() }}"

            ScheduleType.EVERY_N_HOURS ->
                "$amount${food}every ${schedule.intervalHours ?: 0} hours"

            ScheduleType.WEEKDAYS ->
                "$amount${food}on ${
                    schedule.weekdays.sortedBy { it.value }.joinToString(", ") { it.name }
                } at ${schedule.times.joinToString(", ") { it.toString() }}"

            ScheduleType.AS_NEEDED -> "${amount}as needed".trimEnd(',', ' ')
        }
    }
}
