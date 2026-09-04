package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private lateinit var fakeDao: FakeDao
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeDao()
        viewModel = NotepadViewModel(NoteRepository(fakeDao))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save with non-blank new note inserts note`() = runTest {
        viewModel.save("Title", "Content")
        advanceUntilIdle()

        assertEquals(1, fakeDao.notes.size)
        assertEquals("Title", fakeDao.notes.first().title)
    }

    @Test
    fun `save with blank new note does nothing`() = runTest {
        viewModel.save("", "")
        advanceUntilIdle()

        assertEquals(0, fakeDao.notes.size)
    }

    @Test
    fun `save with blank existing note deletes note`() = runTest {
        val note = NoteEntity(id = 1L, title = "Title", content = "Content", createdAt = 0, updatedAt = 0)
        fakeDao.notes.add(note)

        viewModel.save("", "", 1L)
        advanceUntilIdle()

        assertEquals(0, fakeDao.notes.size)
    }

    @Test
    fun `save with non-blank existing note updates note`() = runTest {
        val note = NoteEntity(id = 1L, title = "Title", content = "Content", createdAt = 0, updatedAt = 0)
        fakeDao.notes.add(note)

        viewModel.save("New Title", "New Content", 1L)
        advanceUntilIdle()

        assertEquals(1, fakeDao.notes.size)
        assertEquals("New Title", fakeDao.notes.first().title)
    }

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        val flow = MutableStateFlow(notes.toList())
        override fun observeAll(): Flow<List<NoteEntity>> = flow
        override suspend fun getById(id: Long) = notes.firstOrNull { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val nextId = (notes.maxOfOrNull { it.id } ?: 0L) + 1
            val withId = if (note.id == 0L) note.copy(id = nextId) else note
            notes.add(withId)
            flow.value = notes.toList()
            return nextId
        }
        override suspend fun update(note: NoteEntity) {
            val idx = notes.indexOfFirst { it.id == note.id }
            if (idx >= 0) notes[idx] = note
            flow.value = notes.toList()
        }
        override suspend fun delete(note: NoteEntity) {
            notes.removeAll { it.id == note.id }
            flow.value = notes.toList()
        }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val idx = notes.indexOfFirst { it.id == id }
            if (idx >= 0) notes[idx] = notes[idx].copy(pinned = pinned)
            flow.value = notes.toList()
        }
        override suspend fun clear() {
            notes.clear()
            flow.value = notes.toList()
        }
    }
}
