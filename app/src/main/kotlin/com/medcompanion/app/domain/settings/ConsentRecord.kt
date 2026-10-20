package com.medcompanion.app.domain.settings

import java.time.Instant

/**
 * One consent the patient gave, with the version of the wording they read and
 * the moment they gave it (T13, D-021).
 *
 * A consent counts as *granted* only when the stored [version] is at least the
 * current one and it carries a time. Raising [CURRENT_VERSION] therefore asks
 * every older acceptance again, which is what a wording change must do, and
 * revoking resets the record to [NOT_ACCEPTED].
 */
data class ConsentRecord(
    val version: Int = NOT_ACCEPTED,
    val acceptedAt: Instant? = null,
) {
    /** True when this record is for the given (or the current) consent version. */
    fun isGranted(currentVersion: Int = CURRENT_VERSION): Boolean = version >= currentVersion && acceptedAt != null

    companion object {
        /** No consent was ever given. */
        const val NOT_ACCEPTED = 0

        /** Bump when the wording changes, so the patient is asked again. */
        const val CURRENT_VERSION = 1
    }
}
