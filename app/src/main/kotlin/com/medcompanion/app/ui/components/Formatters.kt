package com.medcompanion.app.ui.components

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

/**
 * Locale- and preference-aware formatting. The platform formatters follow the
 * device 12/24-hour setting and digit style (AGENTS.md > Localization and UX).
 */

fun LocalTime.formatTime(context: Context): String {
    val format =
        android.text.format.DateFormat
            .getTimeFormat(context)
    val calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    return format.format(calendar.time)
}

fun LocalDate.formatDate(context: Context): String {
    val format =
        android.text.format.DateFormat
            .getDateFormat(context)
    val date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
    return format.format(date)
}

fun Instant.formatDateTime(context: Context): String {
    val date = Date.from(this)
    val dateFormat =
        android.text.format.DateFormat
            .getDateFormat(context)
    val timeFormat =
        android.text.format.DateFormat
            .getTimeFormat(context)
    return "${dateFormat.format(date)} ${timeFormat.format(date)}"
}
