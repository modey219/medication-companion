package com.medcompanion.app.feature.today

import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import java.time.Instant
import java.time.LocalTime

/** Immutable UI state for the Today tab (T4, T5). */
data class TodayUiState(
    val isLoading: Boolean = true,
    val groups: List<DoseGroup> = emptyList(),
    val hasMedications: Boolean = false,
    val reminderSetupIncomplete: Boolean = false,
    val error: TodayError? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && groups.isEmpty()

    val showAddMedicationCta: Boolean
        get() = isEmpty && !hasMedications
}

/** All doses scheduled at the same local time, sorted ascending by time. */
data class DoseGroup(
    val time: LocalTime,
    val doses: List<DoseRow>,
)

/** One dose row: enough data to render without further queries. */
data class DoseRow(
    val id: Long,
    val medicationId: Long,
    val name: String,
    val strength: String,
    val doseAmount: String,
    val foodRelation: FoodRelation?,
    val status: DoseStatus,
    val scheduledAt: Instant,
    val isOverdue: Boolean,
    val isActionable: Boolean,
    val snoozedUntil: Instant?,
)

/** User-visible failures from a dose action; the screen maps it to a string. */
enum class TodayError {
    UPDATE_FAILED,
}
