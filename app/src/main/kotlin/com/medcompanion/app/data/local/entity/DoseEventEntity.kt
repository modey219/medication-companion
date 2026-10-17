package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.DoseStatus
import java.time.Instant

@Entity(
    tableName = "dose_events",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["medicationId", "scheduledAt"], unique = true),
        Index("status"),
        Index("scheduledAt"),
    ],
)
data class DoseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: Instant,
    val status: DoseStatus,
    val actedAt: Instant?,
    val note: String?,
)
