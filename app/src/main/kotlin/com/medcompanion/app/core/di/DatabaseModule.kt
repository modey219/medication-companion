package com.medcompanion.app.core.di

import android.content.Context
import androidx.room.Room
import com.medcompanion.app.data.local.MIGRATION_1_2
import com.medcompanion.app.data.local.MedCompanionDatabase
import com.medcompanion.app.data.local.dao.DoseEventDao
import com.medcompanion.app.data.local.dao.MedicationDao
import com.medcompanion.app.data.local.dao.ProfileEntryDao
import com.medcompanion.app.data.local.dao.ScheduleDao
import com.medcompanion.app.data.local.dao.SymptomLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MedCompanionDatabase =
        Room
            .databaseBuilder(context, MedCompanionDatabase::class.java, MedCompanionDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideMedicationDao(database: MedCompanionDatabase): MedicationDao = database.medicationDao()

    @Provides
    fun provideScheduleDao(database: MedCompanionDatabase): ScheduleDao = database.scheduleDao()

    @Provides
    fun provideDoseEventDao(database: MedCompanionDatabase): DoseEventDao = database.doseEventDao()

    @Provides
    fun provideSymptomLogDao(database: MedCompanionDatabase): SymptomLogDao = database.symptomLogDao()

    @Provides
    fun provideProfileEntryDao(database: MedCompanionDatabase): ProfileEntryDao = database.profileEntryDao()
}
