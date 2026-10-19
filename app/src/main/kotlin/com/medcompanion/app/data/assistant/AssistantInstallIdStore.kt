package com.medcompanion.app.data.assistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The opaque install id the backend uses to rate-limit per install.
 *
 * It is a random value that identifies nothing: no account, no device id, no
 * phone number. It lives in the settings DataStore and is regenerated when the
 * patient deletes all their data, so a fresh identity is used afterwards.
 */
@Singleton
class AssistantInstallIdStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /** The stored id, generating and storing a new one the first time. */
        suspend fun get(): String {
            val existing = dataStore.data.first()[INSTALL_ID]
            if (!existing.isNullOrEmpty()) return existing

            val generated = newId()
            dataStore.edit { preferences ->
                // Another coroutine may have written one in the meantime; keep it.
                if (preferences[INSTALL_ID].isNullOrEmpty()) {
                    preferences[INSTALL_ID] = generated
                }
            }
            return dataStore.data.first()[INSTALL_ID] ?: generated
        }

        /** 32 lowercase hex characters, inside the backend's allowed alphabet. */
        private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

        private companion object {
            val INSTALL_ID = stringPreferencesKey("assistant_install_id")
        }
    }
