package com.medcompanion.app.data.assistant

import com.medcompanion.app.core.time.AppClock
import com.medcompanion.app.domain.assistant.PatientAdherence
import com.medcompanion.app.domain.assistant.PatientContext
import com.medcompanion.app.domain.assistant.PatientContextEntry
import com.medcompanion.app.domain.assistant.PatientContextHistory
import com.medcompanion.app.domain.assistant.PatientContextMedication
import com.medcompanion.app.domain.assistant.PatientContextSymptom
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ProfileRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import com.medcompanion.app.domain.repository.SymptomRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the structured patient context the assistant is personalized with
 * (T13, D-021). It is assembled on the device from what the patient entered and
 * is only sent while the profile consent is granted.
 *
 * The builder decides what counts as "recent" and caps every list, so the request
 * stays small and the same inputs always produce the same context. Missing data
 * is never turned into a fact: an empty list travels as an empty list together
 * with the section status, so the model is told "not asked" rather than "none".
 *
 * Everything is read once per message; a failure is the caller's to handle (it
 * must not send a personalized question without its context).
 *
 * TODO(clinical-review): the window lengths and the caps, and whether the
 * assistant should see adherence at all.
 */
@Singleton
class PatientContextBuilder
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val medicationRepository: MedicationRepository,
        private val scheduleRepository: ScheduleRepository,
        private val doseEventRepository: DoseEventRepository,
        private val symptomRepository: SymptomRepository,
        private val clock: AppClock,
    ) {
        suspend fun build(): PatientContext {
            val now = clock.now()
            val basics = profileRepository.observeBasics().first()
            val entries = profileRepository.observeAll().first()
            val history = profileRepository.observeHistory().first()
            val sections = profileRepository.observeSections().first()
            val medications = medicationRepository.observeAll().first()

            val active = medications.filter { it.status == MedicationStatus.ACTIVE }
            return PatientContext(
                generatedAt = now,
                ageYears = basics?.birthYear?.let { birthYear -> (clock.today().year - birthYear).takeIf { it >= 0 } },
                sex = basics?.sex,
                pregnantOrBreastfeeding = basics?.pregnantOrBreastfeeding,
                weightKg = basics?.weightKg,
                allergies = entries.forType(ProfileEntryType.ALLERGY),
                conditions = entries.forType(ProfileEntryType.CONDITION),
                medicalHistory =
                    history
                        .sortedBy { it.id }
                        .take(MAX_HISTORY)
                        .map { PatientContextHistory(type = it.type, label = it.label, year = it.year) },
                medications = active.sortedBy { it.name.lowercase() }.take(MAX_MEDICATIONS).map { toMedication(it) },
                stoppedLast30Days = stopped(medications, now),
                adherenceLast14Days = adherence(now),
                recentSymptoms = recentSymptoms(now),
                sections = sections.mapValues { (_, state) -> state.status },
            )
        }

        private suspend fun toMedication(medication: Medication): PatientContextMedication {
            val schedules = scheduleRepository.getByMedication(medication.id)
            return PatientContextMedication(
                name = medication.name.trim(),
                strength = medication.strength.trim(),
                status = medication.status,
                schedules = schedules.sortedBy { it.id }.map { MedicationScheduleText.of(it) },
                stopReason = medication.stopReason?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        private suspend fun stopped(
            medications: List<Medication>,
            now: Instant,
        ): List<PatientContextMedication> {
            val since = now.minus(STOPPED_DAYS, ChronoUnit.DAYS)
            val recent =
                medications
                    .filter { it.status != MedicationStatus.ACTIVE }
                    .filter { medication -> medication.endDate?.let { !it.isBefore(since) } == true }
                    .sortedByDescending { it.endDate }
                    .take(MAX_MEDICATIONS)
            return recent.map { toMedication(it) }
        }

        private suspend fun adherence(now: Instant): PatientAdherence {
            val doses = dosesInLastDays(now, ADHERENCE_DAYS)
            return PatientAdherence(
                taken = doses.count { it.status == DoseStatus.TAKEN },
                missed = doses.count { it.status == DoseStatus.MISSED },
                recorded = doses.size,
            )
        }

        private suspend fun recentSymptoms(now: Instant): List<PatientContextSymptom> =
            symptomRepository
                .observeBetween(now.minus(SYMPTOM_DAYS, ChronoUnit.DAYS), now)
                .first()
                .sortedByDescending { it.loggedAt }
                .take(MAX_SYMPTOMS)
                .map { PatientContextSymptom(name = it.name, severity = it.severity, loggedAt = it.loggedAt) }

        private suspend fun dosesInLastDays(
            now: Instant,
            days: Long,
        ): List<DoseEvent> = doseEventRepository.observeBetween(now.minus(days, ChronoUnit.DAYS), now).first()

        private fun List<ProfileEntry>.forType(type: ProfileEntryType): List<PatientContextEntry> =
            filter { it.type == type }
                .sortedBy { it.id }
                .take(MAX_ENTRIES)
                .map {
                    PatientContextEntry(
                        label = it.label,
                        severity = it.severity,
                        reaction = it.reaction,
                        sinceYear = it.sinceYear,
                    )
                }

        private companion object {
            const val ADHERENCE_DAYS = 14L
            const val SYMPTOM_DAYS = 14L
            const val STOPPED_DAYS = 30L
            const val MAX_ENTRIES = 20
            const val MAX_HISTORY = 20
            const val MAX_MEDICATIONS = 30
            const val MAX_SYMPTOMS = 10
        }
    }
