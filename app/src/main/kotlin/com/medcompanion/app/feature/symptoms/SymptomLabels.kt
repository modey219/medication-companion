package com.medcompanion.app.feature.symptoms

import androidx.annotation.StringRes
import com.medcompanion.app.R

/**
 * Words for the 1 to 5 severity scale. The labels describe intensity only and
 * never say what a symptom means.
 *
 * TODO(clinical-review): confirm the wording of the five levels.
 */
fun severityLabelRes(severity: Int): Int {
    require(severity in SEVERITY_RANGE) { "severity outside $SEVERITY_RANGE: $severity" }
    return when (severity) {
        1 -> R.string.symptom_severity_1
        2 -> R.string.symptom_severity_2
        3 -> R.string.symptom_severity_3
        4 -> R.string.symptom_severity_4
        else -> R.string.symptom_severity_5
    }
}

@StringRes
fun SymptomsError.messageRes(): Int =
    when (this) {
        SymptomsError.SAVE_FAILED -> R.string.symptoms_error_save
        SymptomsError.DELETE_FAILED -> R.string.symptoms_error_delete
    }
