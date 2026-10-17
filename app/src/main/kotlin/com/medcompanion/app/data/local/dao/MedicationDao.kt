package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.domain.model.MedicationStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Delete
    suspend fun delete(medication: MedicationEntity)

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: MedicationStatus): Flow<List<MedicationEntity>>

    @Query(
        "UPDATE medications SET status = :status, stopReason = :reason, endDate = :endDate " +
            "WHERE id = :id",
    )
    suspend fun updateStatus(
        id: Long,
        status: MedicationStatus,
        reason: String?,
        endDate: Instant?,
    )

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}
