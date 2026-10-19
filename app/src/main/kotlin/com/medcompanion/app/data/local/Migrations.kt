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

/**
 * 2 -> 3 (T12): the health profile the assistant reviews before answering.
 *
 * - `profile_entries` gains three nullable columns, so entries written before
 *   this version keep working untouched.
 * - `patient_basics` holds a single row (id 1) with the patient's basic facts.
 * - `medical_history_entries` holds past illnesses, surgeries, hospitalizations
 *   and family history.
 * - `profile_section_states` records only the patient's own "none" confirmation;
 *   `LISTED` is derived from the rows that exist, so it is never stored.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profile_entries ADD COLUMN severity INTEGER")
            db.execSQL("ALTER TABLE profile_entries ADD COLUMN reaction TEXT")
            db.execSQL("ALTER TABLE profile_entries ADD COLUMN sinceYear INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `patient_basics` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`birthYear` INTEGER, " +
                    "`sex` TEXT, " +
                    "`pregnantOrBreastfeeding` INTEGER, " +
                    "`weightKg` INTEGER, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medical_history_entries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`label` TEXT NOT NULL, " +
                    "`year` INTEGER, " +
                    "`note` TEXT)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_medical_history_entries_type` " +
                    "ON `medical_history_entries` (`type`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profile_section_states` (" +
                    "`section` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`confirmedAt` INTEGER, " +
                    "PRIMARY KEY(`section`))",
            )
        }
    }
