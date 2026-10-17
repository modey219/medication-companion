package com.medcompanion.app.core.di

import com.medcompanion.app.data.repository.DoseEventRepositoryImpl
import com.medcompanion.app.data.repository.MedicationRepositoryImpl
import com.medcompanion.app.data.repository.ProfileRepositoryImpl
import com.medcompanion.app.data.repository.ScheduleRepositoryImpl
import com.medcompanion.app.data.repository.SymptomRepositoryImpl
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ProfileRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.repository.SymptomRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds repository interfaces to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindDoseEventRepository(impl: DoseEventRepositoryImpl): DoseEventRepository

    @Binds
    @Singleton
    abstract fun bindSymptomRepository(impl: SymptomRepositoryImpl): SymptomRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}
