package com.medcompanion.app.domain.repository

import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Read and write access to the patient's medications. */
interface MedicationRepository {
    fun observeAll(): Flow<List<Medication>>

    fun observeByStatus(status: MedicationStatus): Flow<List<Medication>>

    suspend fun getById(id: Long): Medication?

    suspend fun add(medication: Medication): Long

    suspend fun update(medication: Medication)

    /** Marks a medication as stopped, recording the reason and end date. */
    suspend fun stop(
        id: Long,
        reason: String?,
        endDate: Instant,
    )

    suspend fun delete(medication: Medication)
}
