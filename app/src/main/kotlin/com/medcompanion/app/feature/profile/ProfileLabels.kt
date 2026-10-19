package com.medcompanion.app.feature.profile

import androidx.annotation.StringRes
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.settings.AppLanguage

/** Section title of an entry list. */
@StringRes
fun ProfileEntryType.sectionTitleRes(): Int =
    when (this) {
        ProfileEntryType.ALLERGY -> R.string.profile_allergy_title
        ProfileEntryType.CONDITION -> R.string.profile_condition_title
    }

/** "Add …" action label of an entry list. */
@StringRes
fun ProfileEntryType.addLabelRes(): Int =
    when (this) {
        ProfileEntryType.ALLERGY -> R.string.profile_add_allergy
        ProfileEntryType.CONDITION -> R.string.profile_add_condition
    }

/** Label of the text field that asks for the entry itself. */
@StringRes
fun ProfileEntryType.fieldLabelRes(): Int =
    when (this) {
        ProfileEntryType.ALLERGY -> R.string.profile_entry_label_allergy
        ProfileEntryType.CONDITION -> R.string.profile_entry_label_condition
    }

/** Name of a language choice, always written in that language. */
@StringRes
fun AppLanguage.labelRes(): Int =
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ARABIC -> R.string.language_arabic
        AppLanguage.ENGLISH -> R.string.language_english
    }

/** One-line feedback shown in the snackbar. */
@StringRes
fun ProfileMessage.messageRes(): Int =
    when (this) {
        ProfileMessage.EMERGENCY_NUMBER_SAVED -> R.string.profile_emergency_saved
        ProfileMessage.STOCK_THRESHOLD_SAVED -> R.string.profile_stock_saved
        ProfileMessage.PHARMACY_NUMBER_SAVED -> R.string.profile_pharmacy_saved
        ProfileMessage.DATA_DELETED -> R.string.profile_delete_confirmed
        ProfileMessage.EXPORT_FAILED -> R.string.profile_error_export
        ProfileMessage.DELETE_FAILED -> R.string.profile_error_delete
        ProfileMessage.SAVE_FAILED -> R.string.error_save_failed
        ProfileMessage.DELETE_ENTRY_FAILED -> R.string.profile_error_delete
    }
