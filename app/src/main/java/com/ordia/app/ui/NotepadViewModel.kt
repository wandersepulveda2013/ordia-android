package com.ordia.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModel(
    private val repo: NoteRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    internal companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
        const val KEY_DRAFT_ID = "draftId"
        const val KEY_DRAFT_WAS_NEW = "draftWasNew"
    }

    val notes: StateFlow<List<NoteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Notes matching [searchQuery]; equal to [notes] while the query is blank. */
    val searchResults: StateFlow<List<NoteEntity>> =
        _searchQuery
            .flatMapLatest(repo::observeSearch)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** Restored across config changes and process death (SavedStateHandle). */
    private var draftId: Long?
        get() = savedState[KEY_DRAFT_ID]
        set(value) {
            if (value == null) savedState.remove<Long>(KEY_DRAFT_ID) else savedState[KEY_DRAFT_ID] = value
        }

    /** True when [draftId] was created fresh during this session (not a pre-existing note). */
    private var draftWasNew: Boolean
        get() = savedState[KEY_DRAFT_WAS_NEW] ?: false
        set(value) {
            if (!value) savedState.remove<Boolean>(KEY_DRAFT_WAS_NEW) else savedState[KEY_DRAFT_WAS_NEW] = value
        }

    private var autosaveJob: Job? = null

    fun save(title: String, content: String, existingId: Long? = null) {
        if (existingId == null && title.isBlank() && content.isBlank()) return
        viewModelScope.launch { doPersist(title, content, existingId) }
    }

    /**
     * Call when the editor opens: [existingId] is null while composing a new note.
     *
     * The editor's LaunchedEffect invokes this whenever the screen (re)enters
     * composition (rotation, process death). A draft that is already in flight
     * (an autosave already created the row) must be resumed, never reset, or
     * the next autosave would insert a duplicate note.
     */
    fun beginDraft(existingId: Long?) {
        autosaveJob?.cancel()
        autosaveJob = null
        // Only a null id can be resuming an in-flight new-note draft; an explicit
        // note id always rebinds the session (also while a previous commit is still
        // running — see commitDraft).
        if (existingId == null && draftId != null) return
        draftId = existingId
        draftWasNew = existingId == null
    }

    /** Debounced persistence, invoked on every editor change. */
    fun autosave(title: String, content: String) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            doPersist(title, content, draftId, bindDraft = true)
        }
    }

    /**
     * Persists the final editor content (back / "Hecho") and clears the draft
     * session. The session is cleared synchronously so navigation away from the
     * note is immediately safe: a write that is still completing applies only to
     * the snapshot taken here and can never leak into a subsequently opened note.
     */
    fun commitDraft(title: String, content: String) {
        autosaveJob?.cancel()
        autosaveJob = null
        val doneId = draftId
        val doneWasNew = draftWasNew
        draftId = null
        draftWasNew = false
        viewModelScope.launch {
            doPersistCommit(title, content, doneId, doneWasNew)
        }
    }

    /** Shared persistence under a draft id. Returns null when nothing changed. */
    private suspend fun doPersist(
        title: String,
        content: String,
        id: Long?,
        bindDraft: Boolean = false,
    ): Long? {
        if (id == null) {
            // Never create a brand-new note that has no content.
            if (title.isBlank() && content.isBlank()) return null
            val newId = repo.create(title, content)
            if (bindDraft) {
                draftId = newId
                draftWasNew = true
            }
            return newId
        }
        if (saveCurrent(id, title, content)) return id
        return null
    }

    /** Applies final-content semantics (ghost cleanup) then persists. */
    private suspend fun doPersistCommit(title: String, content: String, doneId: Long?, doneWasNew: Boolean) {
        if (doneId == null) {
            if (title.isBlank() && content.isBlank()) return
            repo.create(title, content)
            return
        }
        val current = repo.get(doneId)
        if (current == null) return // Deleted elsewhere while the write was in flight.
        if (doneWasNew && title.isBlank() && content.isBlank()) {
            repo.delete(current) // Fresh note ended up blank: drop the ghost.
            return
        }
        repo.update(current.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
    }

    /** Returns true when the note was updated, false when it no longer exists. */
    private suspend fun saveCurrent(id: Long, title: String, content: String): Boolean {
        val current = repo.get(id)
        if (current == null) {
            // Target was deleted elsewhere (e.g. while this write was in flight):
            // never resurrect it.
            return false
        }
        if (current.title == title && current.content == content) return true
        repo.update(current.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
        return true
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { repo.delete(note) }
    }

    /**
     * Reinserts a previously deleted note. Keeps its original id when that slot
     * is still free; otherwise, if it was reused by another note (SQLite rowid
     * reuse after delete), reinserts under a fresh id so undo never overwrites
     * a live note.
     */
    fun restore(note: NoteEntity) {
        viewModelScope.launch {
            val free = repo.get(note.id) == null
            repo.save(if (free) note else note.copy(id = 0L))
        }
    }

    fun togglePinned(id: Long) {
        viewModelScope.launch { repo.togglePinned(id) }
    }
}
