package com.medcompanion.app.domain.repository

import com.medcompanion.app.domain.model.SymptomLog
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Read and write access to the patient's symptom log. */
interface SymptomRepository {
    fun observeAll(): Flow<List<SymptomLog>>

    fun observeBetween(
        start: Instant,
        end: Instant,
    ): Flow<List<SymptomLog>>

    suspend fun add(log: SymptomLog): Long

    suspend fun delete(log: SymptomLog)
}
