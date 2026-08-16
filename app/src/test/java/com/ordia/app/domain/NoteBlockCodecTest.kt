package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBlockCodecTest {
    @Test fun encodeDecodeRoundTripPreservesEveryField() {
        val blocks = listOf(
            NoteBlock(type = NoteBlockType.HEADING, text = "Objetivo"),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "Comprar leche", checked = true),
            NoteBlock(type = NoteBlockType.BULLET, text = "Revisar correo"),
            NoteBlock(type = NoteBlockType.DIVIDER),
            NoteBlock(type = NoteBlockType.QUOTE, text = "El mejor momento para plantar un árbol"),
            NoteBlock(type = NoteBlockType.PARAGRAPH, text = "Texto libre")
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(blocks.size, decoded.size)
        blocks.zip(decoded).forEach { (original, restored) ->
            assertEquals(original.id, restored.id)
            assertEquals(original.type, restored.type)
            assertEquals(original.text, restored.text)
            assertEquals(original.checked, restored.checked)
        }
    }

    @Test fun emptyDataFallsBackToPlainBodyLines() {
        val decoded = NoteBlockCodec.decode("", "Primera línea\nSegunda línea")

        assertEquals(2, decoded.size)
        assertEquals("Primera línea", decoded[0].text)
        assertEquals("Segunda línea", decoded[1].text)
    }

    @Test fun corruptJsonFallsBackWithoutCrashing() {
        val decoded = NoteBlockCodec.decode("{not valid json", "Respaldo legible")

        assertEquals(1, decoded.size)
        assertEquals("Respaldo legible", decoded[0].text)
    }

    @Test fun toPlainTextRendersChecklistMarkers() {
        val text = NoteBlockCodec.toPlainText(listOf(
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "Pendiente", checked = false),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "Hecho", checked = true)
        ))

        assertEquals("[ ] Pendiente\n[x] Hecho", text)
    }

    @Test fun unknownTypeDecodesToParagraphWithoutDroppingText() {
        val encoded = """[{"id":"a1","type":"FUTURE_TYPE","text":"Sigue existiendo","checked":false}]"""

        val decoded = NoteBlockCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[0].type)
        assertEquals("Sigue existiendo", decoded[0].text)
        assertNotEquals("", decoded[0].id)
        assertTrue(decoded[0].id.isNotBlank())
    }
}
