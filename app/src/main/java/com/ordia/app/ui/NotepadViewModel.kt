package com.ordia.app.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        const val KEY_SEARCH_QUERY = "searchQuery"
        private const val TAG = "NotepadViewModel"
    }

    /**
     * Snapshot of a final editor commit (back / "Hecho") whose storage write
     * failed after retries. Kept in memory so the typed text is never silently
     * lost (BUG-010): the next draft write (autosave or commit) re-applies
     * pending snapshots first, while a transient storage failure is still fresh..
     */
    private class FinalCommitSnapshot(
        val title: String,
        val content: String,
        val id: Long?,
        val wasNew: Boolean,
    )

    /** One-shot event per failed persistence write (storage full, DB error, …). */
    private val _persistenceError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val persistenceError: SharedFlow<Unit> = _persistenceError.asSharedFlow()

    val notes: StateFlow<List<NoteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow(savedState[KEY_SEARCH_QUERY] ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Notes matching [searchQuery]; equal to [notes] while the query is blank. */
    val searchResults: StateFlow<List<NoteEntity>> =
        _searchQuery
            .flatMapLatest(repo::observeSearch)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) savedState.remove<String>(KEY_SEARCH_QUERY) else savedState[KEY_SEARCH_QUERY] = query
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

    /**
     * Failed final commits awaiting a retry, in FIFO order (oldest first) so a
     * second failure can never mask an older one. Bounded toa few entries:
     * each snapshot is an entire note the user typed — keeping more than a handful
     * would itself be a memory risk..
     */
    private val pendingFinalCommits = ArrayDeque<FinalCommitSnapshot>()

    /**
     * Resilience: a failed persistence write (disk full, DB error…) must never
     * crash the app. The user's text stays in the editor state, the next autosave
     * retries, and the UI gets a non-fatal signal ([persistenceError]). Cancellation
     * is rethrown so cancel () keeps working on in-flight autosaves.
     */
    private fun launchPersist(
        block: suspend () -> Unit,
        onFinalFailure: (() -> Unit)? = null,
    ): Job =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "Persistence write failed", e) }
                _persistenceError.tryEmit(Unit)
                withContext(NonCancellable) {
                    // Retry once after the failure:the underlying storage may have
                    // recovered (e.g., disk space freed),and the text must not be lost
                    // silently. NonCancellable so the retry cannot be cancelled mid-write.

                    // A second consecutive failure must still not crash the ViewModel:
                    // the text stays in the editor state and the next autosave keeps retrying;
                    // keep emitting the non-fatal signal så the UI can warn the user..
                    try {
                        block()
                        runCatching { Log.w(TAG, "Persistence retry succeeded after failure") }
                    } catch (retryE: CancellationException) {
                        throw retryE
                    } catch (retryE: Exception) {
                        runCatching { Log.e(TAG, "Persistence retry failed", retryE) }
                        _persistenceError.tryEmit(Unit)
                        // Let the caller preserve whatever cannot be written now (e.g. a
                        // final editor commit snapshot) so it can be retried later instead
                        // of silently disappearing (BUG-010).
                        onFinalFailure?.invoke()
                    }
                }
            }
        }

    fun save(title: String, content: String, existingId: Long? = null) {
        if (existingId == null && title.isBlank() && content.isBlank()) return
        launchPersist(block ={ doPersist(title, content, existingId) })
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
        autosaveJob = launchPersist(block ={
            delay(AUTOSAVE_DEBOUNCE_MS)
            // Re-apply failed final commits first (BUG-010):an older snapshot must
            // never overwrite newer text, so it lands before the user's current edits..
            retryPendingFinalCommits()
            doPersist(title, content, draftId, bindDraft = true)
        })
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
        val snapshot = FinalCommitSnapshot(title, content, doneId, doneWasNew)

        launchPersist(
            block = {
                // Re-apply older failed commits first so their text lands oldest-first..
                retryPendingFinalCommits()
                doPersistCommit(title, content, doneId, doneWasNew)
            },
            onFinalFailure = { pendingFinalCommits.addLast(snapshot) },
        )
    }

    /**
     * Re-applies failed final commits that could not be written when their editor
     * closed (BUG-010). Runs at the start of every draft write so older snapshots
     * always land before never texts, and a failure here must not abort the current
     * write — the snapshot stays queued for another attempt..
     */
    private suspend fun retryPendingFinalCommits() {
        if (pendingFinalCommits.isEmpty()) return
        for (snapshot in pendingFinalCommits.toList()) {
            try {
                doPersistCommit(snapshot.title, snapshot.content, snapshot.id, snapshot.wasNew)
                // Remove by reference only so a failed tail entry cannot mask successes..
                pendingFinalCommits.remove(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "Pending final commit retry failed", e) }
                _persistenceError.tryEmit(Unit)
                // Keep the snapshot: a later retry (e.g. next autosave/commit) may succeed..
            }
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
        // Skipping the no-change write keeps `updatedAt` honest — merely opening
        // and closing a note must not reorder the list (mirror of saveCurrent).
        if (current.title == title && current.content == content) return
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
        launchPersist(block ={ repo.delete(note) })
    }

    /**
     * Reinserts a previously deleted note. Keeps its original id when that slot
     * is still free; otherwise, if it was reused by another note (SQLite rowid
     * reuse after delete), reinserts under a fresh id so undo never overwrites
     * a live note. When the occupying row is byte-identical to the deleted
     * snapshot (delete write failed or undo raced it before it landed), nothing
     * to restore — reinserting would duplicate the note (BUG-011).
     */
    fun restore(note: NoteEntity) {
        launchPersist(block ={
            val current = repo.get(note.id)
            if (current == null) {
                repo.save(note)
            } else if (current.copy(id = 0L) != note.copy(id = 0L)) {
                repo.save(note.copy(id = 0L))
            }
        })
    }

    fun togglePinned(id: Long) {
        launchPersist(block ={ repo.togglePinned(id) })
    }
}
