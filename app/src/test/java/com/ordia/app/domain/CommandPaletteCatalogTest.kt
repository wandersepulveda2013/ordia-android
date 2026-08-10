package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteCatalogTest {
    @Test
    fun emptyQuery_returnsOnlyFrequentCommandsInStableOrder() {
        assertEquals(
            listOf(
                CommandPaletteId.CAPTURE,
                CommandPaletteId.TODAY,
                CommandPaletteId.CALENDAR,
                CommandPaletteId.NOTES,
                CommandPaletteId.FOCUS
            ),
            CommandPaletteCatalog.search("   ").map { it.id }
        )
    }

    @Test
    fun aliases_openTheExpectedRealDestinations() {
        assertEquals(CommandPaletteId.CALENDAR, CommandPaletteCatalog.search("agenda").single().id)
        assertEquals(CommandPaletteId.SETTINGS, CommandPaletteCatalog.search("configuracion").single().id)
        assertEquals(CommandPaletteId.PRIVACY, CommandPaletteCatalog.search("seguridad").single().id)
        assertEquals(CommandPaletteId.INTELLIGENCE, CommandPaletteCatalog.search("ia").single().id)
    }

    @Test
    fun matching_normalizesAccentsCaseAndPunctuation() {
        assertEquals(CommandPaletteId.HABITS, CommandPaletteCatalog.search("  HÁBITOS!!! ").single().id)
        assertEquals(CommandPaletteId.FOCUS, CommandPaletteCatalog.search("CONCENTRACIÓN").single().id)
    }

    @Test
    fun unknownQuery_doesNotInventACommand() {
        assertTrue(CommandPaletteCatalog.search("crear una entidad inexistente").isEmpty())
    }
}
