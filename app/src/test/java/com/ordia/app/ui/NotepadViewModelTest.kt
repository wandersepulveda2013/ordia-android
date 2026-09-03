package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.test.advanceUntilIdle

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll(): Flow<List<NoteEntity>> = flowOf(notes.toList())
        override suspend fun getById(id: Long): NoteEntity? = notes.firstOrNull { it.id == id }
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
    fun save_emptyDraft_isNotSaved() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("", "")
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_validDraft_isSaved() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        viewModel.save("Title", "Content")
        advanceUntilIdle()

        assertEquals(1, dao.notes.size)
        assertEquals("Title", dao.notes[0].title)
        assertEquals("Content", dao.notes[0].content)
    }

    @Test
    fun save_existingDraftMadeEmpty_isDeleted() = runTest {
        val note = NoteEntity(id = 1, title = "A", content = "x", createdAt = 1, updatedAt = 2, pinned = false)
        val dao = FakeDao(mutableListOf(note))
        val repo = NoteRepository(dao)
        val viewModel = NotepadViewModel(repo)

        assertEquals(1, dao.notes.size)

        viewModel.save("   ", "", existingId = 1)
        advanceUntilIdle()

        assertEquals(0, dao.notes.size)
        assertNull(dao.notes.firstOrNull { it.id == 1L })
    }
}
