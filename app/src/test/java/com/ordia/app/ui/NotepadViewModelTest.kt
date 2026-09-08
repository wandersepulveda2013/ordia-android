package com.ordia.app.ui

import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import com.ordia.app.data.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
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

    private val dispatcher = StandardTestDispatcher()

    private class FakeDao : NoteDao {
        val saved = mutableListOf<NoteEntity>()
        val deleted = mutableListOf<NoteEntity>()
        val updated = mutableListOf<NoteEntity>()
        var existingNote: NoteEntity? = null

        override fun observeAll(): Flow<List<NoteEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): NoteEntity? = existingNote
        override suspend fun insert(note: NoteEntity): Long {
            saved.add(note)
            return 1L
        }
        override suspend fun update(note: NoteEntity) {
            updated.add(note)
        }
        override suspend fun delete(note: NoteEntity) {
            deleted.add(note)
        }
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() {}
    }

    private val dao = FakeDao()
    private val repo = NoteRepository(dao)
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_inserts_new_note_if_title_provided() = runTest(dispatcher) {
        viewModel.save("Title", "Content", null)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, dao.saved.size)
        assertEquals("Title", dao.saved[0].title)
        assertEquals("Content", dao.saved[0].content)
    }

    @Test
    fun save_discards_new_note_if_blank() = runTest(dispatcher) {
        viewModel.save("  ", "", null)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, dao.saved.size)
    }

    @Test
    fun save_deletes_existing_note_if_blank() = runTest(dispatcher) {
        val existing = NoteEntity(1, "T", "C", 0, 0, false)
        dao.existingNote = existing

        viewModel.save("", "   ", 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, dao.deleted.size)
        assertEquals(existing, dao.deleted[0])
        assertEquals(0, dao.updated.size)
    }
}
