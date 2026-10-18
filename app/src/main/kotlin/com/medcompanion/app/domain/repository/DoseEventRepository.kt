package com.medcompanion.app.domain.repository

import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Read and write access to scheduled doses. */
interface DoseEventRepository {
    fun observeBetween(
        start: Instant,
        end: Instant,
    ): Flow<List<DoseEvent>>

    fun observeByMedication(medicationId: Long): Flow<List<DoseEvent>>

    /** First unacted (PENDING or SNOOZED) dose strictly after [after]; the Today next-dose hint. */
    fun observeNextAfter(after: Instant): Flow<DoseEvent?>

    suspend fun getById(id: Long): DoseEvent?

    /** One-shot read of a window, used by the reminder engine instead of collecting. */
    suspend fun getBetween(
        start: Instant,
        end: Instant,
    ): List<DoseEvent>

    /** All events with the given status at or before [cutoff] (missed-dose catch-up). */
    suspend fun getByStatusBefore(
        status: DoseStatus,
        cutoff: Instant,
    ): List<DoseEvent>

    /**
     * Inserts a dose unless one already exists for the same medication and time.
     * Returns the new row id, or -1 when it was a duplicate.
     */
    suspend fun addIgnoringDuplicates(event: DoseEvent): Long

    /** Returns how many doses were actually inserted (duplicates are skipped). */
    suspend fun addAllIgnoringDuplicates(events: List<DoseEvent>): Int

    suspend fun setStatus(
        id: Long,
        status: DoseStatus,
        actedAt: Instant?,
    )

    suspend fun update(event: DoseEvent)

    suspend fun deleteByMedication(medicationId: Long)

    suspend fun deleteById(id: Long)
}
