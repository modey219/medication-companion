package com.medcompanion.app.domain.model

import java.time.Instant

/**
 * A single scheduled dose of a [Medication].
 *
 * [scheduledAt] is unique per medication, so dose generation can retry safely
 * without creating duplicates (AGENTS.md > Data model).
 */
data class DoseEvent(
    val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: Instant,
    val status: DoseStatus = DoseStatus.PENDING,
    val actedAt: Instant? = null,
    val note: String? = null,
)
