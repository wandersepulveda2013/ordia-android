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

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll() = flowOf(notes.toList())
        override suspend fun getById(id: Long) = notes.firstOrNull { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val nextId = (notes.maxOfOrNull { it.id } ?: 0L) + 1
            val withId = note.copy(id = nextId)
            notes.add(withId)
            return nextId
        }
        override suspend fun update(note: NoteEntity) {
            val idx = notes.indexOfFirst { it.id == note.id }
            if (idx >= 0) notes[idx] = note
        }
        override suspend fun delete(note: NoteEntity) { notes.removeAll { it.id == note.id } }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val idx = notes.indexOfFirst { it.id == id }
            if (idx >= 0) notes[idx] = notes[idx].copy(pinned = pinned)
        }
        override suspend fun clear() { notes.clear() }
    }

    @Test
    fun `save skips empty new note`() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("", "")
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun `save deletes existing note if made empty`() = runTest {
        val dao = FakeDao(
            mutableListOf(
                NoteEntity(id = 1, title = "A", content = "x", createdAt = 1, updatedAt = 2, pinned = false)
            )
        )
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("   ", "", existingId = 1L)
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
        assertNull(dao.notes.firstOrNull { it.id == 1L })
    }

    @Test
    fun `save updates existing note if not empty`() = runTest {
        val dao = FakeDao(
            mutableListOf(
                NoteEntity(id = 1, title = "A", content = "x", createdAt = 1, updatedAt = 2, pinned = false)
            )
        )
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("Updated Title", "Updated Content", existingId = 1L)
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Updated Title", dao.notes.first().title)
        assertEquals("Updated Content", dao.notes.first().content)
    }

    @Test
    fun `save creates new note if not empty`() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("New Title", "New Content")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("New Title", dao.notes.first().title)
        assertEquals("New Content", dao.notes.first().content)
    }
}
