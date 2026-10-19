package com.medcompanion.app.core.di

import com.medcompanion.app.data.maintenance.DataMaintenanceImpl
import com.medcompanion.app.domain.maintenance.DataMaintenance
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Data maintenance use cases (T8a). */
@Module
@InstallIn(SingletonComponent::class)
abstract class MaintenanceModule {
    @Binds
    @Singleton
    abstract fun bindDataMaintenance(impl: DataMaintenanceImpl): DataMaintenance
}
