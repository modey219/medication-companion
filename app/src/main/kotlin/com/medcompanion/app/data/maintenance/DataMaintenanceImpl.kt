package com.medcompanion.app.data.maintenance

import com.medcompanion.app.core.locale.LanguageApplier
import com.medcompanion.app.data.local.MedCompanionDatabase
import com.medcompanion.app.data.local.dao.DoseEventDao
import com.medcompanion.app.data.reminders.DoseNotificationFactory
import com.medcompanion.app.domain.maintenance.DataMaintenance
import com.medcompanion.app.domain.reminder.DoseAlarmScheduler
import com.medcompanion.app.domain.settings.AppLanguage
import com.medcompanion.app.domain.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes everything the patient entered (T8a, D-014).
 *
 * Order matters: the alarms and notifications of the doses are cancelled
 * *before* the rows disappear, because once a dose row is gone nothing knows
 * which alarms belong to it. The database is cleared in one Room call so no
 * table can be left behind, and the settings are reset but keep the consent
 * record, so the patient is not sent through onboarding again. The app
 * language goes back to the device language, which is what the settings now
 * say; the per-app locale of AppCompat is updated to match.
 */
@Singleton
class DataMaintenanceImpl
    @Inject
    constructor(
        private val database: MedCompanionDatabase,
        private val doseEventDao: DoseEventDao,
        private val alarmScheduler: DoseAlarmScheduler,
        private val notificationFactory: DoseNotificationFactory,
        private val settingsRepository: SettingsRepository,
        private val languageApplier: LanguageApplier,
    ) : DataMaintenance {
        override suspend fun deleteAllData() {
            doseEventDao.getAllIds().forEach { doseId ->
                alarmScheduler.cancel(doseId)
                notificationFactory.cancel(doseId)
            }
            database.clearAllTables()
            settingsRepository.clear()
            languageApplier.apply(AppLanguage.SYSTEM)
        }
    }
