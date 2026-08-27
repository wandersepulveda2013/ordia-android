package com.ordia.app.ui

import androidx.lifecycle.SavedStateHandle
import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    /** Mimics Room semantics: autoGenerate only for id==0, REPLACE on conflict. */
    private class FakeDao : NoteDao {
        val notes = mutableListOf<NoteEntity>()

        override fun observeAll() = flowOf(sorted())
        override fun observeSearch(query: String) =
            flowOf(sorted().filter {
                it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
            })
        override suspend fun getById(id: Long) = notes.find { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val id = if (note.id == 0L) (notes.maxOfOrNull { it.id } ?: 0L) + 1 else note.id
            notes.removeAll { it.id == id }
            notes.add(note.copy(id = id))
            return id
        }
        override suspend fun update(note: NoteEntity) {
            val i = notes.indexOfFirst { it.id == note.id }
            if (i >= 0) notes[i] = note
        }
        override suspend fun delete(note: NoteEntity) { notes.removeIf { it.id == note.id } }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val i = notes.indexOfFirst { it.id == id }
            if (i >= 0) notes[i] = notes[i].copy(pinned = pinned)
        }
        override suspend fun clear() { notes.clear() }
        private fun sorted() = notes.sortedWith(
            compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt },
        )
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeDao
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        dao = FakeDao()
        viewModel = NotepadViewModel(NoteRepository(dao))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun save_createsNewNote() = runTest(dispatcher) {
        viewModel.save("Título", "Contenido")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Título", dao.notes[0].title)
        assertEquals("Contenido", dao.notes[0].content)
    }

    @Test
    fun save_blankNewNote_isNotPersisted() = runTest(dispatcher) {
        viewModel.save("", "")
        viewModel.save("   ", "\n\t")
        advanceUntilIdle()

        assertTrue(dao.notes.isEmpty())
    }

    @Test
    fun save_updatesExisting_preservingCreatedAt() = runTest(dispatcher) {
        viewModel.save("Original", "Cuerpo")
        advanceUntilIdle()
        val id = dao.notes[0].id
        val createdAt = dao.notes[0].createdAt

        viewModel.save("Editado", "Cuerpo nuevo", existingId = id)
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Editado", dao.notes[0].title)
        assertEquals(createdAt, dao.notes[0].createdAt)
    }

    @Test
    fun deleteThenRestore_keepsSameIdAndContent() = runTest(dispatcher) {
        viewModel.save("Nota", "Cuerpo")
        advanceUntilIdle()
        val note = dao.notes[0]

        viewModel.delete(note)
        advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())

        viewModel.restore(note)
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals(note.id, dao.notes[0].id)
        assertEquals(note.title, dao.notes[0].title)
        assertEquals(note.createdAt, dao.notes[0].createdAt)
    }

    @Test
    fun restore_whenOriginalIdReusedByAnotherNote_reinsertsUnderFreshId() = runTest(dispatcher) {
        viewModel.save("Original", "cuerpo")
        advanceUntilIdle()
        val original = dao.notes[0]
        val originalId = original.id

        // Delete, then let a new note take the freed rowid (SQLite id reuse).
        viewModel.delete(original)
        advanceUntilIdle()
        viewModel.save("Reemplazo", "contenido")
        advanceUntilIdle()
        assertEquals(originalId, dao.notes[0].id)

        // Undo must not overwrite the live note.
        viewModel.restore(original)
        advanceUntilIdle()

        val replacement = dao.notes.single { it.title == "Reemplazo" }
        val restored = dao.notes.single { it.title == "Original" }
        assertEquals(originalId, replacement.id)
        assertTrue(restored.id != originalId)
        assertEquals(restored.content, original.content)
    }

    @Test
    fun togglePinned_flipsFlag() = runTest(dispatcher) {
        viewModel.save("A", "")
        advanceUntilIdle()

        viewModel.togglePinned(dao.notes[0])
        advanceUntilIdle()
        assertTrue(dao.notes[0].pinned)

        viewModel.togglePinned(dao.notes[0])
        advanceUntilIdle()
        assertTrue(!dao.notes[0].pinned)
    }

    @Test
    fun save_newNoteWithBlankTitleButContent_inserts() = runTest(dispatcher) {
        viewModel.save("  ", "idea")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("idea", dao.notes[0].content)
    }

    @Test
    fun save_missingNote_doesNotRecreate() = runTest(dispatcher) {
        viewModel.save("Orphan", "contenido", existingId = 99L)
        advanceUntilIdle()

        assertTrue(dao.notes.isEmpty())
        assertNull(dao.getById(99L))
    }

    @Test
    fun autosave_debounced_noNoteBeforeDelay() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("Borrador", "en curso")

        // 800ms debounce: nothing persisted yet.
        advanceTimeBy(NotepadViewModel.AUTOSAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(dao.notes.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, dao.notes.size)
    }

    @Test
    fun autosave_previousEdit_cancelledByRapidTyping() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("v1", "contenido v1")
        viewModel.autosave("v2", "contenido v2 final")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("v2", dao.notes[0].title)
        assertEquals("contenido v2 final", dao.notes[0].content)
    }

    @Test
    fun autosave_thenBackSave_doesNotDuplicateNote() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("Borrador", "cuerpo")
        advanceUntilIdle()
        val autosavedId = dao.notes.single().id

        viewModel.commitDraft("Borrador", "cuerpo editado")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals(autosavedId, dao.notes[0].id)
        assertEquals("cuerpo editado", dao.notes[0].content)
    }

    @Test
    fun autosave_blankNewDraft_createsNothing_onAutosaveOrCommit() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("", "")
        advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())

        viewModel.commitDraft("", "")
        advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())
    }

    @Test
    fun commitDraft_newNoteEndedBlank_deletesGhost() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("título", "contenido")
        advanceUntilIdle()
        assertEquals(1, dao.notes.size)

        // User typed something (autosave created it) then cleared everything.
        viewModel.commitDraft("", "")
        advanceUntilIdle()

        assertTrue(dao.notes.isEmpty())
    }

    @Test
    fun autosave_existingNote_deletedInFlight_notResurrected() = runTest(dispatcher) {
        viewModel.save("Nota", "cuerpo")
        advanceUntilIdle()
        val note = dao.notes.single()
        viewModel.beginDraft(note.id)

        // Delete manually while a debounced autosave write is pending.
        viewModel.delete(note)
        viewModel.autosave("Nota", "cuerpo editado")
        advanceUntilIdle()

        assertTrue(dao.notes.isEmpty())
    }

    @Test
    fun autosave_updatesExisting_draftIdKeptOnLaterBack() = runTest(dispatcher) {
        viewModel.save("Original", "cuerpo")
        advanceUntilIdle()
        val note = dao.notes.single()
        viewModel.beginDraft(note.id)

        viewModel.autosave("Autosaved", "cuerpo autosave")
        advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        assertEquals("Autosaved", dao.notes[0].title)

        viewModel.commitDraft("Final", "cuerpo final")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals(note.id, dao.notes[0].id)
        assertEquals("Final", dao.notes[0].title)
        assertEquals(note.createdAt, dao.notes[0].createdAt)
    }

    // --- Draft session survives configuration change / process death ---

    /**
     * Regression: after an autosave created the note, the editor's LaunchedEffect
     * re-invokes beginDraft(null) on recreation (rotation, process death). That
     * reset the in-flight draft id, so the next keystroke autosaved into a brand
     * new row, leaving a duplicate note behind.
     */
    @Test
    fun beginDraftAgain_resumesLiveDraft_doesNotDuplicateNote() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("Borrador", "primera versión")
        advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        val autosavedId = dao.notes.single().id

        // Mirror of what NotepadApp does when the composition is recreated.
        viewModel.beginDraft(null)
        viewModel.autosave("Borrador", "primera versión + editada")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals(autosavedId, dao.notes[0].id)
        assertEquals("primera versión + editada", dao.notes[0].content)
    }

    /**
     * Regression (process death): the draft id lives only in memory, and a
     * recreated ViewModel starts from a blank draft. Autosaving again then
     * inserts a duplicate row. The draft session must be restored from saved
     * state so the recreated ViewModel keeps updating the original note.
     */
    @Test
    fun processDeath_restoresDraftId_avoidsDuplicateNote() = runTest(dispatcher) {
        // First session: new-note editor autosaves and creates a row.
        val firstVm = NotepadViewModel(NoteRepository(dao))
        firstVm.beginDraft(null)
        firstVm.autosave("Borrador", "antes del reinicio")
        advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        val autosavedId = dao.notes.single().id

        // Saved state shipped across process death: the framework restores the
        // ViewModel's SavedStateHandle with the draft session it had stored.
        val restored = SavedStateHandle(
            mapOf<String, Any>(
                "draftId" to autosavedId,
                "draftWasNew" to true,
            ),
        )
        val secondVm = NotepadViewModel(NoteRepository(dao), restored)
        // NotepadApp recreates the editor with creating=true → beginDraft(null).
        secondVm.beginDraft(null)
        secondVm.autosave("Borrador", "después del reinicio")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals(autosavedId, dao.notes[0].id)
        assertEquals("después del reinicio", dao.notes[0].content)
    }

    /**
     * Regression (fast note switching): commitDraft clears the draft inside the
     * launched coroutine. If the user backs out of one note and immediately
     * opens another, beginDraft(new) must rebind the draft to the new note even
     * though the previous commit coroutine has not run yet — otherwise the new
     * note is edited under a cleared draft and the eventual autosave inserts a
     * duplicate row for it.
     */
    @Test
    fun beginDraft_afterCommitLaunched_switchesDraftToNewNote() = runTest(dispatcher) {
        viewModel.save("Primera", "nota 1")
        viewModel.save("Segunda", "nota 2")
        advanceUntilIdle()
        val firstId = dao.notes.single { it.title == "Primera" }.id
        val secondId = dao.notes.single { it.title == "Segunda" }.id

        viewModel.beginDraft(firstId)
        viewModel.autosave("Primera", "editada en primer editor")
        advanceUntilIdle()

        // Back: commit launched but the coroutine has NOT run yet (StandardTestDispatcher).
        viewModel.commitDraft("Primera", "nota 1 final")

        // Immediately open the second note.
        viewModel.beginDraft(secondId)
        viewModel.autosave("Segunda", "nota 2")
        advanceUntilIdle()

        assertEquals(2, dao.notes.size)
        val first = dao.notes.single { it.id == firstId }
        val second = dao.notes.single { it.id == secondId }
        assertEquals("nota 1 final", first.content)
        assertEquals("nota 2", second.content)
    }

    /**
     * Regression (back + new note in quick succession): after backing out of a
     * committed note the draft session must already be cleared synchronously;
     * tapping "+" and typing must start a brand-new note instead of resuming
     * (or cloning) the previous one.
     */
    @Test
    fun beginDraft_nullAfterCommitLaunched_startsFreshNewNote() = runTest(dispatcher) {
        viewModel.beginDraft(null)
        viewModel.autosave("Borrador", "contenido")
        advanceUntilIdle()
        assertEquals(1, dao.notes.size)

        viewModel.commitDraft("Borrador", "contenido")
        // New-note editor opens before commit coroutine has run.
        viewModel.beginDraft(null)
        viewModel.autosave("Segundo borrador", "nuevo contenido")
        advanceUntilIdle()

        assertEquals(2, dao.notes.size)
        assertEquals(1, dao.notes.count { it.title == "Borrador" })
        val fresh = dao.notes.single { it.title == "Segundo borrador" }
        assertTrue(fresh.id != dao.notes.single { it.title == "Borrador" }.id)
    }

    // --- Search ---

    @Test
    fun search_defaultsToBlankQueryAndAllNotes() = runTest(dispatcher) {
        val job = launch(dispatcher) { viewModel.searchResults.collect {} }
        viewModel.save("Título", "cuerpo")
        viewModel.save("Otro", "contenido")
        advanceUntilIdle()

        assertEquals("", viewModel.searchQuery.value)
        assertEquals(2, viewModel.searchResults.value.size)
        job.cancel()
    }

    @Test
    fun setSearchQuery_filtersResults() = runTest(dispatcher) {
        viewModel.save("Receta de paella", "azafrán")
        viewModel.save("Lista de la compra", "paella congelada")
        viewModel.save("Ideas", "otra cosa")
        advanceUntilIdle()

        // Subscribe first so stateIn(WhileSubscribed) propagates upstream.
        val job = launch(dispatcher) { viewModel.searchResults.collect {} }
        viewModel.setSearchQuery("paella")
        advanceUntilIdle()

        assertEquals(2, viewModel.searchResults.value.size)
        assertTrue(viewModel.searchResults.value.all {
            it.title.contains("paella", ignoreCase = true) || it.content.contains("paella", ignoreCase = true)
        })
        job.cancel()
    }

    @Test
    fun clearingSearch_restoresAllNotes() = runTest(dispatcher) {
        val job = launch(dispatcher) { viewModel.searchResults.collect {} }
        viewModel.save("Receta", "contenido")
        viewModel.save("Lista", "leche")
        advanceUntilIdle()
        assertEquals(2, viewModel.searchResults.value.size)

        viewModel.setSearchQuery("leche")
        advanceUntilIdle()
        assertEquals(1, viewModel.searchResults.value.size)

        viewModel.setSearchQuery("")
        advanceUntilIdle()
        assertEquals(2, viewModel.searchResults.value.size)
        job.cancel()
    }
}
