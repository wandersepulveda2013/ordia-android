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
    private lateinit var dao: FakeNoteDao
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeNoteDao()
        viewModel = NotepadViewModel(NoteRepository(dao))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save with empty title and empty content discards new note`() = runTest {
        viewModel.save("", "")
        advanceUntilIdle()
        assertEquals(0, dao.notes.value.size)
    }

    @Test
    fun `save with spaces discards new note`() = runTest {
        viewModel.save("   ", "  \n  ")
        advanceUntilIdle()
        assertEquals(0, dao.notes.value.size)
    }

    @Test
    fun `save empty over existing note deletes it`() = runTest {
        dao.insert(NoteEntity(id = 1L, title = "Title", content = "Content", createdAt = 0, updatedAt = 0))
        advanceUntilIdle()
        assertEquals(1, dao.notes.value.size)

        viewModel.save("", "  ", existingId = 1L)
        advanceUntilIdle()
        assertNull(dao.getById(1L))
        assertEquals(0, dao.notes.value.size)
    }

    @Test
    fun `save non-empty string creates or updates`() = runTest {
        viewModel.save("A", "B")
        advanceUntilIdle()
        assertEquals(1, dao.notes.value.size)

        val saved = dao.notes.value.first()
        assertEquals("A", saved.title)
        assertEquals("B", saved.content)

        viewModel.save("C ", " D", existingId = saved.id)
        advanceUntilIdle()

        val updated = dao.notes.value.first()
        assertEquals("C", updated.title)
        assertEquals("D", updated.content)
    }
}

class FakeNoteDao : NoteDao {
    val notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<NoteEntity>> = notes

    override suspend fun getById(id: Long): NoteEntity? = notes.value.find { it.id == id }

    override suspend fun insert(note: NoteEntity): Long {
        val newNote = note.copy(id = nextId++)
        notes.update { it + newNote }
        return newNote.id
    }

    override suspend fun update(note: NoteEntity) {
        notes.update { list -> list.map { if (it.id == note.id) note else it } }
    }

    override suspend fun delete(note: NoteEntity) {
        notes.update { list -> list.filter { it.id != note.id } }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        notes.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    override suspend fun clear() {
        notes.value = emptyList()
    }
}
