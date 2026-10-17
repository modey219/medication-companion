package com.medcompanion.app.core.di

import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.core.time.SystemAppClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [AppClock] to the real system clock. Tests substitute a fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindAppClock(impl: SystemAppClock): AppClock
}
