package com.medcompanion.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed settings (D-014). A read error falls back to the defaults
 * instead of crashing the app, which matters because settings are read on
 * startup before the first frame.
 */
@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override fun observe(): Flow<AppSettings> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map { preferences ->
                    AppSettings(
                        language = AppLanguage.fromTag(preferences[LANGUAGE]),
                        emergencyNumber = preferences[EMERGENCY_NUMBER].orEmpty(),
                        onboardingCompleted = preferences[ONBOARDING_COMPLETED] == true,
                    )
                }

        override suspend fun setLanguage(language: AppLanguage) {
            dataStore.edit { preferences ->
                if (language == AppLanguage.SYSTEM) {
                    preferences.remove(LANGUAGE)
                } else {
                    preferences[LANGUAGE] = language.languageTag.orEmpty()
                }
            }
        }

        override suspend fun setEmergencyNumber(number: String) {
            dataStore.edit { preferences ->
                preferences[EMERGENCY_NUMBER] = number.trim()
            }
        }

        override suspend fun completeOnboarding() {
            dataStore.edit { preferences ->
                preferences[ONBOARDING_COMPLETED] = true
            }
        }

        override suspend fun clear() {
            dataStore.edit { preferences ->
                val consent = preferences[ONBOARDING_COMPLETED]
                preferences.clear()
                if (consent != null) preferences[ONBOARDING_COMPLETED] = consent
            }
        }

        private companion object {
            val LANGUAGE = stringPreferencesKey("language")
            val EMERGENCY_NUMBER = stringPreferencesKey("emergency_number")
            val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        }
    }
