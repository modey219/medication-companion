package com.medcompanion.app.domain.repository

import com.medcompanion.app.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

/** Read and write access to medication schedules. */
interface ScheduleRepository {
    fun observeAll(): Flow<List<Schedule>>

    fun observeByMedication(medicationId: Long): Flow<List<Schedule>>

    suspend fun getByMedication(medicationId: Long): List<Schedule>

    suspend fun getById(id: Long): Schedule?

    suspend fun add(schedule: Schedule): Long

    suspend fun update(schedule: Schedule)

    suspend fun delete(schedule: Schedule)
}
