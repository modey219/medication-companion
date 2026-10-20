package com.medcompanion.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.AppSettings
import com.medcompanion.app.domain.settings.ConsentRecord
import com.medcompanion.app.domain.settings.ReminderPreferences
import com.medcompanion.app.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed settings (D-014). A read error falls back to the defaults
 * instead of crashing the app, which matters because settings are read on
 * startup before the first frame.
 *
 * The reminder and stock values are clamped on write and on read: a value that
 * somehow ended up out of range can never reach the reminder engine.
 */
@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val clock: AppClock,
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
                        graceMinutes =
                            ReminderPreferences.clampGraceMinutes(
                                preferences[GRACE_MINUTES] ?: ReminderPreferences.DEFAULT_GRACE_MINUTES,
                            ),
                        snoozeMinutes =
                            ReminderPreferences.clampSnoozeMinutes(
                                preferences[SNOOZE_MINUTES] ?: ReminderPreferences.DEFAULT_SNOOZE_MINUTES,
                            ),
                        repeatMinutes =
                            ReminderPreferences.clampRepeatMinutes(
                                preferences[REPEAT_MINUTES] ?: ReminderPreferences.DEFAULT_REPEAT_MINUTES,
                            ),
                        lowStockThreshold =
                            ReminderPreferences.clampLowStockThreshold(
                                preferences[LOW_STOCK_THRESHOLD] ?: ReminderPreferences.DEFAULT_LOW_STOCK_THRESHOLD,
                            ),
                        pharmacyNumber = preferences[PHARMACY_NUMBER].orEmpty(),
                        assistantConsentCompleted = preferences[ASSISTANT_CONSENT_COMPLETED] == true,
                        assistantProfileConsent =
                            consentRecord(preferences, PROFILE_CONSENT_VERSION, PROFILE_CONSENT_ACCEPTED_AT),
                        assistantMediaConsent =
                            consentRecord(preferences, MEDIA_CONSENT_VERSION, MEDIA_CONSENT_ACCEPTED_AT),
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

        override suspend fun setGraceMinutes(minutes: Long) {
            dataStore.edit { preferences ->
                preferences[GRACE_MINUTES] = ReminderPreferences.clampGraceMinutes(minutes)
            }
        }

        override suspend fun setSnoozeMinutes(minutes: Long) {
            dataStore.edit { preferences ->
                preferences[SNOOZE_MINUTES] = ReminderPreferences.clampSnoozeMinutes(minutes)
            }
        }

        override suspend fun setRepeatMinutes(minutes: Long) {
            dataStore.edit { preferences ->
                preferences[REPEAT_MINUTES] = ReminderPreferences.clampRepeatMinutes(minutes)
            }
        }

        override suspend fun setLowStockThreshold(doses: Int) {
            dataStore.edit { preferences ->
                preferences[LOW_STOCK_THRESHOLD] = ReminderPreferences.clampLowStockThreshold(doses)
            }
        }

        override suspend fun setPharmacyNumber(number: String) {
            dataStore.edit { preferences ->
                preferences[PHARMACY_NUMBER] = number.trim()
            }
        }

        override suspend fun completeAssistantConsent() {
            dataStore.edit { preferences ->
                preferences[ASSISTANT_CONSENT_COMPLETED] = true
            }
        }

        override suspend fun grantAssistantProfileConsent() {
            dataStore.edit { preferences ->
                preferences.writeConsent(PROFILE_CONSENT_VERSION, PROFILE_CONSENT_ACCEPTED_AT)
            }
        }

        override suspend fun revokeAssistantProfileConsent() {
            dataStore.edit { preferences ->
                preferences.clearConsent(PROFILE_CONSENT_VERSION, PROFILE_CONSENT_ACCEPTED_AT)
            }
        }

        override suspend fun grantAssistantMediaConsent() {
            dataStore.edit { preferences ->
                preferences.writeConsent(MEDIA_CONSENT_VERSION, MEDIA_CONSENT_ACCEPTED_AT)
            }
        }

        override suspend fun revokeAssistantMediaConsent() {
            dataStore.edit { preferences ->
                preferences.clearConsent(MEDIA_CONSENT_VERSION, MEDIA_CONSENT_ACCEPTED_AT)
            }
        }

        override suspend fun completeOnboarding() {
            dataStore.edit { preferences ->
                preferences[ONBOARDING_COMPLETED] = true
            }
        }

        override suspend fun clear() {
            dataStore.edit { preferences ->
                val consented = preferences[ONBOARDING_COMPLETED] == true
                preferences.clear()
                if (consented) preferences[ONBOARDING_COMPLETED] = true
            }
        }

        private fun consentRecord(
            preferences: Preferences,
            versionKey: Preferences.Key<Int>,
            acceptedAtKey: Preferences.Key<Long>,
        ): ConsentRecord =
            ConsentRecord(
                version = preferences[versionKey] ?: ConsentRecord.NOT_ACCEPTED,
                acceptedAt = preferences[acceptedAtKey]?.let(Instant::ofEpochMilli),
            )

        private fun MutablePreferences.writeConsent(
            versionKey: Preferences.Key<Int>,
            acceptedAtKey: Preferences.Key<Long>,
        ) {
            this[versionKey] = ConsentRecord.CURRENT_VERSION
            this[acceptedAtKey] = clock.now().toEpochMilli()
        }

        private fun MutablePreferences.clearConsent(
            versionKey: Preferences.Key<Int>,
            acceptedAtKey: Preferences.Key<Long>,
        ) {
            remove(versionKey)
            remove(acceptedAtKey)
        }

        private companion object {
            val LANGUAGE = stringPreferencesKey("language")
            val EMERGENCY_NUMBER = stringPreferencesKey("emergency_number")
            val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
            val GRACE_MINUTES = longPreferencesKey("reminder_grace_minutes")
            val SNOOZE_MINUTES = longPreferencesKey("reminder_snooze_minutes")
            val REPEAT_MINUTES = longPreferencesKey("reminder_repeat_minutes")
            val LOW_STOCK_THRESHOLD = intPreferencesKey("low_stock_threshold")
            val PHARMACY_NUMBER = stringPreferencesKey("pharmacy_number")
            val ASSISTANT_CONSENT_COMPLETED = booleanPreferencesKey("assistant_consent_completed")
            val PROFILE_CONSENT_VERSION = intPreferencesKey("assistant_profile_consent_version")
            val PROFILE_CONSENT_ACCEPTED_AT = longPreferencesKey("assistant_profile_consent_accepted_at")
            val MEDIA_CONSENT_VERSION = intPreferencesKey("assistant_media_consent_version")
            val MEDIA_CONSENT_ACCEPTED_AT = longPreferencesKey("assistant_media_consent_accepted_at")
        }
    }
