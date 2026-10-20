package com.medcompanion.app.feature.profile

import androidx.annotation.StringRes
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.Sex
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

/** The profile section an entry list belongs to. */
fun ProfileEntryType.section(): ProfileSection =
    when (this) {
        ProfileEntryType.ALLERGY -> ProfileSection.ALLERGIES
        ProfileEntryType.CONDITION -> ProfileSection.CONDITIONS
    }

/** Name of a language choice, always written in that language. */
@StringRes
fun AppLanguage.labelRes(): Int =
    when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ARABIC -> R.string.language_arabic
        AppLanguage.ENGLISH -> R.string.language_english
    }

/** How the patient's sex is written. TODO(clinical-review): the wording. */
@StringRes
fun Sex.labelRes(): Int =
    when (this) {
        Sex.FEMALE -> R.string.profile_sex_female
        Sex.MALE -> R.string.profile_sex_male
        Sex.OTHER -> R.string.profile_sex_other
    }

/** Name of a medical history kind. TODO(clinical-review): the wording. */
@StringRes
fun MedicalHistoryType.labelRes(): Int =
    when (this) {
        MedicalHistoryType.PAST_ILLNESS -> R.string.profile_history_type_past_illness
        MedicalHistoryType.SURGERY -> R.string.profile_history_type_surgery
        MedicalHistoryType.HOSPITALIZATION -> R.string.profile_history_type_hospitalization
        MedicalHistoryType.FAMILY_HISTORY -> R.string.profile_history_type_family
        MedicalHistoryType.OTHER -> R.string.profile_history_type_other
    }

/** Title of one profile section. */
@StringRes
fun ProfileSection.titleRes(): Int =
    when (this) {
        ProfileSection.ALLERGIES -> R.string.profile_allergy_title
        ProfileSection.CONDITIONS -> R.string.profile_condition_title
        ProfileSection.HISTORY -> R.string.profile_history_title
        ProfileSection.MEDICATIONS -> R.string.profile_medications_title
    }

/** Explains how complete a section is, in plain language. */
@StringRes
fun ProfileSectionStatus.descriptionRes(): Int =
    when (this) {
        ProfileSectionStatus.NOT_ASKED -> R.string.profile_section_not_asked
        ProfileSectionStatus.NONE_CONFIRMED -> R.string.profile_section_none_confirmed
        ProfileSectionStatus.LISTED -> R.string.profile_section_listed
    }

/** Label of one step of the allergy and condition severity scale. */
@StringRes
fun severityLabelRes(severity: Int): Int =
    when (severity) {
        ProfileEntry.MIN_SEVERITY -> R.string.profile_severity_mild
        ProfileEntry.MAX_SEVERITY -> R.string.profile_severity_severe
        else -> R.string.profile_severity_moderate
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
        ProfileMessage.BASICS_SAVED -> R.string.profile_basics_saved
        ProfileMessage.HISTORY_SAVED -> R.string.profile_history_saved
        ProfileMessage.SECTION_CONFIRMED -> R.string.profile_section_confirmed
        ProfileMessage.SECTION_CONFIRMATION_CLEARED -> R.string.profile_section_confirmation_cleared
    }
