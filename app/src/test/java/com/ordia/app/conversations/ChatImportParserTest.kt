package com.ordia.app.conversations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatImportParserTest {
    @Test
    fun parsesWhatsAppTextAndParticipants() {
        val raw = """
            31/07/2026, 8:10 - Ana: Te llamo mañana a las 8
            31/07/2026, 8:12 - Luis: Perfecto
            Continuamos por aquí
        """.trimIndent()

        val preview = ChatImportParser.parse(raw, "Chat.txt")

        assertEquals("Chat", preview.title)
        assertEquals(listOf("Ana", "Luis"), preview.participants)
        assertEquals(2, preview.messages.size)
        assertNotNull(preview.messages.first().timestamp)
        assertTrue(preview.messages.last().text.contains("Continuamos"))
    }

    @Test
    fun parsesTelegramJsonWithoutNetwork() {
        val raw = """{"messages":[{"type":"message","from":"Marta","date_unixtime":"1785500000","text":"Nos vemos el lunes"}]}"""

        val preview = ChatImportParser.parse(raw, "Telegram.json")

        assertEquals("Marta", preview.messages.single().sender)
        assertEquals("Nos vemos el lunes", preview.messages.single().text)
    }

    @Test
    fun detectsStructuredConversationButNotSingleSentence() {
        assertTrue(ChatImportParser.looksLikeConversation("Ana: Hola\nLuis: Hola\nAna: Nos vemos"))
        assertFalse(ChatImportParser.looksLikeConversation("Recuérdame comprar leche mañana"))
    }

    @Test
    fun contentHashIsStableAndChangesWithContent() {
        val first = ChatImportParser.parse("Ana: Hola", "uno.txt")
        val second = ChatImportParser.parse("Ana: Hola", "dos.txt")
        val other = ChatImportParser.parse("Ana: Adiós", "uno.txt")

        assertEquals(first.contentHash, second.contentHash)
        assertFalse(first.contentHash == other.contentHash)
    }

    @Test
    fun parsesIosWhatsAppExportWithSeconds() {
        // iOS exporta con corchetes, segundos (HH:MM:SS) y AM/PM; antes del fix esto
        // no casaba como línea estructurada → se perdía remitente, timestamp y se
        // fundían líneas. DD/MM es el formato de los iPhones en locale es.
        val raw = """
            [31/12/24, 11:30:00 PM] Ana: Hola vamos a vernos
            [01/01/25, 12:05:00 AM] Luis: nos vemos mañana
        """.trimIndent()

        val preview = ChatImportParser.parse(raw, "Chat.txt")

        assertEquals(listOf("Ana", "Luis"), preview.participants)
        assertEquals(2, preview.messages.size)
        assertEquals("Ana", preview.messages[0].sender)
        assertEquals("Luis", preview.messages[1].sender)
        assertEquals("Hola vamos a vernos", preview.messages[0].text)
        assertNotNull("El timestamp iOS AM/PM con segundos debe parsearse", preview.messages[0].timestamp)
        assertNotNull("El timestamp iOS 24h con segundos debe parsearse", preview.messages[1].timestamp)
        assertTrue("El orden cronológico debe preservarse",
            (preview.messages[1].timestamp ?: 0L) >= (preview.messages[0].timestamp ?: 0L))
    }

    @Test
    fun detectsIosWhatsAppExportAsStructured() {
        val raw = """
            [31/12/24, 23:59:59] Ana: cita el viernes
            [01/01/25, 00:05:00] Luis: nos vemos mañana
        """.trimIndent()

        assertTrue(ChatImportParser.looksLikeConversation(raw))
    }
}
