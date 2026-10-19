package com.medcompanion.app.domain.export

import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.ProfileEntry
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.model.SymptomLog
import com.medcompanion.app.domain.settings.AppSettings
import java.time.Instant

/** A medication together with the schedules it is taken on. */
data class MedicationWithSchedules(
    val medication: Medication,
    val schedules: List<Schedule>,
)

/**
 * Everything the patient typed into the app, in one value: what "export my
 * data" writes out (JSON and PDF) and what "delete all my data" removes.
 */
data class FullData(
    val generatedAt: Instant,
    val settings: AppSettings,
    val profileEntries: List<ProfileEntry>,
    val medications: List<MedicationWithSchedules>,
    val doses: List<DoseEvent>,
    val symptoms: List<SymptomLog>,
) {
    val isEmpty: Boolean
        get() =
            profileEntries.isEmpty() &&
                medications.isEmpty() &&
                doses.isEmpty() &&
                symptoms.isEmpty()
}
