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

/** The bottom-bar tab that is selected for a given navigation route. */
fun topLevelDestinationFromRoute(route: String?): TopLevelDestination =
    TopLevelDestination.entries.firstOrNull { it.route == route } ?: TopLevelDestination.TODAY
