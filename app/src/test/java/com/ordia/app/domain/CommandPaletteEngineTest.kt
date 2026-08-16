package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteEngineTest {

    @Test
    fun blankQueryReturnsAllDefaults() {
        val results = CommandPaletteEngine.search("")
        assertEquals(CommandPaletteEngine.defaults.size, results.size)
    }

    @Test
    fun exactWordMatches() {
        val results = CommandPaletteEngine.search("calendario")
        assertTrue(results.any { it.id == "open_calendar" })
    }

    @Test
    fun fuzzySubsequenceMatches() {
        val results = CommandPaletteEngine.search("cra")
        assertTrue(results.any { it.id == "create_task" || it.id == "create_note" })
    }

    @Test
    fun noMatchReturnsEmpty() {
        val results = CommandPaletteEngine.search("zzzzzz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun prefixMatchesRankHigher() {
        val results = CommandPaletteEngine.search("org")
        // "Organizar hoy" / "Organiza mi semana" start with the query → prefix bonus
        assertTrue(results.first().label.lowercase().startsWith("org"))
    }

    @Test
    fun prefixBonusActuallyApplies() {
        val results = CommandPaletteEngine.search("guard")
        assertEquals("open_guardians", results.first().id)
    }
}
