package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import java.time.Instant

@Entity(
    tableName = "medications",
    indices = [Index("status"), Index("name")],
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: MedicationForm,
    val strength: String,
    val instructions: String,
    val source: MedicationSource,
    val startDate: Instant,
    val endDate: Instant?,
    val status: MedicationStatus,
    val stopReason: String?,
    val createdAt: Instant,
)
