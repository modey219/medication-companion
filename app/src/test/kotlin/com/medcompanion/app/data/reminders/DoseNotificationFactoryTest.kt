package com.medcompanion.app.data.reminders

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.reminder.DoseReminderPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Lock-screen privacy (Phase 1): by default the drug name is on the lock
 * screen; with the toggle on the lock screen only gets a generic notice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DoseNotificationFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val lockSettings = ReminderLockSettings(context)
    private val factory = DoseNotificationFactory(context, lockSettings)

    private val payload =
        DoseReminderPayload(
            doseId = 5L,
            medicationName = "Metformin",
            strength = "500 mg",
            doseAmount = "1 tablet",
            foodRelation = FoodRelation.AFTER,
        )

    private fun posted(): Notification {
        val shadow = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
        return shadow.allNotifications.single()
    }

    @Test
    fun `by default the drug name is visible on the lock screen`() {
        factory.showReminder(payload)

        val notification = posted()

        assertNull(notification.publicVersion)
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertEquals("Metformin 500 mg", title)
    }

    @Test
    fun `hiding posts a generic public version without the drug name`() {
        lockSettings.setHideNameOnLockScreen(true)

        factory.showReminder(payload)

        val notification = posted()
        val publicVersion = requireNotNull(notification.publicVersion)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)

        val publicTitle = publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertNotNull(publicTitle)
        assertFalse(publicTitle!!.contains("Metformin"))

        // The full shade keeps the details for the user.
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertTrue(title!!.contains("Metformin"))
    }

    @Test
    fun `the toggle applies to the next reminder without restarting`() {
        factory.showReminder(payload)
        assertNull(posted().publicVersion)

        lockSettings.setHideNameOnLockScreen(true)
        factory.showFollowUp(payload)

        assertNotNull(posted().publicVersion)
    }
}
