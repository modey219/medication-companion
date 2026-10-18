package com.medcompanion.app.core.time

/**
 * Snooze duration used by dose actions and by the reminder engine
 * (AGENTS.md > Reminders: "Snooze (default 10 minutes, configurable)").
 * Making it configurable needs DataStore (Profile settings); the value then
 * lives in settings and this constant is only the fallback default.
 */
object SnoozeDefaults {
    const val DEFAULT_SNOOZE_MINUTES: Long = 10
}
