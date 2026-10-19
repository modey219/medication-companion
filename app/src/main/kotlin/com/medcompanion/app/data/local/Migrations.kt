package com.medcompanion.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2 (Phase 1): patient-entered stock fields on `medications`.
 *
 * All three columns are nullable so existing rows keep working untouched:
 * a null value means the patient has not entered that field yet.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medications ADD COLUMN expiryDate INTEGER")
            db.execSQL("ALTER TABLE medications ADD COLUMN openedAt INTEGER")
            db.execSQL("ALTER TABLE medications ADD COLUMN remainingDoses INTEGER")
        }
    }
