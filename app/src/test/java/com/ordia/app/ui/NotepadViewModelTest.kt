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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll() = flowOf(notes.toList())
        override suspend fun getById(id: Long) = notes.firstOrNull { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val nextId = (notes.maxOfOrNull { it.id } ?: 0L) + 1
            notes.add(note.copy(id = nextId))
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

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(dao: FakeDao) = NotepadViewModel(NoteRepository(dao))

    @Test
    fun save_blankNewNote_doesNotPersistGhostNote() = runTest {
        val dao = FakeDao()
        val vm = viewModel(dao)
        vm.save("", "", null)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())
    }

    @Test
    fun save_newNoteWithContent_inserts() = runTest {
        val dao = FakeDao()
        val vm = viewModel(dao)
        vm.save("Lista", "leche", null)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        assertEquals("Lista", dao.notes.first().title)
        assertEquals("leche", dao.notes.first().content)
    }

    @Test
    fun save_newNoteWithOnlyBlankTitleButContent_inserts() = runTest {
        val dao = FakeDao()
        val vm = viewModel(dao)
        vm.save("  ", "idea", null)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        assertEquals("idea", dao.notes.first().content)
    }

    @Test
    fun save_existingNote_updatesAndBumpsUpdatedAt() = runTest {
        val dao = FakeDao(
            mutableListOf(
                NoteEntity(id = 1, title = "Viejo", content = "c", createdAt = 10, updatedAt = 20)
            )
        )
        val vm = viewModel(dao)
        val before = System.currentTimeMillis()
        vm.save("Nuevo", "c", 1)
        mainDispatcher.scheduler.advanceUntilIdle()
        val note = dao.getById(1)
        assertNotNull(note)
        assertEquals("Nuevo", note!!.title)
        assertEquals(10L, note.createdAt)
        assertTrue(note.updatedAt >= before)
    }

    @Test
    fun save_missingNote_doesNotRecreateAfterDelete() = runTest {
        val dao = FakeDao()
        val vm = viewModel(dao)
        vm.save("Orphan", "contenido", 99L)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())
        assertNull(dao.getById(99L))
    }
}
