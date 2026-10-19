package com.medcompanion.app.data.mapper

import com.medcompanion.app.data.local.entity.DoseEventEntity
import com.medcompanion.app.data.local.entity.MedicationEntity
import com.medcompanion.app.data.local.entity.ProfileEntryEntity
import com.medcompanion.app.data.local.entity.ScheduleEntity
import com.medcompanion.app.data.local.entity.SymptomLogEntity
import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.ProfileEntry
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
        note = note,
    )

fun ProfileEntry.toEntity(): ProfileEntryEntity =
    ProfileEntryEntity(
        id = id,
        type = type,
        label = label,
        note = note,
    )
