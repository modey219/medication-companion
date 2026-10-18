package com.medcompanion.app.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.MainActivity
import com.medcompanion.app.core.reminder.ReminderActions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant

/**
 * Exact-alarm plumbing (D-008: setAlarmClock, one reminder plus one grace
 * alarm per dose, cancel removes exactly the two of that dose).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class AlarmManagerDoseAlarmSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = AlarmManagerDoseAlarmScheduler(context)

    private val trigger = Instant.parse("2026-10-17T08:00:00Z")
    private val grace = Instant.parse("2026-10-17T09:00:00Z")

    private fun shadow() = Shadows.shadowOf(alarmManager)

    private fun nextAlarm(): ShadowAlarmManager.ScheduledAlarm = requireNotNull(shadow().nextScheduledAlarm)

    private fun savedIntent(alarm: ShadowAlarmManager.ScheduledAlarm): Intent =
        Shadows.shadowOf(requireNotNull(alarm.operation)).savedIntent

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `a reminder alarm is an exact alarm-clock wakeup with dose id and action`() {
        scheduler.scheduleReminder(7L, trigger)

        val alarm = nextAlarm()

        assertEquals(trigger.toEpochMilli(), alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertNotNull(alarm.alarmClockInfo)
        assertTrue(Shadows.shadowOf(requireNotNull(alarm.operation)).isBroadcast)
        assertEquals(ReminderActions.ACTION_REMINDER, savedIntent(alarm).action)
        assertEquals(7L, savedIntent(alarm).getLongExtra(ReminderActions.EXTRA_DOSE_ID, -1L))
    }

    @Test
    fun `the alarm clock info carries a show intent that opens the app`() {
        scheduler.scheduleReminder(7L, trigger)

        val info = requireNotNull(nextAlarm().alarmClockInfo)
        val showIntent = requireNotNull(info.showIntent)
        val show = Shadows.shadowOf(showIntent as PendingIntent).savedIntent

        assertEquals(MainActivity::class.java.name, show.component?.className)
    }

    @Test
    fun `a grace alarm uses the grace action`() {
        scheduler.scheduleGrace(7L, grace)

        val alarm = nextAlarm()

        assertEquals(grace.toEpochMilli(), alarm.triggerAtTime)
        assertEquals(ReminderActions.ACTION_GRACE, savedIntent(alarm).action)
        assertEquals(7L, savedIntent(alarm).getLongExtra(ReminderActions.EXTRA_DOSE_ID, -1L))
    }

    @Test
    fun `rescheduling a dose replaces its own alarm instead of adding one`() {
        scheduler.scheduleReminder(7L, trigger)
        scheduler.scheduleReminder(7L, grace)

        assertEquals(1, shadow().scheduledAlarms.size)
        assertEquals(grace.toEpochMilli(), nextAlarm().triggerAtTime)
    }

    @Test
    fun `cancel removes both alarms of that dose and leaves others alone`() {
        scheduler.scheduleReminder(7L, trigger)
        scheduler.scheduleGrace(7L, grace)
        scheduler.scheduleReminder(8L, trigger)

        scheduler.cancel(7L)

        val remaining = shadow().scheduledAlarms
        assertEquals(1, remaining.size)
        assertEquals(8L, savedIntent(remaining.single()).getLongExtra(ReminderActions.EXTRA_DOSE_ID, -1L))
    }

    @Test
    fun `cancel is safe when nothing was scheduled`() {
        scheduler.cancel(7L)

        assertNull(shadow().nextScheduledAlarm)
    }

    @Test
    fun `without exact alarm access an inexact wakeup alarm is scheduled instead`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.scheduleReminder(7L, trigger)
        val reminder = nextAlarm()
        scheduler.scheduleGrace(7L, grace)
        val graceAlarm = nextAlarm()

        assertFalse(scheduler.canScheduleExact())
        assertEquals(trigger.toEpochMilli(), reminder.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, reminder.type)
        assertNull(reminder.alarmClockInfo)
        assertEquals(ReminderActions.ACTION_REMINDER, savedIntent(reminder).action)
        assertEquals(grace.toEpochMilli(), graceAlarm.triggerAtTime)
        assertEquals(ReminderActions.ACTION_GRACE, savedIntent(graceAlarm).action)
    }

    @Test
    fun `cancel works for inexact fallback alarms too`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.scheduleReminder(7L, trigger)
        scheduler.scheduleGrace(7L, grace)

        scheduler.cancel(7L)

        assertNull(shadow().nextScheduledAlarm)
    }
}
