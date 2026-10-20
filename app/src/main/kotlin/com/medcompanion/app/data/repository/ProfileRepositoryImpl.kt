package com.medcompanion.app.data.repository

import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.data.local.dao.MedicalHistoryEntryDao
import com.medcompanion.app.data.local.dao.MedicationDao
import com.medcompanion.app.data.local.dao.PatientBasicsDao
import com.medcompanion.app.data.local.dao.ProfileEntryDao
import com.medcompanion.app.data.local.dao.ProfileSectionStateDao
import com.medcompanion.app.data.local.entity.ProfileSectionStateEntity
import com.medcompanion.app.data.mapper.toDomain
import com.medcompanion.app.data.mapper.toEntity
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionState
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl
    @Inject
    constructor(
        private val profileEntryDao: ProfileEntryDao,
        private val patientBasicsDao: PatientBasicsDao,
        private val medicalHistoryEntryDao: MedicalHistoryEntryDao,
        private val profileSectionStateDao: ProfileSectionStateDao,
        private val medicationDao: MedicationDao,
        private val clock: AppClock,
    ) : ProfileRepository {
        override fun observeAll(): Flow<List<ProfileEntry>> =
            profileEntryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override fun observeByType(type: ProfileEntryType): Flow<List<ProfileEntry>> =
            profileEntryDao.observeByType(type).map { entities -> entities.map { it.toDomain() } }

        override suspend fun add(entry: ProfileEntry): Long = profileEntryDao.insert(entry.toEntity())

        override suspend fun update(entry: ProfileEntry) = profileEntryDao.update(entry.toEntity())

        override suspend fun delete(entry: ProfileEntry) = profileEntryDao.delete(entry.toEntity())

        override fun observeBasics(): Flow<PatientBasics?> =
            patientBasicsDao.observe().map { entity -> entity?.toDomain() }

        override suspend fun saveBasics(basics: PatientBasics) {
            if (basics.isEmpty) {
                patientBasicsDao.deleteAll()
            } else {
                patientBasicsDao.upsert(basics.toEntity())
            }
        }

        override fun observeHistory(): Flow<List<MedicalHistoryEntry>> =
            medicalHistoryEntryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

        override suspend fun addHistory(entry: MedicalHistoryEntry): Long =
            medicalHistoryEntryDao.insert(entry.toEntity())

        override suspend fun updateHistory(entry: MedicalHistoryEntry) = medicalHistoryEntryDao.update(entry.toEntity())

        override suspend fun deleteHistory(entry: MedicalHistoryEntry) = medicalHistoryEntryDao.delete(entry.toEntity())

        override fun observeSections(): Flow<Map<ProfileSection, ProfileSectionState>> =
            combine(
                profileEntryDao.observeByType(ProfileEntryType.ALLERGY),
                profileEntryDao.observeByType(ProfileEntryType.CONDITION),
                medicalHistoryEntryDao.observeAll(),
                medicationDao.observeAll(),
                profileSectionStateDao.observeAll(),
            ) { allergies, conditions, history, medications, confirmations ->
                val confirmedAt =
                    confirmations
                        .filter { it.status == ProfileSectionStatus.NONE_CONFIRMED }
                        .associate { it.section to it.confirmedAt }
                val hasEntries =
                    mapOf(
                        ProfileSection.ALLERGIES to allergies.isNotEmpty(),
                        ProfileSection.CONDITIONS to conditions.isNotEmpty(),
                        ProfileSection.HISTORY to history.isNotEmpty(),
                        ProfileSection.MEDICATIONS to medications.isNotEmpty(),
                    )
                hasEntries.mapValues { (section, listed) ->
                    ProfileSectionState.resolve(
                        section = section,
                        hasEntries = listed,
                        confirmedNoneAt = confirmedAt[section],
                    )
                }
            }

        override suspend fun confirmSectionEmpty(section: ProfileSection) {
            profileSectionStateDao.upsert(
                ProfileSectionStateEntity(
                    section = section,
                    status = ProfileSectionStatus.NONE_CONFIRMED,
                    confirmedAt = clock.now(),
                ),
            )
        }

        override suspend fun clearSectionConfirmation(section: ProfileSection) {
            profileSectionStateDao.delete(section)
        }
    }
