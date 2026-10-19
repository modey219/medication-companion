package com.medcompanion.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.domain.settings.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The settings store (T8a, D-014): DataStore on the device, defaults when
 * nothing was set, and a [clear] that resets everything except the first-run
 * consent record.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class DataStoreSettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + Job())
        dataStore =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = DataStoreSettingsRepository(dataStore)
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
    fun `clearing resets the settings but keeps the consent record`() =
        runTest {
            repository.setLanguage(AppLanguage.ENGLISH)
            repository.setEmergencyNumber("997")
            repository.completeOnboarding()

            repository.clear()

            val settings = repository.observe().first()
            assertEquals(AppLanguage.SYSTEM, settings.language)
            assertEquals("", settings.emergencyNumber)
            assertTrue(settings.onboardingCompleted)
        }
}
