package com.medcompanion.app.domain.repository

import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionState
import kotlinx.coroutines.flow.Flow

/**
 * Read and write access to the patient's health profile: allergies, chronic
 * conditions, basic facts, medical history, and how complete each section is.
 */
interface ProfileRepository {
    fun observeAll(): Flow<List<ProfileEntry>>

    fun observeByType(type: ProfileEntryType): Flow<List<ProfileEntry>>

    suspend fun add(entry: ProfileEntry): Long

    suspend fun update(entry: ProfileEntry)

    suspend fun delete(entry: ProfileEntry)

    /** The patient's basic facts, or null when they have not entered any. */
    fun observeBasics(): Flow<PatientBasics?>

    suspend fun saveBasics(basics: PatientBasics)

    fun observeHistory(): Flow<List<MedicalHistoryEntry>>

    suspend fun addHistory(entry: MedicalHistoryEntry): Long

    suspend fun updateHistory(entry: MedicalHistoryEntry)

    suspend fun deleteHistory(entry: MedicalHistoryEntry)

    /** The state of every profile section, derived from the rows that exist. */
    fun observeSections(): Flow<Map<ProfileSection, ProfileSectionState>>

    /** Records the patient's own confirmation that a section has nothing to list. */
    suspend fun confirmSectionEmpty(section: ProfileSection)

    /** Forgets a "none" confirmation so the section goes back to "not asked". */
    suspend fun clearSectionConfirmation(section: ProfileSection)
}
