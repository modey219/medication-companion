package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.MedicalHistoryType

@Entity(
    tableName = "medical_history_entries",
    indices = [Index("type")],
)
data class MedicalHistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MedicalHistoryType,
    val label: String,
    val year: Int?,
    val note: String?,
)
