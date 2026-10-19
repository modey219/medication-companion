package com.medcompanion.app.feature.history

import androidx.annotation.StringRes
import com.medcompanion.app.R

/** Maps History state to localized labels; nothing here judges or advises. */
@StringRes
fun HistoryPeriod.labelRes(): Int =
    when (this) {
        HistoryPeriod.LAST_7_DAYS -> R.string.history_period_7_days
        HistoryPeriod.LAST_30_DAYS -> R.string.history_period_30_days
    }

@StringRes
fun HistoryError.messageRes(): Int =
    when (this) {
        HistoryError.EXPORT_FAILED -> R.string.history_error_export
    }

@StringRes
fun HistoryEntry.kindLabelRes(): Int =
    when (this) {
        is HistoryEntry.DoseLogged -> R.string.history_entry_dose
        is HistoryEntry.SymptomLogged -> R.string.history_entry_symptom
        is HistoryEntry.MedicationStarted -> R.string.history_entry_medication_started
    }
