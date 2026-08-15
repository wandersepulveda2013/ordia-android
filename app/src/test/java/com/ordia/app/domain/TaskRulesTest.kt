package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskRulesTest {
    private val zone = ZoneId.of("America/Santo_Domingo")

    @Test
    fun dueToday_matchesCalendarDay() {
        val today = LocalDate.of(2026, 7, 29)
        val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val due = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(TaskRules.isDueToday(TaskEntity(title = "Tarea", dueAt = due), now, zone))
    }

    @Test
    fun completedTask_isNotOverdue() {
        val task = TaskEntity(title = "Lista", dueAt = 1, completed = true)
        assertFalse(TaskRules.isOverdue(task, 10))
    }

    @Test
    fun nextBestTask_prefersHighPriority() {
        val normal = TaskEntity(id = 1, title = "Normal", priority = TaskPriority.NORMAL)
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        assertEquals(high, TaskRules.nextBestTask(listOf(normal, high), 100)?.task)
    }

    @Test
    fun nextBestTask_filtersOutBlockedTasks() {
        val blocker = TaskEntity(id = 1, title = "Blocker")
        val blocked = TaskEntity(id = 2, title = "Blocked", blockedBy = 1, priority = TaskPriority.HIGH)
        val other = TaskEntity(id = 3, title = "Other", priority = TaskPriority.NORMAL)

        assertEquals(blocker, TaskRules.nextBestTask(listOf(blocker, blocked, other), 100)?.task)

        val completedBlocker = blocker.copy(completed = true)
        assertEquals(blocked, TaskRules.nextBestTask(listOf(completedBlocker, blocked, other), 100)?.task)
    }

    @Test
    fun focusClock_formatsMinutesAndSeconds() {
        assertEquals("25:00", FocusClock.format(1500))
        assertEquals("00:00", FocusClock.format(-2))
    }
}
