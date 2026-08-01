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
}
