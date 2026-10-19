package com.medcompanion.app.core.emergency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The emergency check is a keyword safety net for typed text (Arabic and
 * English). It must never miss an urgent term because of diacritics or case,
 * and it must not fire on ordinary symptoms.
 */
class EmergencySymptomsTest {
    @Test
    fun `arabic urgent terms match`() {
        assertTrue(EmergencySymptoms.isEmergencyText("ضيق في التنفس"))
        assertTrue(EmergencySymptoms.isEmergencyText("ألم في الصدر"))
        assertTrue(EmergencySymptoms.isEmergencyText("إغماء"))
        assertTrue(EmergencySymptoms.isEmergencyText("نزيف"))
    }

    @Test
    fun `arabic diacritics and letter variants do not hide a match`() {
        assertTrue(EmergencySymptoms.isEmergencyText("ضِيق في التنفُّس"))
        assertTrue(EmergencySymptoms.isEmergencyText("ألم في الصدر   "))

        assertTrue(EmergencySymptoms.isEmergencyText("الم في الصدر"))
    }

    @Test
    fun `english matches ignore case and spacing`() {
        assertTrue(EmergencySymptoms.isEmergencyText("Chest Pain"))
        assertTrue(EmergencySymptoms.isEmergencyText("  difficulty breathing "))
        assertTrue(EmergencySymptoms.isEmergencyText("I think this is a stroke"))
    }

    @Test
    fun `ordinary symptoms do not trigger the emergency card`() {
        assertFalse(EmergencySymptoms.isEmergencyText("صداع"))
        assertFalse(EmergencySymptoms.isEmergencyText("headache"))
        assertFalse(EmergencySymptoms.isEmergencyText("زكام وسعال"))
        assertFalse(EmergencySymptoms.isEmergencyText(""))
        assertFalse(EmergencySymptoms.isEmergencyText("   "))
    }
}
