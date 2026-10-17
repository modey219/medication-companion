package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.medcompanion.app.data.local.entity.ProfileEntryEntity
import com.medcompanion.app.domain.model.ProfileEntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileEntryDao {
    @Insert
    suspend fun insert(entry: ProfileEntryEntity): Long

    @Update
    suspend fun update(entry: ProfileEntryEntity)

    @Delete
    suspend fun delete(entry: ProfileEntryEntity)

    @Query("SELECT * FROM profile_entries ORDER BY label")
    fun observeAll(): Flow<List<ProfileEntryEntity>>

    @Query("SELECT * FROM profile_entries WHERE type = :type ORDER BY label")
    fun observeByType(type: ProfileEntryType): Flow<List<ProfileEntryEntity>>

    @Query("SELECT * FROM profile_entries WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntryEntity?

    @Query("DELETE FROM profile_entries")
    suspend fun deleteAll()
}
