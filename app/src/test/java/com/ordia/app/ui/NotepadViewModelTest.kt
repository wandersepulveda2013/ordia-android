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

    private lateinit var dao: FakeDao
    private lateinit var repo: NoteRepository
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeDao()
        repo = NoteRepository(dao)
        viewModel = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save new non-empty note inserts into repo`() = runTest {
        viewModel.save("Title", "Content")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Title", dao.notes[0].title)
        assertEquals("Content", dao.notes[0].content)
    }

    @Test
    fun `save new empty note does not insert into repo`() = runTest {
        viewModel.save("", "  ") // blank title and whitespace content
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun `save existing non-empty note updates repo`() = runTest {
        val id = dao.insert(NoteEntity(title = "Old", content = "Old Content", createdAt = 0, updatedAt = 0))

        viewModel.save("New", "New Content", id)
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("New", dao.notes[0].title)
        assertEquals("New Content", dao.notes[0].content)
    }

    @Test
    fun `save existing empty note deletes from repo`() = runTest {
        val id = dao.insert(NoteEntity(title = "Title", content = "Content", createdAt = 0, updatedAt = 0))
        assertEquals(1, dao.notes.size)

        viewModel.save("", "", id)
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }
}