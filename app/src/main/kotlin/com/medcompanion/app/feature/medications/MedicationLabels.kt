package com.medcompanion.app.feature.medications

import androidx.annotation.StringRes
import com.medcompanion.app.R
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ScheduleType
import java.time.DayOfWeek

/** Maps enums to localized labels. No medical judgement, labels only. */
@StringRes
fun MedicationForm.labelRes(): Int =
    when (this) {
        MedicationForm.TABLET -> R.string.form_tablet
        MedicationForm.CAPSULE -> R.string.form_capsule
        MedicationForm.SYRUP -> R.string.form_syrup
        MedicationForm.INJECTION -> R.string.form_injection
        MedicationForm.DROPS -> R.string.form_drops
        MedicationForm.CREAM -> R.string.form_cream
        MedicationForm.OTHER -> R.string.form_other
    }

@StringRes
fun MedicationSource.labelRes(): Int =
    when (this) {
        MedicationSource.SELF -> R.string.source_self
        MedicationSource.DOCTOR -> R.string.source_doctor
        MedicationSource.PHARMACY -> R.string.source_pharmacy
    }

@StringRes
fun ScheduleType.labelRes(): Int =
    when (this) {
        ScheduleType.DAILY_TIMES -> R.string.schedule_type_daily_times
        ScheduleType.EVERY_N_HOURS -> R.string.schedule_type_every_n_hours
        ScheduleType.WEEKDAYS -> R.string.schedule_type_weekdays
        ScheduleType.AS_NEEDED -> R.string.schedule_type_as_needed
    }

@StringRes
fun FoodRelation.labelRes(): Int =
    when (this) {
        FoodRelation.ANY -> R.string.food_any
        FoodRelation.BEFORE -> R.string.food_before
        FoodRelation.WITH -> R.string.food_with
        FoodRelation.AFTER -> R.string.food_after
    }

@StringRes
fun DayOfWeek.labelRes(): Int =
    when (this) {
        DayOfWeek.SATURDAY -> R.string.weekday_saturday
        DayOfWeek.SUNDAY -> R.string.weekday_sunday
        DayOfWeek.MONDAY -> R.string.weekday_monday
        DayOfWeek.TUESDAY -> R.string.weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.weekday_thursday
        DayOfWeek.FRIDAY -> R.string.weekday_friday
    }

@StringRes
fun DoseStatus.labelRes(): Int =
    when (this) {
        DoseStatus.PENDING -> R.string.dose_pending
        DoseStatus.TAKEN -> R.string.dose_taken
        DoseStatus.MISSED -> R.string.dose_missed
        DoseStatus.SKIPPED -> R.string.dose_skipped
        DoseStatus.SNOOZED -> R.string.dose_snoozed
    }

@StringRes
fun MedicationStatus.labelRes(): Int =
    when (this) {
        MedicationStatus.ACTIVE -> R.string.medication_status_active
        MedicationStatus.COMPLETED -> R.string.medication_status_completed
        MedicationStatus.STOPPED -> R.string.medication_status_stopped
    }
