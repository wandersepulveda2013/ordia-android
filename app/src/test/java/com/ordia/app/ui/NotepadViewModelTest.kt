package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
}
