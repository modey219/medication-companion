package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val type: ScheduleType,
    val times: List<LocalTime>,
    val intervalHours: Int?,
    val weekdays: Set<DayOfWeek>,
    val doseAmount: String,
    val foodRelation: FoodRelation,
)
