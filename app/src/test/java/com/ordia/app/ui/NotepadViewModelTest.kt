package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NotepadViewModelTest {

    private class FakeDao : NoteDao {
        val notes = mutableListOf<NoteEntity>()
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
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() {}
    }

    private lateinit var dao: FakeDao
    private lateinit var repo: NoteRepository
    private lateinit var viewModel: NotepadViewModel

    private val testDispatcher = StandardTestDispatcher()

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
    fun save_withEmptyTitleAndContent_doesNotSaveNewNote() = runTest(testDispatcher) {
        viewModel.save(title = "", content = "", existingId = null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_withEmptyTitleAndContent_deletesExistingNote() = runTest(testDispatcher) {
        val existingNote = NoteEntity(id = 1, title = "A", content = "B", createdAt = 0, updatedAt = 0)
        dao.notes.add(existingNote)

        viewModel.save(title = "   ", content = "\n", existingId = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(dao.notes.find { it.id == 1L })
    }

    @Test
    fun save_withNonEmptyContent_savesNewNote() = runTest(testDispatcher) {
        viewModel.save(title = "", content = "Some content", existingId = null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        assertEquals("Some content", dao.notes.first().content)
    }
}
