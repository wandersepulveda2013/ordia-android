package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDao : NoteDao {
        val notes = mutableListOf<NoteEntity>()
        var nextId = 1L

        override fun observeAll(): Flow<List<NoteEntity>> = flowOf(notes)
        override suspend fun getById(id: Long): NoteEntity? = notes.find { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val id = nextId++
            notes.add(note.copy(id = id))
            return id
        }
        override suspend fun update(note: NoteEntity) {
            val idx = notes.indexOfFirst { it.id == note.id }
            if (idx != -1) {
                notes[idx] = note
            }
        }
        override suspend fun delete(note: NoteEntity) {
            notes.removeIf { it.id == note.id }
        }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val idx = notes.indexOfFirst { it.id == id }
            if (idx != -1) {
                notes[idx] = notes[idx].copy(pinned = pinned)
            }
        }
        override suspend fun clear() {
            notes.clear()
        }
    }

    @Test
    fun save_emptyNewNote_doesNotSave() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("", "")
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_newNoteWithContent_savesNote() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("Test", "Content")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Test", dao.notes[0].title)
        assertEquals("Content", dao.notes[0].content)
    }

    @Test
    fun save_existingNote_updatesNote() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        val id = repo.save(NoteEntity(title = "Old", content = "Text", createdAt = 0, updatedAt = 0))

        viewModel.save("New", "Content", id)
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("New", dao.notes[0].title)
        assertEquals("Content", dao.notes[0].content)
    }

    @Test
    fun save_existingNoteBecomesEmpty_deletesNote() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        val id = repo.save(NoteEntity(title = "Old", content = "Text", createdAt = 0, updatedAt = 0))

        viewModel.save("  ", "", id)
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
        assertNull(dao.getById(id))
    }
}
