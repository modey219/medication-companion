package com.medcompanion.app.data.repository

import com.medcompanion.app.data.local.dao.DoseEventDao
import com.medcompanion.app.data.mapper.toDomain
import com.medcompanion.app.data.mapper.toEntity
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.repository.DoseEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoseEventRepositoryImpl
    @Inject
    constructor(
        private val doseEventDao: DoseEventDao,
    ) : DoseEventRepository {
        override fun observeBetween(
            start: Instant,
            end: Instant,
        ): Flow<List<DoseEvent>> =
            doseEventDao.observeBetween(start, end).map { entities -> entities.map { it.toDomain() } }

        override fun observeByMedication(medicationId: Long): Flow<List<DoseEvent>> =
            doseEventDao.observeByMedication(medicationId).map { entities ->
                entities.map { it.toDomain() }
            }

        override fun observeNextAfter(after: Instant): Flow<DoseEvent?> =
            doseEventDao
                .observeNextAfter(after, listOf(DoseStatus.PENDING, DoseStatus.SNOOZED))
                .map { it?.toDomain() }

        override suspend fun addIgnoringDuplicates(event: DoseEvent): Long =
            doseEventDao.insertIgnoringDuplicates(event.toEntity())

        override suspend fun addAllIgnoringDuplicates(events: List<DoseEvent>): Int =
            doseEventDao
                .insertAllIgnoringDuplicates(events.map { it.toEntity() })
                .count { it != DUPLICATE_ROW_ID }

        override suspend fun getById(id: Long): DoseEvent? = doseEventDao.getById(id)?.toDomain()

        override suspend fun getBetween(
            start: Instant,
            end: Instant,
        ): List<DoseEvent> = doseEventDao.getBetween(start, end).map { it.toDomain() }

        override suspend fun getByStatusBefore(
            status: DoseStatus,
            cutoff: Instant,
        ): List<DoseEvent> = doseEventDao.getByStatusBefore(status, cutoff).map { it.toDomain() }

        override suspend fun setStatus(
            id: Long,
            status: DoseStatus,
            actedAt: Instant?,
        ) = doseEventDao.updateStatus(id, status, actedAt)

        override suspend fun update(event: DoseEvent) = doseEventDao.update(event.toEntity())

        override suspend fun deleteByMedication(medicationId: Long) = doseEventDao.deleteByMedication(medicationId)

        override suspend fun deleteById(id: Long) = doseEventDao.deleteById(id)

        private companion object {
            /** Room returns -1 when an insert was ignored because of a conflict. */
            const val DUPLICATE_ROW_ID = -1L
        }
    }
