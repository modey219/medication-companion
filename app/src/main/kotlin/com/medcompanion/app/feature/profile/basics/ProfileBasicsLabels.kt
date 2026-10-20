package com.medcompanion.app.feature.profile.basics

import androidx.annotation.StringRes
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.Sex
import com.medcompanion.app.ui.components.Choice

/**
 * The sex options, with "not set" first so nothing is forced.
 * TODO(clinical-review): the wording of the four options.
 */
internal val sexChoices: List<Choice<Sex>> =
    listOf(
        Choice(value = null, labelRes = R.string.profile_basics_not_set),
        Choice(value = Sex.FEMALE, labelRes = R.string.profile_sex_female),
        Choice(value = Sex.MALE, labelRes = R.string.profile_sex_male),
        Choice(value = Sex.OTHER, labelRes = R.string.profile_sex_other),
    )

/**
 * The pregnancy options; "not set" is a real answer and stays an option.
 * TODO(clinical-review): the wording of the three options.
 */
internal val pregnancyChoices: List<Choice<Boolean>> =
    listOf(
        Choice(value = null, labelRes = R.string.profile_basics_pregnancy_unset),
        Choice(value = true, labelRes = R.string.profile_basics_pregnancy_yes),
        Choice(value = false, labelRes = R.string.profile_basics_pregnancy_no),
    )

/** One-line feedback shown in the snackbar. */
@StringRes
internal fun ProfileBasicsMessage.messageRes(): Int =
    when (this) {
        ProfileBasicsMessage.SAVED -> R.string.profile_basics_saved
        ProfileBasicsMessage.SAVE_FAILED -> R.string.error_save_failed
    }
