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

    @Test
    fun conversationsCommand_recoversViaItsEverydayAliases() {
        // El 4o. olvido (compromisos vencidos de chat) vivia fuera del comando
        // rapido: estas busquedas devolvia []. Ahora todas abren Conversaciones.
        val aliases = listOf("conversaciones", "conversacion", "compromisos", "compromiso", "chat", "chats", "mensajes", "mensaje")
        aliases.forEach { query ->
            assertEquals(
                "la consulta '$query' debe abrir Conversaciones",
                CommandPaletteId.CONVERSATIONS,
                CommandPaletteCatalog.search(query).single().id
            )
        }
    }

    @Test
    fun conversationsCommand_normalizesAccentsAndCase() {
        assertEquals(CommandPaletteId.CONVERSATIONS, CommandPaletteCatalog.search("CONVERSACIONES").single().id)
        assertEquals(CommandPaletteId.CONVERSATIONS, CommandPaletteCatalog.search("  Mensajes!  ").single().id)
    }

    @Test
    fun conversationsCommand_isNotFrequent_andDoesNotBreakFrequentOrder() {
        // Sin frequentRank, no aparece en la lista de comandos frecuentes (query vacia),
        // pero SI es navegable al escribir su alias. Conserva el orden de los frecuentes.
        val frequent = CommandPaletteCatalog.search("   ").map { it.id }
        assertEquals(
            listOf(CommandPaletteId.CAPTURE, CommandPaletteId.TODAY, CommandPaletteId.CALENDAR, CommandPaletteId.NOTES, CommandPaletteId.FOCUS),
            frequent
        )
        assertTrue(frequent.none { it == CommandPaletteId.CONVERSATIONS })
    }
}
