package com.medcompanion.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of time for the app. Every screen and (later) the reminder
 * engine reads the clock through this interface so tests can control time
 * (see AGENTS.md > Reminders: "All time logic goes through an injectable Clock").
 */
interface AppClock {
    fun now(): Instant

    fun today(): LocalDate

    fun zone(): ZoneId
}

@Singleton
class SystemAppClock
    @Inject
    constructor() : AppClock {
        override fun now(): Instant = Instant.now()

        override fun today(): LocalDate = LocalDate.now(zone())

        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
