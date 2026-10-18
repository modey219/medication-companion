package com.medcompanion.app.data.reminders

import android.app.AlarmManager
import android.content.Intent
import com.medcompanion.app.core.reminder.ReminderActions
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.reminder.DoseReminderPayload
import com.medcompanion.app.domain.reminder.ReminderEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real intents through the routing point: alarm fires, notification actions
 * and system changes (AGENTS.md > Reminders: actions must work without
 * opening the app; reboot, timezone, clock and app updates reschedule).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class ReminderBroadcastHandlerTest {
    private val engine: ReminderEngine = mockk(relaxed = true)
    private val notifications: DoseNotificationFactory = mockk(relaxed = true)
    private val handler = ReminderBroadcastHandler(engine, notifications)

    private val payload =
        DoseReminderPayload(
            doseId = 7L,
            medicationName = "Metformin",
            strength = "500 mg",
            doseAmount = "1 tablet",
            foodRelation = FoodRelation.ANY,
        )

    private fun intent(
        action: String,
        doseId: Long? = null,
    ) = Intent(action).apply {
        if (doseId != null) putExtra(ReminderActions.EXTRA_DOSE_ID, doseId)
    }

    // --- Alarms ---

    @Test
    fun `a reminder alarm shows the reminder notification`() =
        runTest {
            coEvery { engine.onReminderFired(7L) } returns payload

            handler.handle(intent(ReminderActions.ACTION_REMINDER, 7L))

            verify { notifications.showReminder(payload) }
        }

    @Test
    fun `a reminder alarm for a handled dose shows nothing`() =
        runTest {
            coEvery { engine.onReminderFired(7L) } returns null

            handler.handle(intent(ReminderActions.ACTION_REMINDER, 7L))

            verify(exactly = 0) { notifications.showReminder(any()) }
        }

    @Test
    fun `the grace alarm shows the gentle follow-up`() =
        runTest {
            coEvery { engine.onGraceFired(7L) } returns payload

            handler.handle(intent(ReminderActions.ACTION_GRACE, 7L))

            verify { notifications.showFollowUp(payload) }
        }

    @Test
    fun `the grace alarm without a payload shows nothing`() =
        runTest {
            coEvery { engine.onGraceFired(7L) } returns null

            handler.handle(intent(ReminderActions.ACTION_GRACE, 7L))

            verify(exactly = 0) { notifications.showFollowUp(any()) }
        }

    // --- Notification actions ---

    @Test
    fun `notification actions record the matching status`() =
        runTest {
            handler.handle(intent(ReminderActions.ACTION_TAKEN, 7L))
            handler.handle(intent(ReminderActions.ACTION_SNOOZE, 8L))
            handler.handle(intent(ReminderActions.ACTION_SKIP, 9L))

            coVerify { engine.performAction(7L, DoseStatus.TAKEN) }
            coVerify { engine.performAction(8L, DoseStatus.SNOOZED) }
            coVerify { engine.performAction(9L, DoseStatus.SKIPPED) }
        }

    // --- System changes ---

    @Test
    fun `boot completed refreshes every alarm`() =
        runTest {
            handler.handle(Intent(Intent.ACTION_BOOT_COMPLETED))

            coVerify { engine.refresh() }
        }

    @Test
    fun `timezone and clock changes refresh`() =
        runTest {
            handler.handle(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            handler.handle(Intent(Intent.ACTION_TIME_CHANGED))

            coVerify(exactly = 2) { engine.refresh() }
        }

    @Test
    fun `an app update refreshes`() =
        runTest {
            handler.handle(Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

            coVerify { engine.refresh() }
        }

    @Test
    fun `regaining exact alarm permission refreshes`() =
        runTest {
            handler.handle(Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))

            coVerify { engine.refresh() }
        }

    // --- Robustness ---

    @Test
    fun `unknown actions and missing extra are ignored`() =
        runTest {
            handler.handle(intent("com.example.UNKNOWN"))
            handler.handle(intent(ReminderActions.ACTION_TAKEN))
            handler.handle(null)

            coVerify(exactly = 0) { engine.refresh() }
            coVerify(exactly = 0) { engine.performAction(any(), any()) }
            verify(exactly = 0) { notifications.showReminder(any()) }
        }

    @Test
    fun `an engine failure never escapes the handler`() =
        runTest {
            coEvery { engine.onReminderFired(7L) } throws IllegalStateException("database closed")

            handler.handle(intent(ReminderActions.ACTION_REMINDER, 7L))

            assertTrue(true) // reaching here means nothing was thrown
        }
}
