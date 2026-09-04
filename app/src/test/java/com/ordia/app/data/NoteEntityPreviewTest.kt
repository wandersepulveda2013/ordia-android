package com.ordia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NoteEntityPreviewTest {

    @Test
    fun preview_blankText_returnsEmpty() {
        assertEquals("", NoteEntity.preview(""))
        assertEquals("", NoteEntity.preview("   \n\t \n"))
    }

    @Test
    fun preview_takesFirstTwoNonBlankLines() {
        assertEquals(
            "línea 1\nlínea 2",
            NoteEntity.preview("\n\n  línea 1  \n\nlínea 2\nlínea 3"),
        )
    }

    @Test
    fun preview_capsLengthAtMaxChars() {
        val text = "x".repeat(500)
        assertEquals(160, NoteEntity.preview(text).length)
        assertEquals(200, NoteEntity.preview(text, maxChars = 200).length)
    }

    @Test
    fun preview_withinLimit_keepsEmojiIntact() {
        assertEquals("👍", NoteEntity.preview("👍"))
        assertEquals("hola 👍", NoteEntity.preview("hola 👍", maxChars = 10))
    }

    @Test
    fun preview_emojiAtBoundary_doesNotSplitSurrogatePair() {
        // "a" * 159 + "👍": `take(160)` would cut between the surrogate pair
        // and leave a dangling high surrogate (rendered as a broken char).
        val text = "a".repeat(159) + "👍"
        val out = NoteEntity.preview(text, 160)
        // The emoji cannot fit within the 160-char cap, so it is dropped whole
        // rather than split mid-pair (never a lone surrogate in the preview).
        assertEquals("a".repeat(159), out)
        assertFalse(out.endsWith('\ufffd'))
    }

    @Test
    fun safeTakeChars_shortInput_isUnchanged() {
        assertEquals("", NoteEntity.safeTakeChars("", 5))
        assertEquals("abc 👍", NoteEntity.safeTakeChars("abc 👍", 20))
    }

    @Test
    fun preview_singleLongLine_isCapped() {
        val longLine = "palabra ".repeat(100).trimEnd()
        val out = NoteEntity.preview(longLine)
        assertEquals(out.length, 160) // single line too long: capped without second line
        assertEquals(1, out.lineSequence().count { it.isNotBlank() })
    }
}