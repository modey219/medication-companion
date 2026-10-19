package com.medcompanion.app.data.reminders

import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.settings.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Engine behaviour against in-memory repositories: window generation,
 * reconcile (schedule edit, stop, timezone change), missed-dose catch-up,
 * snooze rescheduling and graceful degradation without exact alarms
 * (AGENTS.md > Reminders test matrix).
 */
class ReminderEngineTest {
    private val clock = FakeAppClock() // 2026-10-17T12:00:00Z, UTC
    private val doses = FakeDoseEventRepository()
    private val medications = FakeMedicationRepository()
    private val schedules = FakeScheduleRepository()
    private val alarms = FakeDoseAlarmScheduler()
    private val settings = FakeSettingsRepository()
    private val notifications = mockk<DoseNotificationFactory>(relaxed = true)

    private fun engine() =
        ReminderEngineImpl(
            doseEventRepository = doses,
            medicationRepository = medications,
            scheduleRepository = schedules,
            alarmScheduler = alarms,
            notificationFactory = notifications,
            settingsRepository = settings,
            clock = clock,
        )

    private val now = Instant.parse("2026-10-17T12:00:00Z")

    // --- Generation and idempotency ---

    @Test
    fun `refresh generates the rolling window and schedules one alarm pair per dose`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))))

            engine().refresh()

            // 7 days x 2 times minus today's 08:00 which is already past.
            assertEquals(13, doses.events.size)
            assertEquals(13, alarms.reminders.size)
            assertEquals(13, alarms.graces.size)
            assertEquals(Instant.parse("2026-10-17T20:00:00Z"), alarms.reminders.values.min())
            // Grace is scheduledAt + 60 min, so the earliest is 21:00, not 13:00.
            assertEquals(Instant.parse("2026-10-17T21:00:00Z"), alarms.graces.values.min())
        }

    @Test
    fun `refresh is idempotent`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()

            engine.refresh()
            engine.refresh()

            assertEquals(7, doses.events.size)
        }

    @Test
    fun `a reminder whose time passed while the device was off fires at the next refresh`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(11, 30))))
            val engine = engine()
            clock.setNow(Instant.parse("2026-10-17T11:00:00Z"))
            engine.refresh() // the 11:30 dose is created and armed

            // Device off across 11:30; the alarm is lost. Back at 12:00 the
            // dose is still inside its grace period, so it is re-armed now.
            clock.setNow(now)
            alarms.clear()
            engine.refresh()

            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T11:30:00Z") }
            assertEquals(DoseStatus.PENDING, dose.status)
            assertEquals(now, alarms.reminders[dose.id])
            assertEquals(Instant.parse("2026-10-17T12:30:00Z"), alarms.graces[dose.id])
        }

    // --- Reconcile: edits, stop, timezone ---

    @Test
    fun `an edited schedule removes stale future doses and keeps history`() =
        runTest {
            medications.put(medication(id = 1))
            val schedule = schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0)))
            schedules.put(schedule)
            val engine = engine()
            engine.refresh()
            val staleIds = doses.events.map { it.id }
            val acted = doses.events.first()
            doses.setStatus(acted.id, DoseStatus.TAKEN, now)

            schedules.replace(schedule.copy(times = listOf(LocalTime.of(21, 0))))
            engine.refresh()

            val staleAt =
                doses.events.filter { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            assertTrue(staleAt.none { it.status == DoseStatus.PENDING })
            assertTrue(doses.events.any { it.id == acted.id && it.status == DoseStatus.TAKEN })
            assertTrue(doses.events.all { it.scheduledAt != Instant.parse("2026-10-18T20:00:00Z") })
            assertTrue(doses.events.any { it.scheduledAt == Instant.parse("2026-10-18T21:00:00Z") })
            // Stale alarms were cancelled (except the acted dose, cancelled by performAction semantics).
            assertTrue(staleIds.size > 1)
            assertTrue(alarms.cancelled.containsAll(staleIds.drop(1)))
        }

    @Test
    fun `stopping a medication removes its future doses and cancels their alarms`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val ids = doses.events.map { it.id }

            medications.stop(id = 1, reason = null, endDate = now)
            engine.refresh()

            assertTrue(doses.events.isEmpty())
            assertTrue(alarms.cancelled.containsAll(ids))
        }

    @Test
    fun `a timezone change moves future doses to the new local times`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(8, 0))))
            val engine = engine()
            engine.refresh()
            // Window is [today, today+7): today's 08:00 is already past, so the
            // first dose is tomorrow and the last is on the sixth day.
            assertEquals(
                Instant.parse("2026-10-18T08:00:00Z"),
                doses.events.first().scheduledAt,
            )
            assertEquals(
                Instant.parse("2026-10-23T08:00:00Z"),
                doses.events.last().scheduledAt,
            )

            clock.setZone(ZoneId.of("Asia/Riyadh")) // UTC+3
            engine.refresh()

            // Every future UTC 08:00 is stale and replaced by local 08:00 (05:00 UTC).
            assertEquals(
                listOf(
                    Instant.parse("2026-10-18T05:00:00Z"),
                    Instant.parse("2026-10-19T05:00:00Z"),
                    Instant.parse("2026-10-20T05:00:00Z"),
                    Instant.parse("2026-10-21T05:00:00Z"),
                    Instant.parse("2026-10-22T05:00:00Z"),
                    Instant.parse("2026-10-23T05:00:00Z"),
                ),
                doses.events.map { it.scheduledAt },
            )
        }

    // --- Missed-dose catch-up ---

    @Test
    fun `doses whose grace passed are marked missed silently`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()
            doses.replace(dose.copy(scheduledAt = now.minusSeconds(2 * 60 * 60), status = DoseStatus.PENDING))

            engine.refresh()

            assertEquals(DoseStatus.MISSED, doses.getById(dose.id)?.status)
            verify(exactly = 0) { notifications.showReminder(any()) }
            verify(exactly = 0) { notifications.showFollowUp(any()) }
        }

    @Test
    fun `a snoozed dose past both snooze and grace is marked missed`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()
            doses.replace(
                dose.copy(
                    scheduledAt = now.minusSeconds(3 * 60 * 60),
                    status = DoseStatus.SNOOZED,
                    actedAt = now.minusSeconds(2 * 60 * 60),
                ),
            )

            engine.refresh()

            assertEquals(DoseStatus.MISSED, doses.getById(dose.id)?.status)
        }

    @Test
    fun `a live snooze is never silently invalidated by catch-up`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()
            // Scheduled long ago, but the user snoozed one minute ago.
            doses.replace(
                dose.copy(
                    scheduledAt = now.minusSeconds(3 * 60 * 60),
                    status = DoseStatus.SNOOZED,
                    actedAt = now.minusSeconds(60),
                ),
            )

            engine.refresh()

            assertEquals(DoseStatus.SNOOZED, doses.getById(dose.id)?.status)
        }

    // --- Alarm firing ---

    @Test
    fun `onReminderFired returns the payload and arms the grace alarm`() =
        runTest {
            medications.put(medication(id = 1, name = "Metformin", strength = "500 mg"))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0)), doseAmount = "1 tablet"))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()
            alarms.clear()

            val payload = engine.onReminderFired(dose.id)

            assertNotNull(payload)
            assertEquals("Metformin", payload?.medicationName)
            assertEquals("1 tablet", payload?.doseAmount)
            assertEquals(dose.scheduledAt.plusSeconds(3600), alarms.graces[dose.id])
        }

    @Test
    fun `onReminderFired stays silent for doses that were already handled`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            doses.setStatus(dose.id, DoseStatus.TAKEN, now)

            assertNull(engine.onReminderFired(dose.id))
        }

    @Test
    fun `onGraceFired marks the dose missed and returns the follow-up payload at most once`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            val payload = engine.onGraceFired(dose.id)

            assertNotNull(payload)
            assertEquals(DoseStatus.MISSED, doses.getById(dose.id)?.status)
            assertNull(engine.onGraceFired(dose.id))
        }

    // --- Actions ---

    @Test
    fun `performAction records the status, cancels alarms and the notification`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            engine.performAction(dose.id, DoseStatus.TAKEN)

            assertEquals(DoseStatus.TAKEN, doses.getById(dose.id)?.status)
            assertEquals(now, doses.getById(dose.id)?.actedAt)
            assertTrue(alarms.cancelled.contains(dose.id))
            assertFalse(alarms.reminders.containsKey(dose.id))
            assertFalse(alarms.graces.containsKey(dose.id))
            verify { notifications.cancel(dose.id) }
        }

    @Test
    fun `taking a dose decrements the tracked remaining quantity`() =
        runTest {
            medications.put(medication(id = 1, remainingDoses = 5))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            engine.performAction(dose.id, DoseStatus.TAKEN)

            assertEquals(4, medications.items.single().remainingDoses)
        }

    @Test
    fun `doses that are not taken never change the remaining quantity`() =
        runTest {
            medications.put(medication(id = 1, remainingDoses = 5))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            engine.performAction(dose.id, DoseStatus.SKIPPED)

            assertEquals(5, medications.items.single().remainingDoses)
        }

    @Test
    fun `medications without tracked stock are left alone`() =
        runTest {
            medications.put(medication(id = 1, remainingDoses = null))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            engine.performAction(dose.id, DoseStatus.TAKEN)

            assertNull(medications.items.single().remainingDoses)
        }

    @Test
    fun `the remaining quantity never goes below zero`() =
        runTest {
            medications.put(medication(id = 1, remainingDoses = 0))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            engine.performAction(dose.id, DoseStatus.TAKEN)

            assertEquals(0, medications.items.single().remainingDoses)
        }

    @Test
    fun `an unacted dose is reminded again inside its grace window`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            clock.setNow(Instant.parse("2026-10-17T20:00:00Z"))
            alarms.clear()

            engine.onReminderFired(dose.id)

            assertEquals(Instant.parse("2026-10-17T20:05:00Z"), alarms.reminders[dose.id])
            assertEquals(Instant.parse("2026-10-17T21:00:00Z"), alarms.graces[dose.id])
        }

    @Test
    fun `a repeat is not scheduled when it would fall after the grace period`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            // 20:56 + 5 min = 21:01, past the 21:00 grace end.
            clock.setNow(Instant.parse("2026-10-17T20:56:00Z"))
            alarms.clear()

            engine.onReminderFired(dose.id)

            assertFalse(alarms.reminders.containsKey(dose.id))
            assertEquals(Instant.parse("2026-10-17T21:00:00Z"), alarms.graces[dose.id])
        }

    @Test
    fun `a reminder that fires entirely after the grace period does not repeat`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            clock.setNow(Instant.parse("2026-10-17T21:30:00Z"))
            alarms.clear()

            engine.onReminderFired(dose.id)

            assertFalse(alarms.reminders.containsKey(dose.id))
            assertFalse(alarms.graces.containsKey(dose.id))
        }

    @Test
    fun `acting on the dose cancels the pending repeat`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            clock.setNow(Instant.parse("2026-10-17T20:00:00Z"))
            engine.onReminderFired(dose.id)
            assertTrue(alarms.reminders.containsKey(dose.id))

            engine.performAction(dose.id, DoseStatus.TAKEN)

            assertFalse(alarms.reminders.containsKey(dose.id))
            assertFalse(alarms.graces.containsKey(dose.id))
        }

    @Test
    fun `snoozing reschedules a reminder ten minutes later and keeps the grace alarm`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()

            engine.performAction(dose.id, DoseStatus.SNOOZED)

            assertEquals(DoseStatus.SNOOZED, doses.getById(dose.id)?.status)
            assertEquals(now.plusSeconds(600), alarms.reminders[dose.id])
            assertEquals(dose.scheduledAt.plusSeconds(3600), alarms.graces[dose.id])
        }

    @Test
    fun `snoozing an overdue dose does not arm a grace alarm in the past`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.first()
            doses.replace(dose.copy(scheduledAt = now.minusSeconds(3 * 60 * 60)))
            alarms.clear()

            engine.performAction(dose.id, DoseStatus.SNOOZED)

            assertEquals(now.plusSeconds(600), alarms.reminders[dose.id])
            assertFalse(alarms.graces.containsKey(dose.id))
        }

    // --- Patient reminder preferences ---

    @Test
    fun `the grace period and snooze length come from the patient settings`() =
        runTest {
            settings.setGraceMinutes(30)
            settings.setSnoozeMinutes(20)
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()

            engine.refresh()

            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            // Grace 30 min instead of the default 60.
            assertEquals(Instant.parse("2026-10-17T20:30:00Z"), alarms.graces[dose.id])

            clock.setNow(Instant.parse("2026-10-17T20:00:00Z"))
            engine.performAction(dose.id, DoseStatus.SNOOZED)

            // Snooze 20 min instead of the default 10.
            assertEquals(Instant.parse("2026-10-17T20:20:00Z"), alarms.reminders[dose.id])
        }

    @Test
    fun `the repeat cadence comes from the patient settings`() =
        runTest {
            settings.setRepeatMinutes(15)
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val dose = doses.events.single { it.scheduledAt == Instant.parse("2026-10-17T20:00:00Z") }
            clock.setNow(Instant.parse("2026-10-17T20:00:00Z"))
            alarms.clear()

            engine.onReminderFired(dose.id)

            // 15 min instead of the default 5.
            assertEquals(Instant.parse("2026-10-17T20:15:00Z"), alarms.reminders[dose.id])
        }

    // --- Degradation and reboot ---

    @Test
    fun `without exact alarm access doses are generated and alarms are still scheduled`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            alarms.canSchedule = false

            engine().refresh()

            assertTrue(doses.events.isNotEmpty())
            // D-008 rev. 2: the scheduler degrades to an inexact wakeup, so
            // the engine must keep scheduling instead of skipping alarms.
            assertEquals(doses.events.size, alarms.reminders.size)
            assertEquals(doses.events.size, alarms.graces.size)
        }

    @Test
    fun `a refresh after reboot reschedules every future reminder`() =
        runTest {
            medications.put(medication(id = 1))
            schedules.put(schedule(medicationId = 1, times = listOf(LocalTime.of(20, 0))))
            val engine = engine()
            engine.refresh()
            val expected = doses.events.size

            alarms.reminders.clear()
            alarms.graces.clear()
            engine.refresh()

            assertEquals(expected, alarms.reminders.size)
            assertEquals(expected, alarms.graces.size)
        }

    @Test
    fun `refresh never throws when a repository fails`() =
        runTest {
            val broken = mockk<MedicationRepository>(relaxed = true)
            every { broken.observeAll() } throws IllegalStateException("database closed")
            val engine =
                ReminderEngineImpl(
                    doseEventRepository = doses,
                    medicationRepository = broken,
                    scheduleRepository = schedules,
                    alarmScheduler = alarms,
                    notificationFactory = notifications,
                    settingsRepository = settings,
                    clock = clock,
                )

            engine.refresh()
        }

    private fun medication(
        id: Long,
        name: String = "Medication $id",
        strength: String = "500 mg",
        remainingDoses: Int? = null,
    ) = Medication(
        id = id,
        name = name,
        form = MedicationForm.TABLET,
        strength = strength,
        instructions = "",
        source = MedicationSource.SELF,
        startDate = Instant.parse("2026-10-01T00:00:00Z"),
        endDate = null,
        status = MedicationStatus.ACTIVE,
        stopReason = null,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        remainingDoses = remainingDoses,
    )

    private fun schedule(
        medicationId: Long,
        times: List<LocalTime>,
        doseAmount: String = "1 tablet",
    ) = Schedule(
        id = 100L + medicationId,
        medicationId = medicationId,
        type = ScheduleType.DAILY_TIMES,
        times = times,
        intervalHours = null,
        weekdays = emptySet(),
        doseAmount = doseAmount,
        foodRelation = FoodRelation.ANY,
    )
}
