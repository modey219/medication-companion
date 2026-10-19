package com.medcompanion.app.feature.reminders

import com.medcompanion.app.data.reminders.ReminderLockSettings
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import com.medcompanion.app.domain.settings.FakeSettingsRepository
import com.medcompanion.app.domain.settings.ReminderPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSetupViewModelTest {
    private val permissionChecker: ReminderPermissionChecker = mockk()
    private val reminderEngine: ReminderEngine = mockk(relaxed = true)
    private val lockSettings: ReminderLockSettings = mockk(relaxed = true)
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { lockSettings.hideNameOnLockScreen() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubPermissions(
        notifications: Boolean,
        exact: Boolean,
        battery: Boolean,
    ) {
        every { permissionChecker.notificationsEnabled() } returns notifications
        every { permissionChecker.exactAlarmsEnabled() } returns exact
        every { permissionChecker.batteryOptimizationExempt() } returns battery
    }

    private fun viewModel() =
        ReminderSetupViewModel(
            permissionChecker = permissionChecker,
            reminderEngine = reminderEngine,
            lockSettings = lockSettings,
            settingsRepository = settings,
        )

    @Test
    fun `state mirrors the three system permissions`() {
        stubPermissions(notifications = true, exact = false, battery = true)

        val state = viewModel().uiState.value

        assertTrue(state.notificationsGranted)
        assertFalse(state.exactAlarmsGranted)
        assertTrue(state.batteryExempt)
        assertFalse(state.isComplete)
    }

    @Test
    fun `becoming complete schedules the missing alarms`() =
        runTest {
            stubPermissions(notifications = false, exact = true, battery = true)
            val vm = viewModel()
            coVerify(exactly = 0) { reminderEngine.refresh() }

            stubPermissions(notifications = true, exact = true, battery = true)
            vm.refreshStatus()

            assertTrue(vm.uiState.value.isComplete)
            coVerify(exactly = 1) { reminderEngine.refresh() }
        }

    @Test
    fun `refreshing without a change does not touch the engine`() =
        runTest {
            stubPermissions(notifications = true, exact = true, battery = false)
            val vm = viewModel()

            vm.refreshStatus()
            vm.refreshStatus()

            assertFalse(vm.uiState.value.isComplete)
            coVerify(exactly = 0) { reminderEngine.refresh() }
        }

    @Test
    fun `losing a permission after setup stops further refreshes`() =
        runTest {
            stubPermissions(notifications = true, exact = true, battery = false)
            val vm = viewModel()

            stubPermissions(notifications = true, exact = true, battery = true)
            vm.refreshStatus()

            stubPermissions(notifications = true, exact = false, battery = true)
            vm.refreshStatus()

            assertFalse(vm.uiState.value.isComplete)
            coVerify(exactly = 1) { reminderEngine.refresh() }
        }

    @Test
    fun `lock-screen hiding starts off and toggling persists the setting`() {
        stubPermissions(notifications = true, exact = true, battery = true)
        val vm = viewModel()
        assertFalse(vm.uiState.value.hideNameOnLockScreen)

        vm.onHideNameToggle(true)

        assertTrue(vm.uiState.value.hideNameOnLockScreen)
        verify { lockSettings.setHideNameOnLockScreen(true) }
    }

    @Test
    fun `refresh re-reads the stored lock-screen setting`() {
        every { lockSettings.hideNameOnLockScreen() } returns true
        stubPermissions(notifications = true, exact = true, battery = true)
        val vm = viewModel()

        assertTrue(vm.uiState.value.hideNameOnLockScreen)
    }

    @Test
    fun `the stepper moves each reminder preference by its own step`() =
        runTest {
            stubPermissions(notifications = true, exact = true, battery = true)
            val vm = viewModel()

            vm.onGraceMinutesChange(+1)
            vm.onSnoozeMinutesChange(+1)
            vm.onRepeatMinutesChange(+1)

            // defaults 60 / 10 / 5 with steps 15 / 5 / 5.
            assertEquals(75, vm.uiState.value.graceMinutes)
            assertEquals(15, vm.uiState.value.snoozeMinutes)
            assertEquals(10, vm.uiState.value.repeatMinutes)
        }

    @Test
    fun `a reminder preference change refreshes the alarms`() =
        runTest {
            stubPermissions(notifications = true, exact = true, battery = true)
            val vm = viewModel()

            vm.onSnoozeMinutesChange(+1)

            assertEquals(15, settings.current.snoozeMinutes)
            coVerify { reminderEngine.refresh() }
        }

    @Test
    fun `the reminder preferences stay inside their allowed range`() =
        runTest {
            stubPermissions(notifications = true, exact = true, battery = true)
            val vm = viewModel()

            repeat(20) { vm.onGraceMinutesChange(-1) }
            repeat(20) { vm.onSnoozeMinutesChange(+1) }
            vm.onRepeatMinutesChange(-1)

            assertEquals(ReminderPreferences.GRACE_MINUTES_RANGE.first, vm.uiState.value.graceMinutes)
            assertEquals(ReminderPreferences.SNOOZE_MINUTES_RANGE.last, vm.uiState.value.snoozeMinutes)
            assertEquals(ReminderPreferences.REPEAT_MINUTES_RANGE.first, vm.uiState.value.repeatMinutes)
        }
}
