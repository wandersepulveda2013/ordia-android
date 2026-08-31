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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll(): Flow<List<NoteEntity>> = flowOf(notes.toList())
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

    private val testDispatcher = StandardTestDispatcher()
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
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_emptyNewNote_doesNothing() = runTest {
        viewModel.save("", "")
        advanceUntilIdle()
        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_nonEmptyNewNote_savesIt() = runTest {
        viewModel.save("Title", "Content")
        advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        assertEquals("Title", dao.notes.first().title)
    }

    @Test
    fun save_emptyExistingNote_deletesIt() = runTest {
        val note = NoteEntity(id = 1L, title = "Title", content = "Content", createdAt = 0L, updatedAt = 0L)
        dao.notes.add(note)

        viewModel.save("", "", 1L)
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_nonEmptyExistingNote_updatesIt() = runTest {
        val note = NoteEntity(id = 1L, title = "Title", content = "Content", createdAt = 0L, updatedAt = 0L)
        dao.notes.add(note)

        viewModel.save("New Title", "New Content", 1L)
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("New Title", dao.notes.first().title)
        assertEquals("New Content", dao.notes.first().content)
    }
}
