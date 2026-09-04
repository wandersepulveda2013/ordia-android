package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun save_withEmptyContent_discardsNewNote() = runTest(testDispatcher) {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("", "")
        testScheduler.advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_withEmptyContent_deletesExistingNote() = runTest(testDispatcher) {
        val dao = FakeDao(mutableListOf(NoteEntity(id = 1, title = "A", content = "B", createdAt = 0, updatedAt = 0)))
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("   ", "", existingId = 1)
        testScheduler.advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_withValidContent_savesNote() = runTest(testDispatcher) {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("Title", "Content")
        testScheduler.advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Title", dao.notes[0].title)
    }

    @Test
    fun save_withValidContent_updatesExistingNote() = runTest(testDispatcher) {
        val dao = FakeDao(mutableListOf(NoteEntity(id = 1, title = "A", content = "B", createdAt = 0, updatedAt = 0)))
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("Updated", "Text", existingId = 1)
        testScheduler.advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Updated", dao.notes[0].title)
    }
}
