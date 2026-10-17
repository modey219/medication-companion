package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.medcompanion.app.data.local.entity.SymptomLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SymptomLogDao {
    @Insert
    suspend fun insert(log: SymptomLogEntity): Long

    @Delete
    suspend fun delete(log: SymptomLogEntity)

    @Query("SELECT * FROM symptom_logs ORDER BY loggedAt DESC")
    fun observeAll(): Flow<List<SymptomLogEntity>>

    @Query(
        "SELECT * FROM symptom_logs WHERE loggedAt >= :start AND loggedAt < :end " +
            "ORDER BY loggedAt DESC",
    )
    fun observeBetween(
        start: Instant,
        end: Instant,
    ): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM symptom_logs WHERE id = :id")
    suspend fun getById(id: Long): SymptomLogEntity?

    @Query("DELETE FROM symptom_logs")
    suspend fun deleteAll()
}
