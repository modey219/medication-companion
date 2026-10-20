package com.medcompanion.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.data.local.entity.MedicalHistoryEntryEntity
import com.medcompanion.app.data.local.entity.PatientBasicsEntity
import com.medcompanion.app.data.local.entity.ProfileSectionStateEntity
import com.medcompanion.app.domain.model.MedicalHistoryType
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ProfileSection
import com.medcompanion.app.domain.model.ProfileSectionStatus
import com.medcompanion.app.domain.model.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Migration tests built from the exported Room schemas (AGENTS.md > Data
 * model). Each older version is recreated from `app/schemas/.../<version>.json`
 * exactly as Room exported it; opening it with the current database class
 * forces Room to run the migrations and validate the resulting schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MedCompanionDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `migration 1 to 2 keeps rows and adds nullable stock columns`() =
        runTest {
            createLegacyDatabase(TEST_DB, version = 1)

            val database = openCurrentDatabase(TEST_DB)
            try {
                val migrated = database.medicationDao().getById(LEGACY_ID)
                assertNotNull(migrated)
                assertEquals("Metformin", migrated?.name)
                assertEquals(MedicationStatus.ACTIVE, migrated?.status)
                assertNull(migrated?.expiryDate)
                assertNull(migrated?.openedAt)
                assertNull(migrated?.remainingDoses)

                // The new columns are usable after the migration.
                database.medicationDao().update(
                    migrated!!.copy(
                        expiryDate = EXPIRY,
                        openedAt = OPENED,
                        remainingDoses = 4,
                    ),
                )
                val updated = database.medicationDao().getById(LEGACY_ID)
                assertEquals(EXPIRY, updated?.expiryDate)
                assertEquals(OPENED, updated?.openedAt)
                assertEquals(4, updated?.remainingDoses)
            } finally {
                database.close()
            }
        }

    @Test
    fun `migration 2 to 3 keeps the entries and adds the health profile`() =
        runTest {
            createLegacyDatabase(TEST_DB, version = 2)

            val database = openCurrentDatabase(TEST_DB)
            try {
                val entry = database.profileEntryDao().getById(LEGACY_ID)
                assertNotNull(entry)
                assertEquals(ProfileEntryType.ALLERGY, entry?.type)
                assertEquals("Penicillin", entry?.label)
                // The new columns are empty for the entries written before.
                assertNull(entry?.severity)
                assertNull(entry?.reaction)
                assertNull(entry?.sinceYear)

                assertNull(database.patientBasicsDao().get())
                val historyFlow = database.medicalHistoryEntryDao().observeAll()
                assertEquals(0, historyFlow.first().size)
                val sectionDao = database.profileSectionStateDao()
                assertNull(sectionDao.getBySection(ProfileSection.ALLERGIES))

                // Every new table is usable after the migration.
                database.patientBasicsDao().upsert(
                    PatientBasicsEntity(
                        birthYear = 1960,
                        sex = Sex.FEMALE,
                        pregnantOrBreastfeeding = null,
                        weightKg = 70,
                    ),
                )
                val historyId =
                    database.medicalHistoryEntryDao().insert(
                        MedicalHistoryEntryEntity(
                            type = MedicalHistoryType.SURGERY,
                            label = "Gallbladder removal",
                            year = 2019,
                            note = null,
                        ),
                    )
                assertEquals(1L, historyId)
                database.profileSectionStateDao().upsert(
                    ProfileSectionStateEntity(
                        section = ProfileSection.CONDITIONS,
                        status = ProfileSectionStatus.NONE_CONFIRMED,
                        confirmedAt = OPENED,
                    ),
                )
                assertEquals(1960, database.patientBasicsDao().get()?.birthYear)
                assertEquals(
                    ProfileSectionStatus.NONE_CONFIRMED,
                    database.profileSectionStateDao().getBySection(ProfileSection.CONDITIONS)?.status,
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun `migration from 1 straight to 3 runs both steps`() =
        runTest {
            createLegacyDatabase(TEST_DB, version = 1)

            val database = openCurrentDatabase(TEST_DB)
            try {
                val medication = database.medicationDao().getById(LEGACY_ID)
                assertEquals("Metformin", medication?.name)
                assertNull(medication?.expiryDate)

                val entry = database.profileEntryDao().getById(LEGACY_ID)
                assertEquals("Penicillin", entry?.label)
                assertNull(entry?.severity)

                assertNull(database.patientBasicsDao().get())
            } finally {
                database.close()
            }
        }

    private fun createLegacyDatabase(
        name: String,
        version: Int,
    ) {
        context.deleteDatabase(name)
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(version) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                schemaStatements(version).forEach(db::execSQL)
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        try {
            helper.writableDatabase.execSQL(
                "INSERT INTO medications " +
                    "(name, form, strength, instructions, source, startDate, endDate, " +
                    "status, stopReason, createdAt) " +
                    "VALUES ('Metformin', 'TABLET', '500 mg', 'With food', 'DOCTOR', " +
                    "1000, NULL, 'ACTIVE', NULL, 999)",
            )
            helper.writableDatabase.execSQL(
                "INSERT INTO profile_entries (type, label, note) " +
                    "VALUES ('ALLERGY', 'Penicillin', NULL)",
            )
        } finally {
            helper.close()
        }
    }

    private fun openCurrentDatabase(name: String): MedCompanionDatabase =
        Room
            .databaseBuilder(context, MedCompanionDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    /** DDL of an exported schema, including the identity hash row. */
    private fun schemaStatements(version: Int): List<String> {
        val file = File(SCHEMA_PATH_TEMPLATE.replace(VERSION_PLACEHOLDER, version.toString()))
        check(file.exists()) { "Exported Room schema not found at ${file.absolutePath}" }
        val database = JSONObject(file.readText()).getJSONObject("database")
        val statements = mutableListOf<String>()
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            statements += entity.getString("createSql").replace("\${TABLE_NAME}", table)
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                statements +=
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("\${TABLE_NAME}", table)
            }
        }
        val setupQueries = database.getJSONArray("setupQueries")
        for (i in 0 until setupQueries.length()) {
            statements += setupQueries.getString(i)
        }
        return statements
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val LEGACY_ID = 1L
        const val VERSION_PLACEHOLDER = "{version}"
        const val SCHEMA_PATH_TEMPLATE =
            "schemas/com.medcompanion.app.data.local.MedCompanionDatabase/$VERSION_PLACEHOLDER.json"
        val EXPIRY: Instant = Instant.parse("2027-03-10T00:00:00Z")
        val OPENED: Instant = Instant.parse("2026-10-17T00:00:00Z")
    }
}
