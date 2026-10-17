package com.medcompanion.app.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * When a [Medication] should be taken. Exactly one schedule per medication in v1.
 *
 * - [ScheduleType.DAILY_TIMES] uses [times].
 * - [ScheduleType.EVERY_N_HOURS] uses [intervalHours].
 * - [ScheduleType.WEEKDAYS] uses [times] and [weekdays].
 * - [ScheduleType.AS_NEEDED] uses neither.
 */
data class Schedule(
    val id: Long = 0,
    val medicationId: Long,
    val type: ScheduleType,
    val times: List<LocalTime> = emptyList(),
    val intervalHours: Int? = null,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val doseAmount: String,
    val foodRelation: FoodRelation = FoodRelation.ANY,
)
