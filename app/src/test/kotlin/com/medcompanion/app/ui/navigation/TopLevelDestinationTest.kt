package com.medcompanion.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `there are exactly five top level destinations`() {
        assertEquals(5, TopLevelDestination.entries.size)
    }

    @Test
    fun `routes are unique`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `unknown route falls back to today`() {
        assertEquals(TopLevelDestination.TODAY, topLevelDestinationFromRoute("does-not-exist"))
    }

    @Test
    fun `null route falls back to today`() {
        assertEquals(TopLevelDestination.TODAY, topLevelDestinationFromRoute(null))
    }

    @Test
    fun `known route maps to its destination`() {
        assertEquals(
            TopLevelDestination.ASSISTANT,
            topLevelDestinationFromRoute(TopLevelDestination.ASSISTANT.route),
        )
    }

    @Test
    fun `nested medication add route keeps medications tab selected`() {
        assertEquals(TopLevelDestination.MEDICATIONS, topLevelDestinationFromRoute(MEDICATION_ADD_ROUTE))
    }

    @Test
    fun `nested medication edit route keeps medications tab selected`() {
        assertEquals(TopLevelDestination.MEDICATIONS, topLevelDestinationFromRoute(medicationEditRoute(42L)))
    }

    @Test
    fun `prefix without slash boundary does not match`() {
        assertEquals(TopLevelDestination.TODAY, topLevelDestinationFromRoute("medicationss"))
    }

    @Test
    fun `medication add and edit routes are edit routes`() {
        assertTrue(isMedicationEditRoute(MEDICATION_ADD_ROUTE))
        assertTrue(isMedicationEditRoute(medicationEditRoute(1L)))
        assertFalse(isMedicationEditRoute(TopLevelDestination.MEDICATIONS.route))
        assertFalse(isMedicationEditRoute(PROFILE_ROUTE))
        assertFalse(isMedicationEditRoute(null))
    }
}
