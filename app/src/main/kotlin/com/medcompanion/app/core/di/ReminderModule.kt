package com.medcompanion.app.core.di

import com.medcompanion.app.data.reminders.AlarmManagerDoseAlarmScheduler
import com.medcompanion.app.data.reminders.ReminderEngineImpl
import com.medcompanion.app.data.reminders.ReminderPermissionCheckerImpl
import com.medcompanion.app.domain.reminder.DoseAlarmScheduler
import com.medcompanion.app.domain.reminder.ReminderEngine
import com.medcompanion.app.domain.reminder.ReminderPermissionChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    @Singleton
    abstract fun bindReminderEngine(impl: ReminderEngineImpl): ReminderEngine

    @Binds
    @Singleton
    abstract fun bindDoseAlarmScheduler(impl: AlarmManagerDoseAlarmScheduler): DoseAlarmScheduler

    @Binds
    @Singleton
    abstract fun bindReminderPermissionChecker(impl: ReminderPermissionCheckerImpl): ReminderPermissionChecker
}
