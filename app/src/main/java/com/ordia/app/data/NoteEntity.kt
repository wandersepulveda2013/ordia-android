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
        fun preview(text: String): String = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    }
}
