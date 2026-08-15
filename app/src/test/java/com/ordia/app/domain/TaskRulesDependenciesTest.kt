package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRulesDependenciesTest {

    @Test
    fun nextBestTask_respectsDependencies() {
        val blocker = TaskEntity(id = 1, title = "Blocker", priority = TaskPriority.HIGH)
        val blocked = TaskEntity(id = 2, title = "Blocked", priority = TaskPriority.URGENT, blockedBy = 1)

        // blocked is urgent, but it depends on blocker which is not completed.
        val best = TaskRules.nextBestTask(listOf(blocker, blocked), 100)?.task
        assertEquals(blocker.id, best?.id)
    }

    @Test
    fun nextBestTask_allowsBlockedIfBlockerCompleted() {
        val blocker = TaskEntity(id = 1, title = "Blocker", priority = TaskPriority.HIGH, completed = true)
        val blocked = TaskEntity(id = 2, title = "Blocked", priority = TaskPriority.URGENT, blockedBy = 1)

        // blocker is completed, so blocked can be returned
        val best = TaskRules.nextBestTask(listOf(blocker, blocked), 100)?.task
        assertEquals(blocked.id, best?.id)
    }
}
