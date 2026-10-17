package com.medcompanion.app.data.local

import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.FoodRelation
import com.medcompanion.app.domain.model.MedicationForm
import com.medcompanion.app.domain.model.MedicationSource
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.ProfileEntryType
import com.medcompanion.app.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `instant round trips through epoch millis`() {
        val instant = Instant.parse("2026-10-17T08:30:00Z")

        val stored = converters.fromInstant(instant)

        assertEquals(instant.toEpochMilli(), stored)
        assertEquals(instant, converters.toInstant(stored))
    }

    @Test
    fun `null instant round trips`() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }

    @Test
    fun `local time list round trips`() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 30))

        val stored = converters.fromLocalTimeList(times)

        assertEquals("08:00,20:30", stored)
        assertEquals(times, converters.toLocalTimeList(stored))
    }

    @Test
    fun `empty local time list serializes to blank and back`() {
        assertEquals("", converters.fromLocalTimeList(emptyList()))
        assertEquals(emptyList<LocalTime>(), converters.toLocalTimeList(""))
    }

    @Test
    fun `day of week set round trips`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

        val stored = converters.fromDayOfWeekSet(days)

        assertEquals(days, converters.toDayOfWeekSet(stored))
    }

    @Test
    fun `empty day of week set round trips`() {
        assertEquals("", converters.fromDayOfWeekSet(emptySet()))
        assertEquals(emptySet<DayOfWeek>(), converters.toDayOfWeekSet(""))
    }

    @Test
    fun `enum converters round trip`() {
        assertEquals(MedicationForm.SYRUP, converters.toMedicationForm("SYRUP"))
        assertEquals("SYRUP", converters.fromMedicationForm(MedicationForm.SYRUP))

        assertEquals(MedicationSource.DOCTOR, converters.toMedicationSource("DOCTOR"))
        assertEquals(MedicationStatus.STOPPED, converters.toMedicationStatus("STOPPED"))
        assertEquals(ScheduleType.WEEKDAYS, converters.toScheduleType("WEEKDAYS"))
        assertEquals(FoodRelation.AFTER, converters.toFoodRelation("AFTER"))
        assertEquals(DoseStatus.SNOOZED, converters.toDoseStatus("SNOOZED"))
        assertEquals(ProfileEntryType.ALLERGY, converters.toProfileEntryType("ALLERGY"))
    }
}
