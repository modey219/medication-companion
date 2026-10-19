package com.medcompanion.app.data.reminders

import com.medcompanion.app.core.reminder.ReminderDefaults
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.reminder.DoseAlarmScheduler
import com.medcompanion.app.domain.reminder.DoseGenerator
import com.medcompanion.app.domain.reminder.DoseReminderPayload
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.domain.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-process reminder orchestrator. Every entry point (alarm receiver,
 * system-change receiver, activity start, ViewModel mutation) funnels into
 * this class so alarms and events can never drift apart.
 *
 * Refresh is deliberately fire-and-swallow (except cancellation): it runs on
 * app start and inside receivers, where a database hiccup must degrade into
 * missing rescheduling rather than a crash. See D-008 in docs/DECISIONS.md
 * for the reconcile and missed-dose rules.
 *
 * The grace period, the snooze length and the repeat cadence come from the
 * patient's settings (T8b, D-017) and are re-read on every pass, so changing a
 * value takes effect on the next refresh without restarting the app.
 */
@Singleton
class ReminderEngineImpl
    @Inject
    constructor(
        private val doseEventRepository: DoseEventRepository,
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val alarmScheduler: DoseAlarmScheduler,
        private val notificationFactory: DoseNotificationFactory,
        private val settingsRepository: SettingsRepository,
        private val clock: AppClock,
    ) : ReminderEngine {
        override suspend fun refresh() {
            try {
                val settings = settingsRepository.observe().first()
                val graceSeconds = settings.graceSeconds()
                val snoozeSeconds = settings.snoozeSeconds()
                val now = clock.now()
                val zone = clock.zone()
                val windowStart = now.atZone(zone).toLocalDate()
                val windowEnd =
                    windowStart
                        .plusDays(ReminderDefaults.GENERATION_WINDOW_DAYS)
                        .atStartOfDay(zone)
                        .toInstant()
                val graceCutoff = now.minusSeconds(graceSeconds)

                val medications = medicationRepository.observeAll().first()
                val schedules = scheduleRepository.observeAll().first()
                val desired =
                    DoseGenerator.generate(
                        medications = medications,
                        schedules = schedules,
                        windowStart = windowStart,
                        windowDays = ReminderDefaults.GENERATION_WINDOW_DAYS,
                        zone = zone,
                        notBefore = now,
                    )

                reconcile(desired, now, graceCutoff, windowEnd)

                // Missed-dose catch-up after sleep or a device that was off:
                // silent by design, because the grace alarm owns the single
                // gentle follow-up and may not have fired.
                catchUpOverdue(graceCutoff, snoozeSeconds)

                // Live snoozes are found by action time, not scheduled time, so
                // a reboot right after snoozing an overdue dose still reminds.
                val liveSnoozes =
                    doseEventRepository
                        .getByStatusBefore(DoseStatus.SNOOZED, now)
                        .filter { (it.actedAt ?: it.scheduledAt).plusSeconds(snoozeSeconds) >= now }
                val unacted =
                    (doseEventRepository.getBetween(graceCutoff, windowEnd) + liveSnoozes)
                        .distinctBy { it.id }
                        .filter { it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED }
                unacted.forEach { dose ->
                    val reminderAt =
                        if (dose.status == DoseStatus.SNOOZED) {
                            (dose.actedAt ?: dose.scheduledAt).plusSeconds(snoozeSeconds)
                        } else {
                            dose.scheduledAt
                        }
                    // A reminder whose time passed while the device was off fires
                    // at the next refresh instead of vanishing.
                    alarmScheduler.scheduleReminder(dose.id, maxOf(reminderAt, now))
                    val graceAt = dose.scheduledAt.plusSeconds(graceSeconds)
                    if (graceAt >= now) {
                        alarmScheduler.scheduleGrace(dose.id, graceAt)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Refresh must never crash the app or a receiver; a failed pass
                // simply leaves the previous alarms in place.
            }
        }

        override suspend fun performAction(
            doseId: Long,
            status: DoseStatus,
        ) {
            doseEventRepository.setStatus(doseId, status, clock.now())
            alarmScheduler.cancel(doseId)
            notificationFactory.cancel(doseId)
            if (status == DoseStatus.TAKEN) decrementRemaining(doseId)
            if (status != DoseStatus.SNOOZED) return

            val settings = settingsRepository.observe().first()
            val now = clock.now()
            val dose = doseEventRepository.getById(doseId) ?: return
            val snoozeEnd = (dose.actedAt ?: now).plusSeconds(settings.snoozeSeconds())
            alarmScheduler.scheduleReminder(doseId, maxOf(snoozeEnd, now))
            val graceAt = dose.scheduledAt.plusSeconds(settings.graceSeconds())
            if (graceAt >= now) {
                alarmScheduler.scheduleGrace(doseId, graceAt)
            }
        }

        /**
         * Taking a dose consumes one unit when the patient tracks stock.
         * `null` means the stock is not tracked; zero floors at zero so the
         * count never goes negative.
         */
        private suspend fun decrementRemaining(doseId: Long) {
            val dose = doseEventRepository.getById(doseId) ?: return
            val medication = medicationRepository.getById(dose.medicationId) ?: return
            val remaining = medication.remainingDoses ?: return
            if (remaining <= 0) return
            medicationRepository.update(medication.copy(remainingDoses = remaining - 1))
        }

        /**
         * A reminder that fired while the dose is still unacted is repeated
         * every `repeatMinutes` until the patient acts or the grace period
         * ends; the repeat is a fresh alarm for the same dose, so acting on the
         * notification cancels it like any other.
         */
        override suspend fun onReminderFired(doseId: Long): DoseReminderPayload? {
            val settings = settingsRepository.observe().first()
            val now = clock.now()
            val dose = doseEventRepository.getById(doseId) ?: return null
            if (dose.status != DoseStatus.PENDING && dose.status != DoseStatus.SNOOZED) return null
            val graceAt = dose.scheduledAt.plusSeconds(settings.graceSeconds())
            if (graceAt >= now) {
                alarmScheduler.scheduleGrace(doseId, graceAt)
                val repeatAt = now.plusSeconds(settings.repeatSeconds())
                if (repeatAt <= graceAt) {
                    alarmScheduler.scheduleReminder(doseId, repeatAt)
                }
            }
            return payloadFor(dose)
        }

        override suspend fun onGraceFired(doseId: Long): DoseReminderPayload? {
            val dose = doseEventRepository.getById(doseId) ?: return null
            if (dose.status != DoseStatus.PENDING && dose.status != DoseStatus.SNOOZED) return null
            doseEventRepository.setStatus(doseId, DoseStatus.MISSED, null)
            alarmScheduler.cancel(doseId)
            notificationFactory.cancel(doseId)
            return payloadFor(dose)
        }

        /**
         * Deletes future unacted events the fresh generation no longer wants
         * (schedule edit, stop, timezone or clock change) and inserts the
         * desired ones; Room's unique index makes the insert idempotent.
         */
        private suspend fun reconcile(
            desired: List<DoseEvent>,
            now: Instant,
            graceCutoff: Instant,
            windowEnd: Instant,
        ) {
            val desiredKeys = desired.mapTo(mutableSetOf()) { it.medicationId to it.scheduledAt }
            doseEventRepository
                .getBetween(graceCutoff, windowEnd)
                .filter { dose ->
                    dose.scheduledAt >= now &&
                        dose.status == DoseStatus.PENDING &&
                        dose.actedAt == null &&
                        (dose.medicationId to dose.scheduledAt) !in desiredKeys
                }.forEach { stale ->
                    doseEventRepository.deleteById(stale.id)
                    alarmScheduler.cancel(stale.id)
                }
            doseEventRepository.addAllIgnoringDuplicates(desired)
        }

        /** PENDING past its grace, or SNOOZED whose snooze expired after grace. */
        private suspend fun catchUpOverdue(
            graceCutoff: Instant,
            snoozeSeconds: Long,
        ) {
            val now = clock.now()
            val overdue =
                doseEventRepository.getByStatusBefore(DoseStatus.PENDING, graceCutoff) +
                    doseEventRepository
                        .getByStatusBefore(DoseStatus.SNOOZED, graceCutoff)
                        .filter { dose ->
                            val snoozeEnd = (dose.actedAt ?: dose.scheduledAt).plusSeconds(snoozeSeconds)
                            snoozeEnd <= now
                        }
            overdue.forEach { dose -> doseEventRepository.setStatus(dose.id, DoseStatus.MISSED, null) }
        }

        private suspend fun payloadFor(dose: DoseEvent): DoseReminderPayload? {
            val medication = medicationRepository.getById(dose.medicationId) ?: return null
            val schedule = scheduleRepository.getByMedication(dose.medicationId).firstOrNull()
            return DoseReminderPayload(
                doseId = dose.id,
                medicationName = medication.name,
                strength = medication.strength,
                doseAmount = schedule?.doseAmount.orEmpty(),
                foodRelation = schedule?.foodRelation ?: FoodRelation.ANY,
            )
        }

        private fun AppSettings.graceSeconds(): Long = graceMinutes * SECONDS_PER_MINUTE

        private fun AppSettings.snoozeSeconds(): Long = snoozeMinutes * SECONDS_PER_MINUTE

        private fun AppSettings.repeatSeconds(): Long = repeatMinutes * SECONDS_PER_MINUTE

        private companion object {
            const val SECONDS_PER_MINUTE = 60L
        }
    }
