package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.medcompanion.app.data.local.entity.MedicalHistoryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalHistoryEntryDao {
    @Insert
    suspend fun insert(entry: MedicalHistoryEntryEntity): Long

    @Update
    suspend fun update(entry: MedicalHistoryEntryEntity)

    @Delete
    suspend fun delete(entry: MedicalHistoryEntryEntity)

    @Query("SELECT * FROM medical_history_entries ORDER BY type, label")
    fun observeAll(): Flow<List<MedicalHistoryEntryEntity>>

    @Query("SELECT * FROM medical_history_entries WHERE id = :id")
    suspend fun getById(id: Long): MedicalHistoryEntryEntity?

    @Query("DELETE FROM medical_history_entries")
    suspend fun deleteAll()
}
