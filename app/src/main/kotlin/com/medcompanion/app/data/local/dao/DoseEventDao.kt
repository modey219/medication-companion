package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medcompanion.app.data.local.entity.DoseEventEntity
import com.medcompanion.app.domain.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface DoseEventDao {
    /**
     * Idempotent insert: the unique index on (medicationId, scheduledAt) means a
     * repeated generation run ignores duplicates and returns -1 for them.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(event: DoseEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoringDuplicates(events: List<DoseEventEntity>): List<Long>

    @Update
    suspend fun update(event: DoseEventEntity)

    @Query("SELECT * FROM dose_events WHERE id = :id")
    suspend fun getById(id: Long): DoseEventEntity?

    /** Every event ever generated, oldest first; used by the data export (T8a). */
    @Query("SELECT * FROM dose_events ORDER BY scheduledAt")
    fun observeAll(): Flow<List<DoseEventEntity>>

    /** Ids of every event, so their alarms and notifications can be cancelled. */
    @Query("SELECT id FROM dose_events")
    suspend fun getAllIds(): List<Long>

    @Query(
        "SELECT * FROM dose_events WHERE scheduledAt >= :start AND scheduledAt < :end " +
            "ORDER BY scheduledAt",
    )
    fun observeBetween(
        start: Instant,
        end: Instant,
    ): Flow<List<DoseEventEntity>>

    @Query(
        "SELECT * FROM dose_events WHERE medicationId = :medicationId ORDER BY scheduledAt",
    )
    fun observeByMedication(medicationId: Long): Flow<List<DoseEventEntity>>

    @Query(
        "SELECT * FROM dose_events WHERE scheduledAt >= :start AND scheduledAt < :end " +
            "ORDER BY scheduledAt",
    )
    suspend fun getBetween(
        start: Instant,
        end: Instant,
    ): List<DoseEventEntity>

    @Query("SELECT * FROM dose_events WHERE status = :status AND scheduledAt <= :cutoff")
    suspend fun getByStatusBefore(
        status: DoseStatus,
        cutoff: Instant,
    ): List<DoseEventEntity>

    @Query(
        "SELECT * FROM dose_events WHERE scheduledAt > :after AND status IN (:statuses) " +
            "ORDER BY scheduledAt LIMIT 1",
    )
    fun observeNextAfter(
        after: Instant,
        statuses: List<DoseStatus>,
    ): Flow<DoseEventEntity?>

    @Query("UPDATE dose_events SET status = :status, actedAt = :actedAt WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: DoseStatus,
        actedAt: Instant?,
    )

    @Query("DELETE FROM dose_events WHERE medicationId = :medicationId")
    suspend fun deleteByMedication(medicationId: Long)

    @Query("DELETE FROM dose_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM dose_events")
    suspend fun deleteAll()
}
