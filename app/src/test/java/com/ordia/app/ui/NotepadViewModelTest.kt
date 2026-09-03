package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private class FakeNoteDao : NoteDao {
        val notes = MutableStateFlow<List<NoteEntity>>(emptyList())
        val savedNotes = mutableListOf<NoteEntity>()
        val deletedNotes = mutableListOf<NoteEntity>()
        val pinnedToggled = mutableListOf<Pair<Long, Boolean>>()

        override fun observeAll(): Flow<List<NoteEntity>> = notes

        override suspend fun getById(id: Long): NoteEntity? = notes.value.find { it.id == id }

        override suspend fun insert(note: NoteEntity): Long {
            savedNotes.add(note)
            val newList = notes.value.toMutableList()
            newList.add(note.copy(id = (newList.size + 1).toLong()))
            notes.value = newList
            return (newList.size).toLong()
        }

        override suspend fun update(note: NoteEntity) {
            val newList = notes.value.toMutableList()
            val index = newList.indexOfFirst { it.id == note.id }
            if (index != -1) {
                newList[index] = note
            }
            notes.value = newList
        }

        override suspend fun delete(note: NoteEntity) {
            deletedNotes.add(note)
            val newList = notes.value.toMutableList()
            newList.removeIf { it.id == note.id }
            notes.value = newList
        }

        override suspend fun setPinned(id: Long, pinned: Boolean) {
            pinnedToggled.add(id to pinned)
            val newList = notes.value.toMutableList()
            val index = newList.indexOfFirst { it.id == id }
            if (index != -1) {
                newList[index] = newList[index].copy(pinned = pinned)
            }
            notes.value = newList
        }

        override suspend fun clear() {
            notes.value = emptyList()
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeNoteDao
    private lateinit var repository: NoteRepository
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeNoteDao()
        repository = NoteRepository(dao)
        viewModel = NotepadViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_newNote_savesToRepository() = runTest {
        viewModel.save("Test Title", "Test Content")
        advanceUntilIdle()
        assertEquals(1, dao.savedNotes.size)
        assertEquals("Test Title", dao.savedNotes[0].title)
        assertEquals("Test Content", dao.savedNotes[0].content)
    }

    @Test
    fun save_emptyNewNote_doesNotSave() = runTest {
        viewModel.save("", "   ")
        advanceUntilIdle()
        assertEquals(0, dao.savedNotes.size)
    }

    @Test
    fun save_updateExistingNote_updatesRepository() = runTest {
        val note = NoteEntity(title = "Old", content = "Old content", createdAt = 0L, updatedAt = 0L, id = 1L)
        dao.insert(note) // ID becomes 1

        viewModel.save("New Title", "New Content", existingId = 1L)
        advanceUntilIdle()

        val updatedNote = dao.getById(1L)
        assertEquals("New Title", updatedNote?.title)
        assertEquals("New Content", updatedNote?.content)
    }

    @Test
    fun save_emptyExistingNote_deletesFromRepository() = runTest {
        val note = NoteEntity(title = "Old", content = "Old content", createdAt = 0L, updatedAt = 0L, id = 1L)
        dao.insert(note) // ID becomes 1

        viewModel.save("", "", existingId = 1L)
        advanceUntilIdle()

        assertEquals(1, dao.deletedNotes.size)
        assertNull(dao.getById(1L))
    }
}
