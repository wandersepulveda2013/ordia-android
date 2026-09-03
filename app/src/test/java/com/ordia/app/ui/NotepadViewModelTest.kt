package com.ordia.app.ui

import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import com.ordia.app.data.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private lateinit var fakeDao: FakeNoteDao
    private lateinit var repo: NoteRepository
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeNoteDao()
        repo = NoteRepository(fakeDao)
        viewModel = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save empty note does not insert it`() = runTest(testDispatcher) {
        viewModel.save("", "")
        testScheduler.advanceUntilIdle()

        val notes = fakeDao.observeAllFlow.value
        assertEquals(0, notes.size)
    }

    @Test
    fun `save existing note with empty content deletes it`() = runTest(testDispatcher) {
        // Insert an initial note
        val initialNote = NoteEntity(id = 1L, title = "Title", content = "Content", createdAt = 0L, updatedAt = 0L)
        fakeDao.insert(initialNote)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeDao.observeAllFlow.value.size)

        // Save it with empty content
        viewModel.save("", "", 1L)
        testScheduler.advanceUntilIdle()

        // It should be deleted
        val notes = fakeDao.observeAllFlow.value
        assertEquals(0, notes.size)
        assertNull(fakeDao.getById(1L))
    }
}

class FakeNoteDao : NoteDao {
    val observeAllFlow = MutableStateFlow<List<NoteEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<NoteEntity>> = observeAllFlow

    override suspend fun getById(id: Long): NoteEntity? {
        return observeAllFlow.value.find { it.id == id }
    }

    override suspend fun insert(note: NoteEntity): Long {
        val id = if (note.id == 0L) nextId++ else note.id
        val newNote = note.copy(id = id)
        observeAllFlow.update { it + newNote }
        return id
    }

    override suspend fun update(note: NoteEntity) {
        observeAllFlow.update { list -> list.map { if (it.id == note.id) note else it } }
    }

    override suspend fun delete(note: NoteEntity) {
        observeAllFlow.update { list -> list.filter { it.id != note.id } }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        observeAllFlow.update { list ->
            list.map { if (it.id == id) it.copy(pinned = pinned) else it }
        }
    }

    override suspend fun clear() {
        observeAllFlow.value = emptyList()
    }
}
