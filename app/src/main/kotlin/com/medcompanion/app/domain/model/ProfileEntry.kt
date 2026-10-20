package com.medcompanion.app.domain.model

/**
 * A fact the patient entered about themselves: an allergy or a chronic condition.
 *
 * [severity] is only entered when the patient wants to: 1 mild, 2 moderate,
 * 3 severe. [reaction] is the plain-language description of what happened, and
 * [sinceYear] is when it started, if the patient knows.
 *
 * TODO(clinical-review): the 1 to 3 severity scale and its labels.
 */
data class ProfileEntry(
    val id: Long = 0,
    val type: ProfileEntryType,
    val label: String,
    val severity: Int? = null,
    val reaction: String? = null,
    val sinceYear: Int? = null,
    val note: String? = null,
) {
    init {
        require(severity == null || severity in SEVERITY_RANGE) {
            "severity must be between ${SEVERITY_RANGE.first} and ${SEVERITY_RANGE.last}"
        }
        require(sinceYear == null || sinceYear in ProfileYears.RANGE) {
            "sinceYear must be between ${ProfileYears.MIN} and ${ProfileYears.MAX}"
        }
    }

    companion object {
        const val MIN_SEVERITY = 1
        const val MAX_SEVERITY = 3

        val SEVERITY_RANGE = MIN_SEVERITY..MAX_SEVERITY
    }
}
