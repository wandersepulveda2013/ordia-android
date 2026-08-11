package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NoteBlockCodecTest {

    @Test
    fun test_encode() {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        val blocks = listOf(
            NoteBlock(id = id1, type = NoteBlockType.PARAGRAPH, text = "Hello", checked = false),
            NoteBlock(id = id2, type = NoteBlockType.CHECKLIST, text = "Task", checked = true)
        )
        val encoded = NoteBlockCodec.encode(blocks)

        val decoded = NoteBlockCodec.decode(encoded)
        assertEquals(blocks, decoded)

        assertTrue(encoded.contains("\"type\":\"PARAGRAPH\""))
        assertTrue(encoded.contains("\"type\":\"CHECKLIST\""))
        assertTrue(encoded.contains("\"checked\":true"))
        assertTrue(encoded.contains("\"checked\":false"))
        assertTrue(encoded.contains(id1))
        assertTrue(encoded.contains(id2))
    }

    @Test
    fun test_decode_happy_path() {
        val json = """[{"id":"123","type":"HEADING","text":"Title","checked":false}]"""
        val decoded = NoteBlockCodec.decode(json)

        assertEquals(1, decoded.size)
        assertEquals("123", decoded[0].id)
        assertEquals(NoteBlockType.HEADING, decoded[0].type)
        assertEquals("Title", decoded[0].text)
        assertEquals(false, decoded[0].checked)
    }

    @Test
    fun test_decode_empty_string() {
        val emptyString = ""
        val fallbackBody = "Line 1\nLine 2"
        val decoded = NoteBlockCodec.decode(emptyString, fallbackBody)

        assertEquals(2, decoded.size)
        assertEquals("Line 1", decoded[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[0].type)
        assertEquals("Line 2", decoded[1].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[1].type)

        val emptyString2 = ""
        val decoded2 = NoteBlockCodec.decode(emptyString2)
        assertEquals(1, decoded2.size)
        assertEquals("", decoded2[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded2[0].type)
    }

    @Test
    fun test_decode_invalid_json() {
        val invalidJson = "this is not json"
        val fallbackBody = "Fallback text"

        val decoded = NoteBlockCodec.decode(invalidJson, fallbackBody)

        assertEquals(1, decoded.size)
        assertEquals("Fallback text", decoded[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[0].type)
    }

    @Test
    fun test_decode_missing_fields() {
        val jsonMissingFields = """[{"text":"Just text"}]"""
        val decoded = NoteBlockCodec.decode(jsonMissingFields)

        assertEquals(1, decoded.size)
        assertEquals("Just text", decoded[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, decoded[0].type) // Default type
        assertEquals(false, decoded[0].checked) // Default checked
        assertTrue(decoded[0].id.isNotBlank()) // UUID generated
    }

    @Test
    fun test_to_plain_text() {
        val blocks = listOf(
            NoteBlock(type = NoteBlockType.HEADING, text = "Heading"),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "Done task", checked = true),
            NoteBlock(type = NoteBlockType.CHECKLIST, text = "Pending task", checked = false),
            NoteBlock(type = NoteBlockType.QUOTE, text = "Quote text"),
            NoteBlock(type = NoteBlockType.BULLET, text = "Bullet item"),
            NoteBlock(type = NoteBlockType.NUMBERED, text = "1. Numbered item"),
            NoteBlock(type = NoteBlockType.DIVIDER, text = ""),
            NoteBlock(type = NoteBlockType.PARAGRAPH, text = "Just a paragraph")
        )

        val plainText = NoteBlockCodec.toPlainText(blocks)

        val expected = """
            Heading
            [x] Done task
            [ ] Pending task
            > Quote text
            • Bullet item
            1. Numbered item
            ---
            Just a paragraph
        """.trimIndent()

        assertEquals(expected, plainText)
    }
}
