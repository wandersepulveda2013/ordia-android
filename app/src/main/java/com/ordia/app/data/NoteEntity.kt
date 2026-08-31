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
        fun preview(text: String, maxChars: Int = 160): String =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .take(2)
            .joinToString("\n")
            .take(maxChars)
    }
}
