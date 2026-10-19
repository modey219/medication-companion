package com.medcompanion.app.feature.medications.edit

import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.ScheduleType
import com.medcompanion.app.domain.schedule.DraftError
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Immutable UI state for the add/edit medication form. */
data class MedicationEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val medicationId: Long? = null,
    val name: String = "",
    val form: MedicationForm = MedicationForm.TABLET,
    val strength: String = "",
    val instructions: String = "",
    val source: MedicationSource = MedicationSource.SELF,
    // Placeholder until the ViewModel sets clock.today(); LocalDate.of (not
    // LocalDate.EPOCH, which needs API 34 while minSdk is 26).
    val startDate: LocalDate = LocalDate.of(1970, 1, 1),
    val endDate: LocalDate? = null,
    val scheduleType: ScheduleType = ScheduleType.DAILY_TIMES,
    // One sensible default slot so the daily-times form never opens empty;
    // ignored for EVERY_N_HOURS and AS_NEEDED.
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val intervalHours: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    val doseAmount: String = "",
    val foodRelation: FoodRelation = FoodRelation.ANY,
    // Phase 1 stock fields; all optional (null/empty until entered).
    val expiryDate: LocalDate? = null,
    val openedDate: LocalDate? = null,
    val remainingDoses: String = "",
    val errors: Set<DraftError> = emptySet(),
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val isSaved: Boolean = false,
) {
    val isEditing: Boolean get() = medicationId != null

    fun hasError(error: DraftError): Boolean = error in errors
}
