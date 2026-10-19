package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medcompanion.app.data.local.entity.ProfileSectionStateEntity
import com.medcompanion.app.domain.model.ProfileSection
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileSectionStateDao {
    @Query("SELECT * FROM profile_section_states")
    fun observeAll(): Flow<List<ProfileSectionStateEntity>>

    @Query("SELECT * FROM profile_section_states WHERE section = :section")
    suspend fun getBySection(section: ProfileSection): ProfileSectionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ProfileSectionStateEntity)

    @Query("DELETE FROM profile_section_states WHERE section = :section")
    suspend fun delete(section: ProfileSection)

    @Query("DELETE FROM profile_section_states")
    suspend fun deleteAll()
}
