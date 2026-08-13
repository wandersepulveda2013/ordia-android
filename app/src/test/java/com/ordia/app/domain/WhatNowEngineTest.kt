package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WhatNowEngineTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun evaluateReturnsNullIfNoPendingTasks() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "A", completed = true),
            TaskEntity(id = 2, title = "B", archived = true),
            TaskEntity(id = 3, title = "C", parentTaskId = 1L)
        )
        val result = WhatNowEngine.evaluate(tasks, now = 1000L, zone = zone)
        assertNull(result)
    }

    @Test
    fun evaluatePrioritizesOverdueTasks() {
        val now = 1000L
        val tasks = listOf(
            TaskEntity(id = 1, title = "Not Due", dueAt = null),
            TaskEntity(id = 2, title = "Due Later", dueAt = 2000L),
            TaskEntity(id = 3, title = "Overdue", dueAt = 500L, durationMinutes = 30)
        )
        val result = WhatNowEngine.evaluate(tasks, now = now, zone = zone)
        assertNotNull(result)
        assertEquals(3L, result!!.taskId)
        assertEquals(true, result.isOverdue)
        assertEquals(30, result.durationMinutes)
    }

    @Test
    fun evaluatePrioritizesUrgentDueToday() {
        // Today is 1970-01-01
        val now = Instant.parse("1970-01-01T12:00:00Z").toEpochMilli()
        val dueTodayMillis = Instant.parse("1970-01-01T15:00:00Z").toEpochMilli()
        val tasks = listOf(
            TaskEntity(id = 1, title = "Normal Today", dueAt = dueTodayMillis, priority = TaskPriority.NORMAL),
            TaskEntity(id = 2, title = "Urgent Today", dueAt = dueTodayMillis, priority = TaskPriority.URGENT)
        )
        val result = WhatNowEngine.evaluate(tasks, now = now, zone = zone)
        assertNotNull(result)
        assertEquals(2L, result!!.taskId)
        assertEquals(false, result.isOverdue)
    }
}
