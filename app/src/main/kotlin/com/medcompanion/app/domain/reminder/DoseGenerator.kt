package com.medcompanion.app.domain.reminder

import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Pure dose-event generation for the rolling window (AGENTS.md > Data model:
 * "Generate dose events on a rolling window, for example the next 7 days.
 * Generation must be idempotent").
 *
 * Determinism: the same medications, schedules, window and zone always produce
 * the same `scheduledAt` instants, so Room's unique index on
 * (medicationId, scheduledAt) makes repeated runs insert nothing new.
 *
 * Conventions (D-005):
 * - Medication activity is the half-open instant range
 *   `[startDate, endDate)`; a null end date is open.
 * - Instants at or before [notBefore] (usually "now") are never generated, so
 *   a first run does not fabricate already-missed history and no past alarm
 *   can ever be scheduled.
 * - `EVERY_N_HOURS` is anchored at the start day of the medication in [zone]
 *   plus k x interval hours (absolute hours, deterministic across runs).
 * - `WEEKDAYS` uses [Schedule.times] only on the selected weekdays;
 *   `DAILY_TIMES` uses [Schedule.times] every day; `AS_NEEDED` generates
 *   nothing.
 */
object DoseGenerator {
    fun generate(
        medications: List<Medication>,
        schedules: List<Schedule>,
        windowStart: LocalDate,
        windowDays: Long,
        zone: ZoneId,
        notBefore: Instant,
    ): List<DoseEvent> {
        if (windowDays <= 0) return emptyList()
        val scheduleByMedication = schedules.associateBy { it.medicationId }
        val windowStartInstant = windowStart.atStartOfDay(zone).toInstant()
        val windowEndInstant = windowStart.plusDays(windowDays).atStartOfDay(zone).toInstant()
        val dates = (0 until windowDays).map { offset -> windowStart.plusDays(offset) }

        return medications
            .filter { it.status == MedicationStatus.ACTIVE }
            .flatMap { medication ->
                val schedule = scheduleByMedication[medication.id] ?: return@flatMap emptyList()
                if (schedule.type == ScheduleType.AS_NEEDED) return@flatMap emptyList()

                val candidates =
                    when (schedule.type) {
                        ScheduleType.DAILY_TIMES ->
                            dates.flatMap { date -> schedule.times.map { time -> date.atTime(time) } }
                        ScheduleType.WEEKDAYS ->
                            dates
                                .filter { date -> date.dayOfWeek in schedule.weekdays }
                                .flatMap { date -> schedule.times.map { time -> date.atTime(time) } }
                        ScheduleType.EVERY_N_HOURS ->
                            hourlyInstants(medication, schedule, zone, windowStartInstant, windowEndInstant)
                                .map { it.atZone(zone).toLocalDateTime() }
                        ScheduleType.AS_NEEDED -> emptyList()
                    }

                candidates
                    .map { it.atZone(zone).toInstant() }
                    .filter { it.isUsable(medication, windowStartInstant, windowEndInstant, notBefore) }
                    .distinct()
                    .sorted()
                    .map { doseEvent(medication, it) }
            }.sortedBy { it.scheduledAt }
    }

    private fun doseEvent(
        medication: Medication,
        scheduledAt: Instant,
    ) = DoseEvent(
        medicationId = medication.id,
        scheduledAt = scheduledAt,
    )

    private fun Instant.isUsable(
        medication: Medication,
        windowStart: Instant,
        windowEnd: Instant,
        notBefore: Instant,
    ): Boolean =
        this >= windowStart &&
            this < windowEnd &&
            this >= medication.startDate &&
            (medication.endDate == null || this < medication.endDate) &&
            this >= notBefore

    /**
     * Instants anchored at the first day of [medication] in [zone] plus
     * k x intervalHours, starting at the first multiple inside the window.
     */
    private fun hourlyInstants(
        medication: Medication,
        schedule: Schedule,
        zone: ZoneId,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<Instant> {
        val intervalHours = schedule.intervalHours ?: return emptyList()
        if (intervalHours !in 1..24) return emptyList()
        val stepSeconds = intervalHours * 3600L
        val anchor =
            medication
                .startDate
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
        val secondsToWindow = Duration.between(anchor, windowStart).seconds
        val firstStep =
            if (secondsToWindow <= 0) {
                0L
            } else {
                ceil(secondsToWindow.toDouble() / stepSeconds).toLong()
            }
        val result = mutableListOf<Instant>()
        var current = anchor.plusSeconds(firstStep * stepSeconds)
        while (current < windowEnd) {
            result += current
            current = current.plusSeconds(stepSeconds)
        }
        return result
    }
}
