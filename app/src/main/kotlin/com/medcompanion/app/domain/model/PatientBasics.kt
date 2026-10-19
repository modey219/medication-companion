package com.medcompanion.app.domain.model

/**
 * The patient's basic facts. One row only, and only what the assistant context
 * needs: the birth *year* is stored, never a full date of birth.
 *
 * TODO(clinical-review): the accepted ranges for birth year and weight.
 */
data class PatientBasics(
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val pregnantOrBreastfeeding: Boolean? = null,
    val weightKg: Int? = null,
) {
    val isEmpty: Boolean
        get() = birthYear == null && sex == null && pregnantOrBreastfeeding == null && weightKg == null

    init {
        require(birthYear == null || birthYear in ProfileYears.RANGE) {
            "birthYear must be between ${ProfileYears.MIN} and ${ProfileYears.MAX}"
        }
        require(weightKg == null || weightKg in WEIGHT_KG_RANGE) {
            "weightKg must be between ${WEIGHT_KG_RANGE.first} and ${WEIGHT_KG_RANGE.last}"
        }
    }

    companion object {
        const val WEIGHT_KG_MIN = 20
        const val WEIGHT_KG_MAX = 400

        val WEIGHT_KG_RANGE = WEIGHT_KG_MIN..WEIGHT_KG_MAX
    }
}
