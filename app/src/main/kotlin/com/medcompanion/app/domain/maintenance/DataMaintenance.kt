package com.medcompanion.app.domain.maintenance

/**
 * "Delete all my data" (T8a). Implementations remove every health entry the
 * patient entered, cancel the reminders that point at it and reset the
 * settings, while keeping the first-run consent record.
 */
interface DataMaintenance {
    suspend fun deleteAllData()
}
