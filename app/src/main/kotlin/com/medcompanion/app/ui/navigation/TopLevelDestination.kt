package com.medcompanion.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.medcompanion.app.R

/**
 * The five top-level destinations shown in the bottom navigation bar.
 * The profile is reached from the top bar and is not part of the bottom bar.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    TODAY(route = "today", labelRes = R.string.tab_today, iconRes = R.drawable.ic_tab_today),
    MEDICATIONS(
        route = "medications",
        labelRes = R.string.tab_medications,
        iconRes = R.drawable.ic_tab_medications,
    ),
    SYMPTOMS(route = "symptoms", labelRes = R.string.tab_symptoms, iconRes = R.drawable.ic_tab_symptoms),
    HISTORY(route = "history", labelRes = R.string.tab_history, iconRes = R.drawable.ic_tab_history),
    ASSISTANT(route = "assistant", labelRes = R.string.tab_assistant, iconRes = R.drawable.ic_tab_assistant),
}

/** Route for the profile screen, opened from the top bar. */
const val PROFILE_ROUTE: String = "profile"

/** Reminder setup screen (T5), reached from the Today banner and the profile. */
const val REMINDERS_SETUP_ROUTE: String = "reminders/setup"

/** Privacy policy screen (T8a), reached from the first run and the profile. */
const val PRIVACY_ROUTE: String = "privacy"

/** Navigation argument for the medication id on the edit route. */
const val ARG_MEDICATION_ID: String = "medicationId"

/** Add a new medication. */
const val MEDICATION_ADD_ROUTE: String = "medications/add"

/** Edit an existing medication; [ARG_MEDICATION_ID] is a required Long. */
const val MEDICATION_EDIT_ROUTE: String = "medications/edit/{$ARG_MEDICATION_ID}"

/** Route for editing the medication with [id]. */
fun medicationEditRoute(id: Long): String = "medications/edit/$id"

/** True for the add and edit medication routes (they show a back arrow). */
fun isMedicationEditRoute(route: String?): Boolean =
    route == MEDICATION_ADD_ROUTE ||
        (route != null && route.startsWith("medications/edit/"))

/**
 * The bottom-bar tab that is selected for a given navigation route.
 * Nested routes (e.g. `medications/add`) keep their parent tab selected.
 */
fun topLevelDestinationFromRoute(route: String?): TopLevelDestination =
    TopLevelDestination.entries.firstOrNull {
        route == it.route || (route != null && route.startsWith("${it.route}/"))
    } ?: TopLevelDestination.TODAY
