package com.medcompanion.app.data.mapper

import com.medcompanion.app.data.local.entity.DoseEventEntity
import com.medcompanion.app.data.local.entity.MedicalHistoryEntryEntity
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.data.local.entity.PatientBasicsEntity
import com.medcompanion.app.data.local.entity.ProfileEntryEntity
import com.medcompanion.app.data.local.entity.ProfileSectionStateEntity
import com.medcompanion.app.data.local.entity.ScheduleEntity
import com.medcompanion.app.data.local.entity.SymptomLogEntity
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.MedicalHistoryEntry
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.PatientBasics
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.ProfileSectionState
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.SymptomLog

/** Maps between Room entities and domain models so layers stay decoupled. */
fun MedicationEntity.toDomain(): Medication =
    Medication(
        id = id,
        name = name,
        form = form,
        strength = strength,
        instructions = instructions,
        source = source,
        startDate = startDate,
        endDate = endDate,
        status = status,
        stopReason = stopReason,
        createdAt = createdAt,
        expiryDate = expiryDate,
        openedAt = openedAt,
        remainingDoses = remainingDoses,
    )

fun Medication.toEntity(): MedicationEntity =
    MedicationEntity(
        id = id,
        name = name,
        form = form,
        strength = strength,
        instructions = instructions,
        source = source,
        startDate = startDate,
        endDate = endDate,
        status = status,
        stopReason = stopReason,
        createdAt = createdAt,
        expiryDate = expiryDate,
        openedAt = openedAt,
        remainingDoses = remainingDoses,
    )

fun ScheduleEntity.toDomain(): Schedule =
    Schedule(
        id = id,
        medicationId = medicationId,
        type = type,
        times = times,
        intervalHours = intervalHours,
        weekdays = weekdays,
        doseAmount = doseAmount,
        foodRelation = foodRelation,
    )

fun Schedule.toEntity(): ScheduleEntity =
    ScheduleEntity(
        id = id,
        medicationId = medicationId,
        type = type,
        times = times,
        intervalHours = intervalHours,
        weekdays = weekdays,
        doseAmount = doseAmount,
        foodRelation = foodRelation,
    )

fun DoseEventEntity.toDomain(): DoseEvent =
    DoseEvent(
        id = id,
        medicationId = medicationId,
        scheduledAt = scheduledAt,
        status = status,
        actedAt = actedAt,
        note = note,
    )

fun DoseEvent.toEntity(): DoseEventEntity =
    DoseEventEntity(
        id = id,
        medicationId = medicationId,
        scheduledAt = scheduledAt,
        status = status,
        actedAt = actedAt,
        note = note,
    )

fun SymptomLogEntity.toDomain(): SymptomLog =
    SymptomLog(
        id = id,
        loggedAt = loggedAt,
        name = name,
        severity = severity,
        note = note,
    )

fun SymptomLog.toEntity(): SymptomLogEntity =
    SymptomLogEntity(
        id = id,
        loggedAt = loggedAt,
        name = name,
        severity = severity,
        note = note,
    )

fun ProfileEntryEntity.toDomain(): ProfileEntry =
    ProfileEntry(
        id = id,
        type = type,
        label = label,
        severity = severity,
        reaction = reaction,
        sinceYear = sinceYear,
        note = note,
    )

fun ProfileEntry.toEntity(): ProfileEntryEntity =
    ProfileEntryEntity(
        id = id,
        type = type,
        label = label,
        severity = severity,
        reaction = reaction,
        sinceYear = sinceYear,
        note = note,
    )

fun PatientBasicsEntity.toDomain(): PatientBasics =
    PatientBasics(
        birthYear = birthYear,
        sex = sex,
        pregnantOrBreastfeeding = pregnantOrBreastfeeding,
        weightKg = weightKg,
    )

fun PatientBasics.toEntity(): PatientBasicsEntity =
    PatientBasicsEntity(
        birthYear = birthYear,
        sex = sex,
        pregnantOrBreastfeeding = pregnantOrBreastfeeding,
        weightKg = weightKg,
    )

fun MedicalHistoryEntryEntity.toDomain(): MedicalHistoryEntry =
    MedicalHistoryEntry(
        id = id,
        type = type,
        label = label,
        year = year,
        note = note,
    )

fun MedicalHistoryEntry.toEntity(): MedicalHistoryEntryEntity =
    MedicalHistoryEntryEntity(
        id = id,
        type = type,
        label = label,
        year = year,
        note = note,
    )

fun ProfileSectionStateEntity.toDomain(): ProfileSectionState =
    ProfileSectionState(
        section = section,
        status = status,
        confirmedAt = confirmedAt,
    )
