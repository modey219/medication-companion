package com.medcompanion.app.core.di

import com.medcompanion.app.core.time.AppClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Qualifier

/**
 * Marks the periodic "now" stream used by screens that recompute
 * time-derived state while they are visible (Today tab overdue flags).
 * Injected instead of built inside the ViewModel so unit tests can drive
 * ticks deterministically: an autonomous delay loop on the test scheduler
 * keeps runTest draining until the heap is exhausted (see D-007).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PeriodicTicker

@Module
@InstallIn(SingletonComponent::class)
object TimeTickerModule {
    /** Emits [AppClock.now] immediately, then every 30 seconds. */
    @Provides
    @PeriodicTicker
    fun providePeriodicTicker(clock: AppClock): Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(TICK_INTERVAL_MILLIS)
            }
        }

    private const val TICK_INTERVAL_MILLIS = 30_000L
}
