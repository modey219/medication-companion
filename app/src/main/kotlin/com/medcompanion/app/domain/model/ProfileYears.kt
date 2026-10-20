package com.medcompanion.app.domain.model

/**
 * The years the health profile accepts for a birth year, a medical history year,
 * or the year an allergy or a chronic condition started.
 *
 * TODO(clinical-review): confirm these bounds.
 */
object ProfileYears {
    const val MIN = 1900
    const val MAX = 2100

    val RANGE = MIN..MAX
}
