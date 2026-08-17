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

    @Test fun spansRoundTripPreserveInlineFormatting() {
        val blocks = listOf(
            NoteBlock(
                type = NoteBlockType.PARAGRAPH,
                text = "Hola mundo",
                spans = listOf(
                    NoteSpan(text = "Hola", bold = true, colorHex = "#FF0000"),
                    NoteSpan(text = " ", italic = false),
                    NoteSpan(text = "mundo", underline = true, highlight = true, link = "https://ordia.app")
                )
            )
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(1, decoded.size)
        val spans = decoded[0].spans!!
        assertEquals(3, spans.size)
        assertEquals("Hola", spans[0].text)
        assertTrue(spans[0].bold)
        assertEquals("#FF0000", spans[0].colorHex)
        assertTrue(spans[2].underline)
        assertTrue(spans[2].highlight)
        assertEquals("https://ordia.app", spans[2].link)
    }

    @Test fun tableBlockRoundTripsItsCells() {
        val blocks = listOf(
            NoteBlock(
                type = NoteBlockType.TABLE,
                tableRows = listOf(listOf("A", "B"), listOf("1", "2"))
            )
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(NoteBlockType.TABLE, decoded[0].type)
        assertEquals(listOf(listOf("A", "B"), listOf("1", "2")), decoded[0].tableRows)
    }

    @Test fun tableHeaderFlagRoundTrips() {
        val blocks = listOf(
            NoteBlock(
                type = NoteBlockType.TABLE,
                tableRows = listOf(listOf("A", "B")),
                tableHeader = true
            )
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(NoteBlockType.TABLE, decoded[0].type)
        assertTrue(decoded[0].tableHeader)
    }

    @Test fun headingAndCodeTypesRoundTrip() {
        val blocks = listOf(
            NoteBlock(type = NoteBlockType.HEADING_2, text = "Sección"),
            NoteBlock(type = NoteBlockType.HEADING_3, text = "Sub"),
            NoteBlock(type = NoteBlockType.SUBTITLE, text = "Bajada"),
            NoteBlock(type = NoteBlockType.CODE, text = "val x = 1", language = "kotlin")
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(NoteBlockType.HEADING_2, decoded[0].type)
        assertEquals(NoteBlockType.HEADING_3, decoded[1].type)
        assertEquals(NoteBlockType.SUBTITLE, decoded[2].type)
        assertEquals(NoteBlockType.CODE, decoded[3].type)
        assertEquals("kotlin", decoded[3].language)
    }

    @Test fun paragraphStyleRoundTrips() {
        val blocks = listOf(
            NoteBlock(
                text = "Centrado",
                style = NoteParagraphStyle(align = NoteAlign.CENTER, indent = 2, lineSpacing = 1.5f)
            )
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(NoteAlign.CENTER, decoded[0].style.align)
        assertEquals(2, decoded[0].style.indent)
        assertEquals(1.5f, decoded[0].style.lineSpacing)
    }

    @Test fun attachmentBlockRoundTripsUriAndName() {
        val blocks = listOf(
            NoteBlock(
                type = NoteBlockType.IMAGE,
                attachmentUri = "content://x/1",
                attachmentName = "foto.jpg",
                mimeType = "image/jpeg"
            )
        )

        val decoded = NoteBlockCodec.decode(NoteBlockCodec.encode(blocks))

        assertEquals(NoteBlockType.IMAGE, decoded[0].type)
        assertEquals("content://x/1", decoded[0].attachmentUri)
        assertEquals("foto.jpg", decoded[0].attachmentName)
        assertEquals("image/jpeg", decoded[0].mimeType)
    }

    @Test fun plainTextExtractsFromSpansWhenPresent() {
        val block = NoteBlock(
            text = "HolaMundo",
            spans = listOf(NoteSpan(text = "Hola"), NoteSpan(text = "Mundo"))
        )
        assertEquals("HolaMundo", block.plainText)
    }
}
