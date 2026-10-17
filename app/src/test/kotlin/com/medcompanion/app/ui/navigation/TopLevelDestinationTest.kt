package com.medcompanion.app.ui.navigation

import org.junit.Assert.assertEquals
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
}
