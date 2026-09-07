package com.ordia.app.ui

import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import com.ordia.app.data.NoteDao
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class NotepadViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

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
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() { notes.clear() }
    }

    @Test
    fun save_emptyNewNote_isDiscarded() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val vm = NotepadViewModel(repo)

        vm.save("", "   ", null)
        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_emptyExistingNote_isDeleted() = runTest {
        val dao = FakeDao(mutableListOf(NoteEntity(id = 1, title = "A", content = "B", createdAt = 0, updatedAt = 0)))
        val repo = NoteRepository(dao)
        val vm = NotepadViewModel(repo)

        vm.save("  ", "", 1)
        assertEquals(0, dao.notes.size)
    }

    @Test
    fun save_nonEmptyNewNote_isSaved() = runTest {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val vm = NotepadViewModel(repo)

        vm.save("Title", "Content", null)
        assertEquals(1, dao.notes.size)
    }
}
