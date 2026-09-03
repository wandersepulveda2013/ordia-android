package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotepadViewModel(private val repo: NoteRepository) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(title: String, content: String, existingId: Long? = null) {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()

        // Prevent empty entities from cluttering the database
        if (trimmedTitle.isEmpty() && trimmedContent.isEmpty()) {
            if (existingId != null) {
                // If it exists but is now empty, delete it
                viewModelScope.launch {
                    val current = repo.get(existingId)
                    if (current != null) {
                        repo.delete(current)
                    }
                }
            }
            // Do not save empty new drafts
            return
        }

        viewModelScope.launch {
            if (existingId != null) {
                val current = repo.get(existingId)
                val now = System.currentTimeMillis()
                if (current != null) {
                    repo.update(current.copy(title = trimmedTitle, content = trimmedContent, updatedAt = now))
                }
            } else {
                repo.save(NoteEntity(title = trimmedTitle, content = trimmedContent, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { repo.delete(note) }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch { repo.togglePinned(note.id, !note.pinned) }
    }
}
