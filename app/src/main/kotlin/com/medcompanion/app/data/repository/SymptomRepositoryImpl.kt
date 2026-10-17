package com.medcompanion.app.data.repository

import com.medcompanion.app.data.local.dao.SymptomLogDao
import com.medcompanion.app.data.mapper.toDomain
import com.medcompanion.app.data.mapper.toEntity
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.repository.SymptomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SymptomRepositoryImpl
    @Inject
    constructor(
        private val symptomLogDao: SymptomLogDao,
    ) : SymptomRepository {
        override fun observeAll(): Flow<List<SymptomLog>> =
            symptomLogDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeBetween(
            start: Instant,
            end: Instant,
        ): Flow<List<SymptomLog>> =
            symptomLogDao.observeBetween(start, end).map { entities -> entities.map { it.toDomain() } }

        override suspend fun add(log: SymptomLog): Long = symptomLogDao.insert(log.toEntity())

        override suspend fun delete(log: SymptomLog) = symptomLogDao.delete(log.toEntity())
    }
