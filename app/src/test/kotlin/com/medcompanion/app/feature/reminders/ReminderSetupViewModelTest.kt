package com.medcompanion.app.feature.reminders

import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSetupViewModelTest {
    private val permissionChecker: ReminderPermissionChecker = mockk()
    private val reminderEngine: ReminderEngine = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

    private fun viewModel() = ReminderSetupViewModel(permissionChecker, reminderEngine)

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
}
