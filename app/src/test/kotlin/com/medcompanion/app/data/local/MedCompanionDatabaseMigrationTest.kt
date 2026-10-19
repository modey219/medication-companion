package com.medcompanion.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.medcompanion.app.domain.model.MedicationStatus
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
 * model). The version 1 database is recreated from `app/schemas/.../1.json`
 * exactly as Room exported it; opening it with the current database class
 * forces Room to run MIGRATION_1_2 and validate the resulting schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class MedCompanionDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `migration 1 to 2 keeps rows and adds nullable stock columns`() =
        runTest {
            createVersionOneDatabase(TEST_DB)

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

    private fun createVersionOneDatabase(name: String) {
        context.deleteDatabase(name)
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                schemaStatements().forEach(db::execSQL)
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
        } finally {
            helper.close()
        }
    }

    private fun openCurrentDatabase(name: String): MedCompanionDatabase =
        Room
            .databaseBuilder(context, MedCompanionDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    /** DDL of the exported version 1 schema, including the identity hash row. */
    private fun schemaStatements(): List<String> {
        val file = File(SCHEMA_PATH)
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
        const val SCHEMA_PATH = "schemas/com.medcompanion.app.data.local.MedCompanionDatabase/1.json"
        val EXPIRY: Instant = Instant.parse("2027-03-10T00:00:00Z")
        val OPENED: Instant = Instant.parse("2026-10-17T00:00:00Z")
    }
}
