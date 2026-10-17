package com.medcompanion.app.domain.repository

import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import kotlinx.coroutines.flow.Flow

/** Read and write access to the patient's allergies and chronic conditions. */
interface ProfileRepository {
    fun observeAll(): Flow<List<ProfileEntry>>

    fun observeByType(type: ProfileEntryType): Flow<List<ProfileEntry>>

    suspend fun add(entry: ProfileEntry): Long

    suspend fun update(entry: ProfileEntry)

    suspend fun delete(entry: ProfileEntry)
}
