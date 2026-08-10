package com.ordia.app.ui

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
}
