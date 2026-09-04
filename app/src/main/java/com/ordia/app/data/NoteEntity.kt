package com.ordia.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single note in the notepad. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false
) {
    companion object {
        /** First two non-blank lines, capped at [maxChars] chars, for list previews. */
        fun preview(text: String, maxChars: Int = 160): String {
            val text = text.lineSequence()
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .take(2)
                .joinToString("\n")
            return safeTakeChars(text, maxChars)
        }

        /**
         * Truncates [text] to at most [maxChars] chars without splitting a
         * UTF-16 surrogate pair (emoji / astral CJKV): `String.take` cuts
         * mid-pair and renders a broken replacement char in the UI.
         */
        internal fun safeTakeChars(text: String, maxChars: Int): String {
            if (text.length <= maxChars) return text
            var end = maxChars
            if (end in 1 until text.length &&
                Character.isHighSurrogate(text[end - 1]) &&
                Character.isLowSurrogate(text[end])
            ) {
                end--
            }
            return text.substring(0, end)
        }
    }
}
