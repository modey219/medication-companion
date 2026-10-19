package com.medcompanion.app.data.export

import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.export.FullData
import com.medcompanion.app.domain.export.MedicationWithSchedules
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ProfileRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.repository.SymptomRepository
import com.medcompanion.app.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads everything the patient entered into one [FullData] value (T8a). Both
 * exporters use it, so the JSON and the PDF can never disagree, and the
 * Profile screen can tell whether there is anything to export.
 */
@Singleton
class FullDataCollector
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val profileRepository: ProfileRepository,
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val doseEventRepository: DoseEventRepository,
        private val symptomRepository: SymptomRepository,
        private val clock: AppClock,
    ) {
        suspend fun collect(): FullData {
            val medications = medicationRepository.observeAll().first()
            val schedules = scheduleRepository.observeAll().first()
            return FullData(
                generatedAt = clock.now(),
                settings = settingsRepository.observe().first(),
                profileEntries = profileRepository.observeAll().first(),
                medications =
                    medications.map { medication ->
                        MedicationWithSchedules(
                            medication = medication,
                            schedules = schedules.filter { it.medicationId == medication.id },
                        )
                    },
                doses = doseEventRepository.observeAll().first(),
                symptoms = symptomRepository.observeAll().first(),
            )
        }
    }
