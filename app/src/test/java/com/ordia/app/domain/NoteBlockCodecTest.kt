package com.ordia.app.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.json.JSONArray
import org.json.JSONObject

class NoteBlockCodecTest {

    @Test
    fun testEncodeEmptyList() {
        val blocks = emptyList<NoteBlock>()
        val result = NoteBlockCodec.encode(blocks)
        assertEquals("[]", result)
    }

    @Test
    fun testEncodeMultipleBlocks() {
        val blocks = listOf(
            NoteBlock(id = "1", type = NoteBlockType.PARAGRAPH, text = "Hello", checked = false),
            NoteBlock(id = "2", type = NoteBlockType.CHECKLIST, text = "World", checked = true)
        )
        val result = NoteBlockCodec.encode(blocks)

        // Assert JSON format correctness
        val jsonArray = JSONArray(result)
        assertEquals(2, jsonArray.length())

        val firstBlock = jsonArray.getJSONObject(0)
        assertEquals("1", firstBlock.getString("id"))
        assertEquals("PARAGRAPH", firstBlock.getString("type"))
        assertEquals("Hello", firstBlock.getString("text"))
        assertEquals(false, firstBlock.getBoolean("checked"))

        val secondBlock = jsonArray.getJSONObject(1)
        assertEquals("2", secondBlock.getString("id"))
        assertEquals("CHECKLIST", secondBlock.getString("type"))
        assertEquals("World", secondBlock.getString("text"))
        assertEquals(true, secondBlock.getBoolean("checked"))
    }

    @Test
    fun testEncodeSpecialCharacters() {
        val blocks = listOf(
            NoteBlock(id = "3", type = NoteBlockType.QUOTE, text = "He said, \"Hello\nWorld!\"", checked = false)
        )
        val result = NoteBlockCodec.encode(blocks)

        val jsonArray = JSONArray(result)
        assertEquals(1, jsonArray.length())

        val block = jsonArray.getJSONObject(0)
        assertEquals("3", block.getString("id"))
        assertEquals("QUOTE", block.getString("type"))
        assertEquals("He said, \"Hello\nWorld!\"", block.getString("text"))
        assertEquals(false, block.getBoolean("checked"))
    }
}
