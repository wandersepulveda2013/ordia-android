package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeDao()
    private val repo = NoteRepository(dao)
    private lateinit var vm: NotepadViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveBlankNewNote_skipsInsert() = runTest(dispatcher.scheduler) {
        vm.save("", "", null)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())
        assertTrue(vm.notes.value.isEmpty())
    }

    @Test
    fun saveNewNoteWithWhitespaceOnly_skipsInsert() = runTest(dispatcher.scheduler) {
        vm.save("   ", "\n\t", null)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(dao.notes.isEmpty())
    }

    @Test
    fun saveNewNoteWithTitleOrDefault_inserts() = runTest(dispatcher.scheduler) {
        vm.save("Título", "", null)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, dao.notes.size)
        assertEquals("Título", repo.get(dao.notes.first().id)?.title)
    }

    @Test
    fun saveExistingNoteClearedToBlank_stillUpdates() = runTest(dispatcher.scheduler) {
        vm.save("Antes", "cuerpo", null)
        dispatcher.scheduler.advanceUntilIdle()
        val id = dao.notes.first().id
        vm.save("", "", id)
        dispatcher.scheduler.advanceUntilIdle()
        val updated = repo.get(id)!!
        assertTrue(updated.title.isEmpty())
        assertTrue(updated.content.isEmpty())
        assertEquals(1, dao.notes.size)
    }

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(notes.toList())
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
}