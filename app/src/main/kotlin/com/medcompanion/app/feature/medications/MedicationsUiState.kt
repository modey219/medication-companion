package com.medcompanion.app.feature.medications

import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Immutable UI state for the My medications tab. */
data class MedicationsUiState(
    val isLoading: Boolean = true,
    val active: List<MedicationListItem> = emptyList(),
    val completed: List<MedicationListItem> = emptyList(),
    val stopped: List<MedicationListItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && active.isEmpty() && completed.isEmpty() && stopped.isEmpty()
}

/** One row in the medications list, with just enough schedule data to summarize. */
data class MedicationListItem(
    val id: Long,
    val name: String,
    val strength: String,
    val form: MedicationForm,
    val status: MedicationStatus,
    val startDate: Instant,
    val endDate: Instant?,
    val stopReason: String?,
    val scheduleType: ScheduleType?,
    val times: List<LocalTime>,
    val intervalHours: Int?,
    val weekdays: Set<DayOfWeek>,
    val foodRelation: FoodRelation,
    // Phase 1 stock data (null until the patient enters it).
    val expiryDate: LocalDate? = null,
    val remainingDoses: Int? = null,
    val isExpired: Boolean = false,
    val isLowStock: Boolean = false,
)
