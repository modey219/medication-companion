package com.medcompanion.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.medcompanion.app.data.local.dao.DoseEventDao
import com.medcompanion.app.data.local.dao.MedicationDao
import com.medcompanion.app.data.local.dao.ProfileEntryDao
import com.medcompanion.app.data.local.dao.ScheduleDao
import com.medcompanion.app.data.local.dao.SymptomLogDao
import com.medcompanion.app.data.local.entity.DoseEventEntity
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.data.local.entity.ProfileEntryEntity
import com.medcompanion.app.data.local.entity.ScheduleEntity
import com.medcompanion.app.data.local.entity.SymptomLogEntity

/**
 * All app data lives here, on the device only (AGENTS.md > Privacy and security).
 *
 * The schema is exported to `app/schemas` so future versions can add explicit
 * migrations and tests. Bump [version] and add a migration for every change.
 */
@Database(
    entities = [
        MedicationEntity::class,
        ScheduleEntity::class,
        DoseEventEntity::class,
        SymptomLogEntity::class,
        ProfileEntryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MedCompanionDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun doseEventDao(): DoseEventDao

    abstract fun symptomLogDao(): SymptomLogDao

    abstract fun profileEntryDao(): ProfileEntryDao

    companion object {
        const val NAME = "medcompanion.db"
    }
}
