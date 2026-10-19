package com.medcompanion.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.medcompanion.app.core.locale.AppCompatLanguageApplier
import com.medcompanion.app.core.locale.LanguageApplier
import com.medcompanion.app.data.settings.DataStoreSettingsRepository
import com.medcompanion.app.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Settings storage for T8 (D-014). DataStore replaces ad-hoc SharedPreferences:
 * one typed flow for the whole settings model, written off the main thread and
 * read back by the Profile tab.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLanguageApplier(impl: AppCompatLanguageApplier): LanguageApplier

    companion object {
        private const val SETTINGS_FILE = "settings"

        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
            )
    }
}
