package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDao(val notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll() = flowOf(notes.toList())
        override suspend fun getById(id: Long) = notes.firstOrNull { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val nextId = (notes.maxOfOrNull { it.id } ?: 0L) + 1
            notes.add(note.copy(id = nextId))
            return nextId
        }
        override suspend fun update(note: NoteEntity) {
            val idx = notes.indexOfFirst { it.id == note.id }
            if (idx >= 0) notes[idx] = note
        }
        override suspend fun delete(note: NoteEntity) { notes.removeAll { it.id == note.id } }
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() {}
    }

    @Test
    fun save_newValidNote_inserts() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("Title", "Content", null)

        assertEquals(1, dao.notes.size)
        assertEquals("Title", dao.notes.first().title)
    }

    @Test
    fun save_newEmptyNote_doesNotInsert() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("   ", "", null)

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_existingValidNote_updates() = runTest {
        val original = NoteEntity(id = 1, title = "A", content = "B", createdAt = 100, updatedAt = 100)
        val dao = FakeDao(mutableListOf(original))
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("A2", "B2", 1L)

        assertEquals(1, dao.notes.size)
        assertEquals("A2", dao.notes.first().title)
    }

    @Test
    fun save_existingEmptyNote_deletes() = runTest {
        val original = NoteEntity(id = 1, title = "A", content = "B", createdAt = 100, updatedAt = 100)
        val dao = FakeDao(mutableListOf(original))
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("  ", "", 1L)

        assertEquals(0, dao.notes.size)
    }
}
