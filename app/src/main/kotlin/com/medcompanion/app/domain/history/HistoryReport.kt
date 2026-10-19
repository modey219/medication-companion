package com.medcompanion.app.domain.history

import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.MedicationStatus
import java.time.Instant

/**
 * Counts behind the adherence summary. The History tab and the PDF export
 * share the numbers so both always tell the same story. The wording that
 * presents them is deliberately neutral (AGENTS.md > Localization and UX:
 * never judgmental about missed doses).
 */
data class AdherenceSummary(
    val taken: Int = 0,
    val missed: Int = 0,
    val skipped: Int = 0,
    val pending: Int = 0,
) {
    /** Doses the patient has already answered for. */
    val acted: Int
        get() = taken + missed + skipped

    /**
     * Share of acted doses that were taken, or null while nothing has been
     * acted on yet (no percentage out of nothing).
     */
    val takenPercent: Int?
        get() = if (acted == 0) null else taken * PERCENT / acted

    private companion object {
        const val PERCENT = 100
    }
}

/** One dose line in the export, with the names the patient sees. */
data class DoseRecord(
    val medicationName: String,
    val doseAmount: String,
    val scheduledAt: Instant,
    val status: DoseStatus,
)

data class SymptomRecord(
    val name: String,
    val severity: Int,
    val loggedAt: Instant,
    val note: String?,
)

data class MedicationRecord(
    val name: String,
    val strength: String,
    val startDate: Instant,
    val status: MedicationStatus,
)

/**
 * Everything the PDF export needs, already filtered by the chosen period.
 * Plain data only: rendering and formatting happen in the exporter.
 */
data class HistoryReport(
    val periodStart: Instant,
    val periodEnd: Instant,
    val summary: AdherenceSummary,
    val doses: List<DoseRecord>,
    val symptoms: List<SymptomRecord>,
    val medications: List<MedicationRecord>,
)
