package com.medcompanion.app.domain.model

import java.time.Instant

/**
 * How complete one profile section is, as the app shows it.
 *
 * The patient may either list items or explicitly confirm "none". An empty list
 * with [ProfileSectionStatus.NOT_ASKED] means "we never asked", which is not the
 * same as "there is nothing" (AGENTS.md > Medical Safety Rules).
 */
data class ProfileSectionState(
    val section: ProfileSection,
    val status: ProfileSectionStatus,
    val confirmedAt: Instant? = null,
) {
    /** True when the patient has not been asked about this section yet. */
    val isUnknown: Boolean
        get() = status == ProfileSectionStatus.NOT_ASKED

    companion object {
        /**
         * Works out the state of one section from the rows the patient has plus
         * their own "none" confirmation (if any). Entries always win: if there is
         * something to list, the section is [ProfileSectionStatus.LISTED].
         */
        fun resolve(
            section: ProfileSection,
            hasEntries: Boolean,
            confirmedNoneAt: Instant?,
        ): ProfileSectionState =
            when {
                hasEntries -> ProfileSectionState(section, ProfileSectionStatus.LISTED)
                confirmedNoneAt != null ->
                    ProfileSectionState(section, ProfileSectionStatus.NONE_CONFIRMED, confirmedNoneAt)
                else -> ProfileSectionState(section, ProfileSectionStatus.NOT_ASKED)
            }
    }
}
