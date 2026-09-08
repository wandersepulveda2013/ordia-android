package com.ordia.app.ui

import com.ordia.app.data.NoteDao
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeNoteRepository
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeNoteRepository(FakeNoteDao())
        viewModel = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save ignores entirely blank new note`() = runTest(testDispatcher) {
        viewModel.save(title = "", content = "   ")
        advanceUntilIdle()
        assertFalse(repo.savedWasCalled)
    }

    @Test
    fun `save creates new note if not blank`() = runTest(testDispatcher) {
        viewModel.save(title = "test", content = "")
        advanceUntilIdle()
        assertTrue(repo.savedWasCalled)
    }

    @Test
    fun `save deletes existing note if updated to entirely blank`() = runTest(testDispatcher) {
        repo.existingNote = NoteEntity(id = 1L, title = "test", content = "test", createdAt = 0, updatedAt = 0)
        viewModel.save(title = "   ", content = "", existingId = 1L)
        advanceUntilIdle()
        assertTrue(repo.deletedWasCalled)
        assertFalse(repo.updatedWasCalled)
    }

    @Test
    fun `save updates existing note if not blank`() = runTest(testDispatcher) {
        repo.existingNote = NoteEntity(id = 1L, title = "test", content = "test", createdAt = 0, updatedAt = 0)
        viewModel.save(title = "test 2", content = "", existingId = 1L)
        advanceUntilIdle()
        assertFalse(repo.deletedWasCalled)
        assertTrue(repo.updatedWasCalled)
    }

    class FakeNoteDao : NoteDao {
        var existingNote: NoteEntity? = null
        var savedWasCalled = false
        var updatedWasCalled = false
        var deletedWasCalled = false

        override fun observeAll() = MutableStateFlow(emptyList<NoteEntity>())
        override suspend fun getById(id: Long) = if (existingNote?.id == id) existingNote else null
        override suspend fun insert(note: NoteEntity): Long {
            savedWasCalled = true
            return 0L
        }
        override suspend fun update(note: NoteEntity) {
            updatedWasCalled = true
        }
        override suspend fun delete(note: NoteEntity) {
            deletedWasCalled = true
        }
        override suspend fun setPinned(id: Long, pinned: Boolean) {}
        override suspend fun clear() {}
    }

    class FakeNoteRepository(val fakeDao: FakeNoteDao) : NoteRepository(fakeDao) {
        var existingNote: NoteEntity?
            get() = fakeDao.existingNote
            set(value) { fakeDao.existingNote = value }

        val savedWasCalled get() = fakeDao.savedWasCalled
        val updatedWasCalled get() = fakeDao.updatedWasCalled
        val deletedWasCalled get() = fakeDao.deletedWasCalled
    }
}
