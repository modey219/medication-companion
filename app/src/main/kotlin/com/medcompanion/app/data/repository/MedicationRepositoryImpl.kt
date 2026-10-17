package com.medcompanion.app.data.repository

import com.medcompanion.app.data.local.dao.MedicationDao
import com.medcompanion.app.data.mapper.toDomain
import com.medcompanion.app.data.mapper.toEntity
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepositoryImpl
    @Inject
    constructor(
        private val medicationDao: MedicationDao,
    ) : MedicationRepository {
        override fun observeAll(): Flow<List<Medication>> =
            medicationDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeByStatus(status: MedicationStatus): Flow<List<Medication>> =
            medicationDao.observeByStatus(status).map { entities -> entities.map { it.toDomain() } }

        override suspend fun getById(id: Long): Medication? = medicationDao.getById(id)?.toDomain()

        override suspend fun add(medication: Medication): Long = medicationDao.insert(medication.toEntity())

        override suspend fun update(medication: Medication) = medicationDao.update(medication.toEntity())

        override suspend fun stop(
            id: Long,
            reason: String?,
            endDate: Instant,
        ) = medicationDao.updateStatus(
            id = id,
            status = MedicationStatus.STOPPED,
            reason = reason,
            endDate = endDate,
        )

        override suspend fun delete(medication: Medication) = medicationDao.delete(medication.toEntity())
    }
