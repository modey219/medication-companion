package com.medcompanion.app.feature.history

import com.medcompanion.app.domain.history.AdherenceSummary
import com.medcompanion.app.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate

/** Windows offered by the History tab; both end on today. */
enum class HistoryPeriod(
    val days: Long,
) {
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

/** Immutable UI state for the History tab (T7). */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val period: HistoryPeriod = HistoryPeriod.LAST_7_DAYS,
    val dayGroups: List<HistoryDayGroup> = emptyList(),
    val summary: AdherenceSummary = AdherenceSummary(),
    val isExporting: Boolean = false,
    val shareUri: String? = null,
    val error: HistoryError? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && dayGroups.isEmpty()
}

/** One day of the timeline; the screen renders [date] as the group header. */
data class HistoryDayGroup(
    val date: LocalDate,
    val entries: List<HistoryEntry>,
)

/** A single event in the timeline. [at] is the time shown and used for ordering. */
sealed interface HistoryEntry {
    val at: Instant

    data class DoseLogged(
        val medicationName: String,
        val doseAmount: String,
        val status: DoseStatus,
        override val at: Instant,
    ) : HistoryEntry

    data class SymptomLogged(
        val name: String,
        val severity: Int,
        val note: String?,
        override val at: Instant,
    ) : HistoryEntry

    data class MedicationStarted(
        val name: String,
        val strength: String,
        override val at: Instant,
    ) : HistoryEntry
}

/** User-visible failures; the screen maps them to strings. */
enum class HistoryError {
    EXPORT_FAILED,
}
