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
        assertEquals(high, TaskRules.nextBestTask(listOf(normal, high), 100))
    }

    @Test
    fun focusClock_formatsMinutesAndSeconds() {
        assertEquals("25:00", FocusClock.format(1500))
        assertEquals("00:00", FocusClock.format(-2))
    }

    @Test
    fun nextBestTask_ignoresBlockedTasks() {
        val blocking = TaskEntity(id = 1, title = "Blocking task", priority = TaskPriority.HIGH)
        val blocked = TaskEntity(id = 2, title = "Blocked task", priority = TaskPriority.URGENT, blockedBy = 1)
        val normal = TaskEntity(id = 3, title = "Normal task", priority = TaskPriority.NORMAL)
        assertEquals(blocking, TaskRules.nextBestTask(listOf(blocking, blocked, normal), 100))

        val completedBlocking = TaskEntity(id = 1, title = "Blocking task", priority = TaskPriority.HIGH, completed = true)
        assertEquals(blocked, TaskRules.nextBestTask(listOf(completedBlocking, blocked, normal), 100))
    }
}
