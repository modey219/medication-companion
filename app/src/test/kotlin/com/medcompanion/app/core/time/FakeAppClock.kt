package com.medcompanion.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Deterministic clock for tests. The default instant is a fixed reference
 * so assertions on stored dates are stable (AGENTS.md > Reminders: all time
 * logic goes through an injectable Clock).
 */
class FakeAppClock(
    private var instant: Instant = Instant.parse("2026-10-17T12:00:00Z"),
    private var zoneId: ZoneId = ZoneOffset.UTC,
) : AppClock {
    override fun now(): Instant = instant

    override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()

    override fun zone(): ZoneId = zoneId

    fun setNow(newInstant: Instant) {
        instant = newInstant
    }

    /** Simulates a timezone change (AGENTS.md > Reminders test matrix). */
    fun setZone(newZone: ZoneId) {
        zoneId = newZone
    }
}
