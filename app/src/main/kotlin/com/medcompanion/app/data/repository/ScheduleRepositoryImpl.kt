package com.medcompanion.app.data.repository

import com.medcompanion.app.data.local.dao.ScheduleDao
import com.medcompanion.app.data.mapper.toDomain
import com.medcompanion.app.data.mapper.toEntity
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl
    @Inject
    constructor(
        private val scheduleDao: ScheduleDao,
    ) : ScheduleRepository {
        override fun observeByMedication(medicationId: Long): Flow<List<Schedule>> =
            scheduleDao.observeByMedication(medicationId).map { entities ->
                entities.map { it.toDomain() }
            }

        override suspend fun getByMedication(medicationId: Long): List<Schedule> =
            scheduleDao.getByMedication(medicationId).map { it.toDomain() }

        override suspend fun getById(id: Long): Schedule? = scheduleDao.getById(id)?.toDomain()

        override suspend fun add(schedule: Schedule): Long = scheduleDao.insert(schedule.toEntity())

        override suspend fun update(schedule: Schedule) = scheduleDao.update(schedule.toEntity())

        override suspend fun delete(schedule: Schedule) = scheduleDao.delete(schedule.toEntity())
    }
