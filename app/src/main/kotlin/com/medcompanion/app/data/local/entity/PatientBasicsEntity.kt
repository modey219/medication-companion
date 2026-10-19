package com.medcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.medcompanion.app.domain.model.Sex

/**
 * The patient's basic facts. A single row with [SINGLETON_ID], so saving is an
 * upsert and there is never more than one row.
 */
@Entity(tableName = "patient_basics")
data class PatientBasicsEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val birthYear: Int?,
    val sex: Sex?,
    val pregnantOrBreastfeeding: Boolean?,
    val weightKg: Int?,
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
