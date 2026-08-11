package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteBlockCodecTest {

    @Test fun encodeDecodeRoundTripsAllBlockTypes() {
        val original = listOf(
            NoteBlock(type = NoteBlockType.HEADING, text = "Título"),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "comprar", checked = true),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "pendiente", checked = false),
            NoteBlock(type = NoteBlockType.QUOTE, text = "cita"),
            NoteBlock(type = NoteBlockType.BULLET, text = "viñeta"),
            NoteBlock(type = NoteBlockType.NUMBERED, text = "paso"),
            NoteBlock(type = NoteBlockType.DIVIDER),
            NoteBlock(type = NoteBlockType.PARAGRAPH, text = "párrafo")
        )
        val encoded = NoteBlockCodec.encode(original)
        val decoded = NoteBlockCodec.decode(encoded)
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (a, b) ->
            assertEquals(a.type, b.type)
            assertEquals(a.text, b.text)
            assertEquals(a.checked, b.checked)
        }
    }

    @Test fun blankDataFallsBackToBodyLines() {
        val decoded = NoteBlockCodec.decode("", "primera\nsegunda")
        assertEquals(2, decoded.size)
        assertEquals("primera", decoded[0].text)
        assertEquals("segunda", decoded[1].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[0].type)
    }

    @Test fun blankDataWithEmptyBodyReturnsSingleEmptyBlock() {
        val decoded = NoteBlockCodec.decode("", "")
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].text.isEmpty())
    }

    @Test fun corruptedRootElementFallsBackToBody() {
        val decoded = NoteBlockCodec.decode("esto no es json", "cuerpo plano")
        assertEquals(1, decoded.size)
        assertEquals("cuerpo plano", decoded[0].text)
    }

    @Test fun truncatedJsonFallsBackToBody() {
        val decoded = NoteBlockCodec.decode("""[{"id":"a","type":"HE""", "cuerpo")
        assertEquals(1, decoded.size)
        assertEquals("cuerpo", decoded[0].text)
    }

    // ── Regresión P1: un único elemento malformado NO debe perder todos los bloques ──

    @Test fun singleMalformedElementDoesNotDiscardValidBlocks() {
        // Antes del fix, el string "badstring" hacía que TODOS los bloques se perdieran
        // y se devolviera un único párrafo vacío. Ahora se conservan los válidos.
        val mixed = """[
            {"id":"a","type":"HEADING","text":"h1","checked":false},
            "badstring",
            {"id":"c","type":"PARAGRAPH","text":"p1","checked":false}
        ]"""
        val decoded = NoteBlockCodec.decode(mixed)
        assertEquals(2, decoded.size)
        assertEquals(NoteBlockType.HEADING, decoded[0].type)
        assertEquals("h1", decoded[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[1].type)
        assertEquals("p1", decoded[1].text)
    }

    @Test fun allMalformedElementsFallsBackToBody() {
        val allBad = """["uno","dos","tres"]"""
        val decoded = NoteBlockCodec.decode(allBad, "cuerpo de respaldo")
        assertEquals(1, decoded.size)
        assertEquals("cuerpo de respaldo", decoded[0].text)
    }

    @Test fun unknownBlockTypeFallsBackToParagraphKeepingText() {
        // Compatibilidad hacia adelante: un tipo futuro desconocido se degrada a párrafo.
        val future = """[{"id":"a","type":"FUTURE_TYPE","text":"contenido","checked":false}]"""
        val decoded = NoteBlockCodec.decode(future)
        assertEquals(1, decoded.size)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[0].type)
        assertEquals("contenido", decoded[0].text)
    }

    @Test fun missingIdGetsGeneratedUuid() {
        val noId = """[{"type":"HEADING","text":"x","checked":false}]"""
        val decoded = NoteBlockCodec.decode(noId)
        assertEquals(1, decoded.size)
        assertFalse(decoded[0].id.isBlank())
    }

    @Test fun emptyArrayReturnsSingleEmptyBlock() {
        val decoded = NoteBlockCodec.decode("[]", "")
        assertEquals(1, decoded.size)
    }

    @Test fun toPlainTextRendersAllTypes() {
        val blocks = listOf(
            NoteBlock(type = NoteBlockType.HEADING, text = "T"),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "c", checked = true),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "u", checked = false),
            NoteBlock(type = NoteBlockType.QUOTE, text = "q"),
            NoteBlock(type = NoteBlockType.BULLET, text = "b"),
            NoteBlock(type = NoteBlockType.NUMBERED, text = "n"),
            NoteBlock(type = NoteBlockType.DIVIDER),
            NoteBlock(type = NoteBlockType.PARAGRAPH, text = "p")
        )
        val text = NoteBlockCodec.toPlainText(blocks)
        assertTrue(text.contains("T"))
        assertTrue(text.contains("[x] c"))
        assertTrue(text.contains("[ ] u"))
        assertTrue(text.contains("> q"))
        assertTrue(text.contains("• b"))
        assertTrue(text.contains("---"))
    }
}
