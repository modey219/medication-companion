package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "symptom_logs",
    indices = [Index("loggedAt"), Index("name")],
)
data class SymptomLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loggedAt: Instant,
    val name: String,
    val severity: Int,
    val note: String?,
)
