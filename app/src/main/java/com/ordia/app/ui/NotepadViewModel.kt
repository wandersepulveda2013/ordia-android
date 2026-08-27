package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotepadViewModel(private val repo: NoteRepository) : ViewModel() {
    internal companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }

    val notes: StateFlow<List<NoteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var draftId: Long? = null

    /** True when [draftId] was created fresh during this session (not a pre-existing note). */
    private var draftWasNew = false

    private var autosaveJob: Job? = null

    fun save(title: String, content: String, existingId: Long? = null) {
        if (existingId == null && title.isBlank() && content.isBlank()) return
        viewModelScope.launch { persist(title, content, existingId) }
    }

    /** Call when the editor opens: [existingId] is null while composing a new note. */
    fun beginDraft(existingId: Long?) {
        autosaveJob?.cancel()
        autosaveJob = null
        draftId = existingId
        draftWasNew = existingId == null
    }

    /** Debounced persistence, invoked on every editor change. */
    fun autosave(title: String, content: String) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            persist(title, content, draftId)
        }
    }

    /** Persists the final editor content (back / "Hecho") and clears the draft session. */
    fun commitDraft(title: String, content: String) {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch {
            persist(title, content, draftId, commit = true)
            draftWasNew = false
            draftId = null
        }
    }

    /** Shared persistence under a draft id. Returns null when nothing changed. */
    private suspend fun persist(title: String, content: String, id: Long?, commit: Boolean = false): Long? {
        if (id == null) {
            // Never create a brand-new note that has no content.
            if (title.isBlank() && content.isBlank()) return null
            val newId = repo.create(title, content)
            draftId = newId
            draftWasNew = true
            return newId
        }
        val current = repo.get(id)
        if (current == null) {
            // Target was deleted elsewhere (e.g. while this write was in flight):
            // never resurrect it.
            draftId = null
            draftWasNew = false
            return null
        }
        if (commit && draftWasNew && title.isBlank() && content.isBlank()) {
            // A note created fresh this session ended up blank: drop the ghost.
            repo.delete(current)
            draftId = null
            draftWasNew = false
            return null
        }
        repo.update(current.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
        return id
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { repo.delete(note) }
    }

    /** Reinserts a previously deleted note, keeping its original id. */
    fun restore(note: NoteEntity) {
        viewModelScope.launch { repo.save(note) }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch { repo.togglePinned(note.id, !note.pinned) }
    }
}
