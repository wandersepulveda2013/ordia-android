package com.ordia.app.ui

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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    @Test
    fun `save new blank note does not call repo save`() = runTest {
        val saveCalled = AtomicBoolean(false)
        val dummyRepo = object : DummyNoteRepository() {
            override suspend fun save(note: NoteEntity): Long {
                saveCalled.set(true)
                return 1L
            }
        }
        val viewModel = NotepadViewModel(dummyRepo)

        viewModel.save("", "")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, saveCalled.get())
    }

    @Test
    fun `save existing note with blank content deletes it`() = runTest {
        val existingNote = NoteEntity(id = 1, title = "T", content = "C", createdAt = 0L, updatedAt = 0L)
        val deletedNote = AtomicReference<NoteEntity>(null)
        val updateCalled = AtomicBoolean(false)

        val dummyRepo = object : DummyNoteRepository() {
            override suspend fun get(id: Long): NoteEntity? {
                if (id == 1L) return existingNote
                return null
            }

            override suspend fun delete(note: NoteEntity) {
                deletedNote.set(note)
            }

            override suspend fun update(note: NoteEntity) {
                updateCalled.set(true)
            }
        }
        val viewModel = NotepadViewModel(dummyRepo)

        viewModel.save("   ", "", 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(existingNote, deletedNote.get())
        assertEquals(false, updateCalled.get())
    }

    @Test
    fun `save new note with content calls repo save`() = runTest {
        val saveCalled = AtomicBoolean(false)
        val dummyRepo = object : DummyNoteRepository() {
            override suspend fun save(note: NoteEntity): Long {
                saveCalled.set(true)
                return 1L
            }
        }
        val viewModel = NotepadViewModel(dummyRepo)

        viewModel.save("Title", "Content")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, saveCalled.get())
    }

    @Test
    fun `save existing note with content updates it`() = runTest {
        val existingNote = NoteEntity(id = 1, title = "T", content = "C", createdAt = 0L, updatedAt = 0L)
        val updatedNote = AtomicReference<NoteEntity>(null)
        val deleteCalled = AtomicBoolean(false)

        val dummyRepo = object : DummyNoteRepository() {
            override suspend fun get(id: Long): NoteEntity? {
                if (id == 1L) return existingNote
                return null
            }

            override suspend fun delete(note: NoteEntity) {
                deleteCalled.set(true)
            }

            override suspend fun update(note: NoteEntity) {
                updatedNote.set(note)
            }
        }
        val viewModel = NotepadViewModel(dummyRepo)

        viewModel.save("New Title", "New Content", 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, deleteCalled.get())
        assertEquals("New Title", updatedNote.get()?.title)
        assertEquals("New Content", updatedNote.get()?.content)
    }
}

open class DummyNoteRepository : NoteRepository(
    object : com.ordia.app.data.NoteDao {
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<NoteEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): NoteEntity? = null
        override suspend fun insert(note: NoteEntity): Long = 0L
        override suspend fun update(note: NoteEntity) {}
        override suspend fun delete(note: NoteEntity) {}
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() {}
    }
)
