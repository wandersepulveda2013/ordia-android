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
        viewModelScope.launch {
            val isBlank = title.isBlank() && content.isBlank()
            if (existingId != null) {
                val current = repo.get(existingId)
                if (current != null) {
                    if (isBlank) {
                        repo.delete(current)
                    } else {
                        val now = System.currentTimeMillis()
                        repo.update(current.copy(title = title, content = content, updatedAt = now))
                    }
                }
            } else {
                if (!isBlank) {
                    repo.save(NoteEntity(title = title, content = content, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                }
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
