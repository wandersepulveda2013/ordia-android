package com.ordia.app.ui

import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    // Create a dummy DAO to pass to the repository constructor
    private class DummyDao : com.ordia.app.data.NoteDao {
        override fun observeAll(): Flow<List<NoteEntity>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: Long): NoteEntity? = null
        override suspend fun insert(note: NoteEntity): Long = 0L
        override suspend fun update(note: NoteEntity) {}
        override suspend fun delete(note: NoteEntity) {}
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() {}
    }

    private class FakeNoteRepository : NoteRepository(DummyDao()) {
        val notes = mutableListOf<NoteEntity>()
        private val _flow = MutableStateFlow<List<NoteEntity>>(emptyList())

        var deletedNote: NoteEntity? = null
        var savedNote: NoteEntity? = null
        var updatedNote: NoteEntity? = null

        override fun observeAll(): Flow<List<NoteEntity>> = _flow

        override suspend fun get(id: Long): NoteEntity? {
            return notes.find { it.id == id }
        }

        override suspend fun save(note: NoteEntity): Long {
            val newNote = note.copy(id = (notes.size + 1).toLong())
            notes.add(newNote)
            savedNote = newNote
            _flow.value = notes.toList()
            return newNote.id
        }

        override suspend fun update(note: NoteEntity) {
            val index = notes.indexOfFirst { it.id == note.id }
            if (index != -1) {
                notes[index] = note
                updatedNote = note
                _flow.value = notes.toList()
            }
        }

        override suspend fun delete(note: NoteEntity) {
            notes.removeIf { it.id == note.id }
            deletedNote = note
            _flow.value = notes.toList()
        }

        override suspend fun togglePinned(id: Long, pinned: Boolean) {
            val index = notes.indexOfFirst { it.id == id }
            if (index != -1) {
                notes[index] = notes[index].copy(pinned = pinned)
                _flow.value = notes.toList()
            }
        }

    }

    private lateinit var viewModel: NotepadViewModel
    private lateinit var repo: FakeNoteRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeNoteRepository()
        viewModel = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save new blank note does nothing`() = runTest {
        viewModel.save("", "")
        advanceUntilIdle()
        assertTrue(repo.notes.isEmpty())
        assertEquals(null, repo.savedNote)
    }

    @Test
    fun `save existing blank note deletes it`() = runTest {
        val note = NoteEntity(id = 1, title = "Title", content = "Content", createdAt = 0, updatedAt = 0)
        repo.notes.add(note)

        viewModel.save("   ", "", existingId = 1)
        advanceUntilIdle()

        assertTrue(repo.notes.isEmpty())
        assertEquals(note, repo.deletedNote)
    }

    @Test
    fun `save new note with content saves it`() = runTest {
        viewModel.save("Title", "Content")
        advanceUntilIdle()

        assertEquals(1, repo.notes.size)
        assertEquals("Title", repo.savedNote?.title)
        assertEquals("Content", repo.savedNote?.content)
    }

    @Test
    fun `save existing note with content updates it`() = runTest {
        val note = NoteEntity(id = 1, title = "Title", content = "Content", createdAt = 0, updatedAt = 0)
        repo.notes.add(note)

        viewModel.save("New Title", "New Content", existingId = 1)
        advanceUntilIdle()

        assertEquals(1, repo.notes.size)
        assertEquals("New Title", repo.updatedNote?.title)
        assertEquals("New Content", repo.updatedNote?.content)
    }
}
