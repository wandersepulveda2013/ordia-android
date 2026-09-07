package com.ordia.app.ui

import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NotepadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: NoteRepository
    private lateinit var viewModel: NotepadViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = mock(NoteRepository::class.java)
        viewModel = NotepadViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_emptyDraft_doesNotSave() = runTest {
        viewModel.save(title = "", content = "", existingId = null)
        advanceUntilIdle()
        verify(repo).observeAll()
        org.mockito.Mockito.verifyNoMoreInteractions(repo)
    }

    @Test
    fun save_blankDraft_doesNotSave() = runTest {
        viewModel.save(title = "   ", content = "  \n  ", existingId = null)
        advanceUntilIdle()
        verify(repo).observeAll()
        org.mockito.Mockito.verifyNoMoreInteractions(repo)
    }

    @Test
    fun save_validDraft_saves() = runTest {
        viewModel.save(title = "Title", content = "Content", existingId = null)
        advanceUntilIdle()
        verify(repo).save(any())
    }

    @Test
    fun save_existingEmptyDraft_deletes() = runTest {
        val existingNote = NoteEntity(id = 1L, title = "Old", content = "Content", createdAt = 0L, updatedAt = 0L)
        `when`(repo.get(1L)).thenReturn(existingNote)

        viewModel.save(title = "", content = "", existingId = 1L)
        advanceUntilIdle()

        verify(repo).get(1L)
        verify(repo).delete(existingNote)
    }

    @Test
    fun save_existingValidDraft_updates() = runTest {
        val existingNote = NoteEntity(id = 1L, title = "Old", content = "Content", createdAt = 0L, updatedAt = 0L)
        `when`(repo.get(1L)).thenReturn(existingNote)

        viewModel.save(title = "New Title", content = "New Content", existingId = 1L)
        advanceUntilIdle()

        verify(repo).get(1L)
        verify(repo).update(any())
    }
}
