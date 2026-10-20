package com.medcompanion.app.domain.model

/**
 * One fact from the patient's medical history: an illness, a surgery, a
 * hospitalization, or a family history item.
 *
 * TODO(clinical-review): the accepted year range.
 */
data class MedicalHistoryEntry(
    val id: Long = 0,
    val type: MedicalHistoryType,
    val label: String,
    val year: Int? = null,
    val note: String? = null,
) {
    init {
        require(year == null || year in ProfileYears.RANGE) {
            "year must be between ${ProfileYears.MIN} and ${ProfileYears.MAX}"
        }
    }
}
