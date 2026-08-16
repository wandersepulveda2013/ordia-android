package com.ordia.app.ui

import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdiaUiStateTest {
    @Test
    fun datedLowConfidenceInboxTaskRemainsInReviewQueue() {
        val task = TaskEntity(
            id = 42L,
            title = "Confirmar fecha extraída",
            dueAt = 2_000_000L,
            status = TaskStatus.INBOX
        )

        assertEquals(listOf(task), OrdiaUiState(tasks = listOf(task)).inboxTasks)
    }

    @Test
    fun pendingExcludesCompletedArchivedAndCancelled() {
        val state = OrdiaUiState(tasks = listOf(
            TaskEntity(id = 1L, title = "Pendiente"),
            TaskEntity(id = 2L, title = "Hecha", completed = true),
            TaskEntity(id = 3L, title = "Archivada", archived = true),
            TaskEntity(id = 4L, title = "Cancelada", status = TaskStatus.CANCELLED)
        ))

        assertEquals(1, state.pendingTasks.size)
        assertEquals(1L, state.pendingTasks.single().id)
        assertEquals(4, state.rootTasks.size)
    }

    @Test
    fun subtasksAreNotRootTasksNorPending() {
        val subtask = TaskEntity(id = 2L, title = "Sub", parentTaskId = 1L)
        val state = OrdiaUiState(tasks = listOf(
            TaskEntity(id = 1L, title = "Padre"),
            subtask
        ))

        assertEquals(1, state.rootTasks.size)
        assertEquals(1, state.pendingTasks.size)
        assertEquals(1, state.pendingCount)
        assertEquals(listOf(subtask), state.subtasks(1L))
    }

    @Test
    fun completionRateOnlyCountsRootTasks() {
        val state = OrdiaUiState(tasks = listOf(
            TaskEntity(id = 1L, title = "Hecha", completed = true),
            TaskEntity(id = 2L, title = "Pendiente"),
            TaskEntity(id = 3L, title = "Sub hecha", parentTaskId = 1L, completed = true)
        ))

        assertEquals(50, state.completionRate)
        assertEquals(1, state.completedCount)
    }

    @Test
    fun archivedCountSumsAllArchivedKinds() {
        val state = OrdiaUiState(
            archivedTasks = listOf(TaskEntity(id = 1L, title = "T")),
            archivedProjects = listOf(ProjectEntity(id = 2L, name = "P")),
            archivedNotes = listOf(com.ordia.app.data.local.NoteEntity(id = 3L, title = "N"))
        )

        assertEquals(3, state.archivedCount)
    }
}

