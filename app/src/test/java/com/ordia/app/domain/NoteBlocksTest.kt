package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteBlocksTest {

    @Test
    fun decode_invalidJson_returnsFallback() {
        val fallback = "Fallback Text"
        val result = NoteBlockCodec.decode("invalid json data {", fallback)
        assertEquals(1, result.size)
        assertEquals(fallback, result[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, result[0].type)
    }

    @Test
    fun decode_blankJson_returnsFallback() {
        val fallback = "Fallback Text"
        val result = NoteBlockCodec.decode("   ", fallback)
        assertEquals(1, result.size)
        assertEquals(fallback, result[0].text)
        assertEquals(NoteBlockType.PARAGRAPH, result[0].type)
    }
}
