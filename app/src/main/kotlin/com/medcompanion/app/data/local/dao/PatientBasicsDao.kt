package com.medcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medcompanion.app.data.local.entity.PatientBasicsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientBasicsDao {
    @Query("SELECT * FROM patient_basics WHERE id = ${PatientBasicsEntity.SINGLETON_ID}")
    fun observe(): Flow<PatientBasicsEntity?>

    @Query("SELECT * FROM patient_basics WHERE id = ${PatientBasicsEntity.SINGLETON_ID}")
    suspend fun get(): PatientBasicsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(basics: PatientBasicsEntity)

    @Query("DELETE FROM patient_basics")
    suspend fun deleteAll()
}
