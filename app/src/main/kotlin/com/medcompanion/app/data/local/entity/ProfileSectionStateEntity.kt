package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import java.time.Instant

/**
 * Only the patient's own "none" confirmation is stored. [ProfileSectionStatus.LISTED]
 * is derived from the entries that exist, and the medications section is derived
 * from the medication table, so neither is persisted here.
 */
@Entity(tableName = "profile_section_states")
data class ProfileSectionStateEntity(
    @PrimaryKey val section: ProfileSection,
    val status: ProfileSectionStatus,
    val confirmedAt: Instant?,
)
