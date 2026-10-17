package com.medcompanion.app.data.local

import androidx.room.TypeConverter
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * Room type converters. Timestamps are stored as UTC epoch milliseconds; small
 * value lists are stored as comma-separated strings. See AGENTS.md > Data model.
 */
class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromLocalTimeList(value: List<LocalTime>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toLocalTimeList(value: String): List<LocalTime> = value.splitToTokens().map(LocalTime::parse)

    @TypeConverter
    fun fromDayOfWeekSet(value: Set<DayOfWeek>): String = value.joinToString(SEPARATOR) { it.name }

    @TypeConverter
    fun toDayOfWeekSet(value: String): Set<DayOfWeek> = value.splitToTokens().map(DayOfWeek::valueOf).toSet()

    @TypeConverter
    fun fromMedicationForm(value: MedicationForm): String = value.name

    @TypeConverter
    fun toMedicationForm(value: String): MedicationForm = MedicationForm.valueOf(value)

    @TypeConverter
    fun fromMedicationSource(value: MedicationSource): String = value.name

    @TypeConverter
    fun toMedicationSource(value: String): MedicationSource = MedicationSource.valueOf(value)

    @TypeConverter
    fun fromMedicationStatus(value: MedicationStatus): String = value.name

    @TypeConverter
    fun toMedicationStatus(value: String): MedicationStatus = MedicationStatus.valueOf(value)

    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun fromFoodRelation(value: FoodRelation): String = value.name

    @TypeConverter
    fun toFoodRelation(value: String): FoodRelation = FoodRelation.valueOf(value)

    @TypeConverter
    fun fromDoseStatus(value: DoseStatus): String = value.name

    @TypeConverter
    fun toDoseStatus(value: String): DoseStatus = DoseStatus.valueOf(value)

    @TypeConverter
    fun fromProfileEntryType(value: ProfileEntryType): String = value.name

    @TypeConverter
    fun toProfileEntryType(value: String): ProfileEntryType = ProfileEntryType.valueOf(value)

    private fun String.splitToTokens(): List<String> = if (isBlank()) emptyList() else split(SEPARATOR)

    private companion object {
        const val SEPARATOR = ","
    }
}
