package com.medcompanion.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.core.time.FakeAppClock
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.ConsentRecord
import com.medcompanion.app.domain.settings.ReminderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The settings store (T8a, D-014, T13): DataStore on the device, defaults when
 * nothing was set, versioned consents, and a [clear] that resets everything
 * except the first-run consent record.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository
    private val clock = FakeAppClock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + Job())
        dataStore =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = DataStoreSettingsRepository(dataStore, clock)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun `a fresh install reads the defaults`() =
        runTest {
            val settings = repository.observe().first()

            assertEquals(AppLanguage.SYSTEM, settings.language)
            assertEquals("", settings.emergencyNumber)
            assertFalse(settings.onboardingCompleted)
            assertFalse(settings.assistantConsentCompleted)
            assertFalse(settings.assistantProfileConsent.isGranted())
            assertFalse(settings.assistantMediaConsent.isGranted())
        }

    @Test
    fun `the language choice is stored and read back`() =
        runTest {
            repository.setLanguage(AppLanguage.ENGLISH)

            assertEquals(AppLanguage.ENGLISH, repository.observe().first().language)
        }

    @Test
    fun `choosing the device language removes the stored choice`() =
        runTest {
            repository.setLanguage(AppLanguage.ARABIC)
            repository.setLanguage(AppLanguage.SYSTEM)

            assertEquals(AppLanguage.SYSTEM, repository.observe().first().language)
        }

    @Test
    fun `the emergency number is stored trimmed`() =
        runTest {
            repository.setEmergencyNumber("  997  ")

            assertEquals("997", repository.observe().first().emergencyNumber)
        }

    @Test
    fun `the first-run consent is remembered`() =
        runTest {
            repository.completeOnboarding()

            assertTrue(repository.observe().first().onboardingCompleted)
        }

    @Test
    fun `clearing resets everything except the first-run consent record`() =
        runTest {
            repository.setLanguage(AppLanguage.ENGLISH)
            repository.setEmergencyNumber("997")
            repository.setGraceMinutes(30)
            repository.setSnoozeMinutes(20)
            repository.setRepeatMinutes(15)
            repository.setLowStockThreshold(5)
            repository.setPharmacyNumber("01234567")
            repository.completeOnboarding()

            repository.clear()

            val settings = repository.observe().first()
            assertEquals(AppLanguage.SYSTEM, settings.language)
            assertEquals("", settings.emergencyNumber)
            assertEquals(ReminderPreferences.DEFAULT_GRACE_MINUTES, settings.graceMinutes)
            assertEquals(ReminderPreferences.DEFAULT_SNOOZE_MINUTES, settings.snoozeMinutes)
            assertEquals(ReminderPreferences.DEFAULT_REPEAT_MINUTES, settings.repeatMinutes)
            assertEquals(ReminderPreferences.DEFAULT_LOW_STOCK_THRESHOLD, settings.lowStockThreshold)
            assertEquals("", settings.pharmacyNumber)
            assertTrue(settings.onboardingCompleted)
        }

    @Test
    fun `the reminder and stock preferences round-trip through the store`() =
        runTest {
            repository.setGraceMinutes(30)
            repository.setSnoozeMinutes(20)
            repository.setRepeatMinutes(15)
            repository.setLowStockThreshold(5)

            val settings = repository.observe().first()
            assertEquals(30, settings.graceMinutes)
            assertEquals(20, settings.snoozeMinutes)
            assertEquals(15, settings.repeatMinutes)
            assertEquals(5, settings.lowStockThreshold)
        }

    @Test
    fun `out-of-range reminder values are clamped on write`() =
        runTest {
            repository.setGraceMinutes(1000)
            repository.setSnoozeMinutes(1)
            repository.setRepeatMinutes(99)
            repository.setLowStockThreshold(0)

            val settings = repository.observe().first()
            assertEquals(ReminderPreferences.GRACE_MINUTES_RANGE.last, settings.graceMinutes)
            assertEquals(ReminderPreferences.SNOOZE_MINUTES_RANGE.first, settings.snoozeMinutes)
            assertEquals(ReminderPreferences.REPEAT_MINUTES_RANGE.last, settings.repeatMinutes)
            assertEquals(ReminderPreferences.LOW_STOCK_THRESHOLD_RANGE.first, settings.lowStockThreshold)
        }

    @Test
    fun `the pharmacy number is stored trimmed`() =
        runTest {
            repository.setPharmacyNumber("  01234567  ")

            assertEquals("01234567", repository.observe().first().pharmacyNumber)
        }

    @Test
    fun `the assistant consent and the sharing choice round-trip`() =
        runTest {
            repository.completeAssistantConsent()
            repository.grantAssistantProfileConsent()

            val settings = repository.observe().first()
            assertTrue(settings.assistantConsentCompleted)
            assertTrue(settings.assistantProfileConsent.isGranted())
        }

    @Test
    fun `a granted consent carries the version the patient read and the time`() =
        runTest {
            repository.grantAssistantProfileConsent()

            val consent = repository.observe().first().assistantProfileConsent
            assertEquals(ConsentRecord.CURRENT_VERSION, consent.version)
            assertEquals(clock.now(), consent.acceptedAt)
        }

    @Test
    fun `an older consent version is no longer granted`() =
        runTest {
            repository.grantAssistantProfileConsent()

            val stored = repository.observe().first().assistantProfileConsent
            assertFalse(stored.isGranted(currentVersion = stored.version + 1))
        }

    @Test
    fun `a consent can be withdrawn`() =
        runTest {
            repository.grantAssistantProfileConsent()
            repository.grantAssistantMediaConsent()

            repository.revokeAssistantProfileConsent()
            repository.revokeAssistantMediaConsent()

            val settings = repository.observe().first()
            assertFalse(settings.assistantProfileConsent.isGranted())
            assertFalse(settings.assistantMediaConsent.isGranted())
            assertNull(settings.assistantMediaConsent.acceptedAt)
        }

    @Test
    fun `deleting all data also asks for the assistant consents again`() =
        runTest {
            repository.completeAssistantConsent()
            repository.grantAssistantProfileConsent()
            repository.grantAssistantMediaConsent()

            repository.clear()

            val settings = repository.observe().first()
            assertFalse(settings.assistantConsentCompleted)
            assertFalse(settings.assistantProfileConsent.isGranted())
            assertFalse(settings.assistantMediaConsent.isGranted())
        }
}
