package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.ProfileEntryType

@Entity(
    tableName = "profile_entries",
    indices = [Index("type")],
)
data class ProfileEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ProfileEntryType,
    val label: String,
    val note: String?,
)
