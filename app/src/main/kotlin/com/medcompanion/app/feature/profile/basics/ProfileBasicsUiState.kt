package com.medcompanion.app.feature.profile.basics

import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileYears
import com.medcompanion.app.domain.model.Sex

/**
 * State of the "basic information" screen (T12). The fields are held as text so
 * the patient's typing is never thrown away, and the screen stays stateless.
 */
data class ProfileBasicsUiState(
    val isLoading: Boolean = true,
    val birthYear: String = "",
    val sex: Sex? = null,
    val pregnantOrBreastfeeding: Boolean? = null,
    val weightKg: String = "",
    val isDirty: Boolean = false,
    val currentYear: Int = ProfileYears.MAX,
    val message: ProfileBasicsMessage? = null,
) {
    val birthYearValue: Int?
        get() = birthYear.trim().ifBlank { null }?.toIntOrNull()

    val isBirthYearValid: Boolean
        get() =
            birthYear.isBlank() ||
                (birthYearValue?.let { it in ProfileYears.RANGE && it <= currentYear } == true)

    val weightKgValue: Int?
        get() = weightKg.trim().ifBlank { null }?.toIntOrNull()

    val isWeightValid: Boolean
        get() =
            weightKg.isBlank() ||
                (weightKgValue?.let { it in PatientBasics.WEIGHT_KG_RANGE } == true)

    /** The patient's age in years, when it can be worked out. */
    val ageYears: Int?
        get() =
            birthYearValue
                ?.takeIf { isBirthYearValid }
                ?.let { currentYear - it }

    val canSave: Boolean
        get() = isBirthYearValid && isWeightValid

    /** The basics as they would be stored, or null when nothing was entered. */
    fun toBasics(): PatientBasics? {
        val value =
            PatientBasics(
                birthYear = birthYearValue.takeIf { isBirthYearValid },
                sex = sex,
                pregnantOrBreastfeeding = pregnantOrBreastfeeding,
                weightKg = weightKgValue.takeIf { isWeightValid },
            )
        return if (value.isEmpty) null else value
    }
}

/** One-line feedback shown in the snackbar. */
enum class ProfileBasicsMessage {
    SAVED,
    SAVE_FAILED,
}
