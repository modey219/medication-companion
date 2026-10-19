package com.medcompanion.app.domain.settings

/**
 * The reminder and stock preferences the patient can change in the Profile tab
 * and on the reminder setup screen (T8b, AGENTS.md > Reminders: "configurable
 * grace period, snooze"; AGENTS.md > Profile: reminder preferences).
 *
 * The ranges are deliberately narrow and stepper-friendly: the patient picks
 * from reviewed values instead of typing a number, so a dose can never be
 * silently marked MISSED after an accidental keystroke. Every value here is a
 * TODO(clinical-review) item until the team signs the ranges off.
 */
object ReminderPreferences {
    const val DEFAULT_GRACE_MINUTES: Long = 60
    const val DEFAULT_SNOOZE_MINUTES: Long = 10
    const val DEFAULT_REPEAT_MINUTES: Long = 5
    const val DEFAULT_LOW_STOCK_THRESHOLD: Int = 3

    const val GRACE_STEP_MINUTES: Long = 15
    val GRACE_MINUTES_RANGE: LongRange = 15L..180L

    const val SNOOZE_STEP_MINUTES: Long = 5
    val SNOOZE_MINUTES_RANGE: LongRange = 5L..60L

    const val REPEAT_STEP_MINUTES: Long = 5
    val REPEAT_MINUTES_RANGE: LongRange = 5L..30L

    const val LOW_STOCK_STEP: Int = 1
    val LOW_STOCK_THRESHOLD_RANGE: IntRange = 1..10

    /** Clamps are applied on every write, so a stored value is always usable. */
    fun clampGraceMinutes(minutes: Long): Long = minutes.coerceIn(GRACE_MINUTES_RANGE)

    fun clampSnoozeMinutes(minutes: Long): Long = minutes.coerceIn(SNOOZE_MINUTES_RANGE)

    fun clampRepeatMinutes(minutes: Long): Long = minutes.coerceIn(REPEAT_MINUTES_RANGE)

    fun clampLowStockThreshold(doses: Int): Int = doses.coerceIn(LOW_STOCK_THRESHOLD_RANGE)
}
