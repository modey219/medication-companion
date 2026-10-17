package com.medcompanion.app.domain.model

/**
 * A fact the patient entered about themselves: an allergy or a chronic condition.
 */
data class ProfileEntry(
    val id: Long = 0,
    val type: ProfileEntryType,
    val label: String,
    val note: String? = null,
)
