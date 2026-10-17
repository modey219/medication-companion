package com.medcompanion.app.data.repository

import com.medcompanion.app.data.local.dao.ProfileEntryDao
import com.medcompanion.app.data.mapper.toDomain
import com.medcompanion.app.data.mapper.toEntity
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl
    @Inject
    constructor(
        private val profileEntryDao: ProfileEntryDao,
    ) : ProfileRepository {
        override fun observeAll(): Flow<List<ProfileEntry>> =
            profileEntryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeByType(type: ProfileEntryType): Flow<List<ProfileEntry>> =
            profileEntryDao.observeByType(type).map { entities -> entities.map { it.toDomain() } }

        override suspend fun add(entry: ProfileEntry): Long = profileEntryDao.insert(entry.toEntity())

        override suspend fun update(entry: ProfileEntry) = profileEntryDao.update(entry.toEntity())

        override suspend fun delete(entry: ProfileEntry) = profileEntryDao.delete(entry.toEntity())
    }
