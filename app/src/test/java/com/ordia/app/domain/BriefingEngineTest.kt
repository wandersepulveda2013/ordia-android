package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingEngineTest {

    private val zone = java.time.ZoneId.systemDefault()
    private val today = java.time.LocalDate.now()
    private val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val yesterday = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun emptyTasksReturnsEmptyBriefing() {
        val b = BriefingEngine.build(emptyList())
        assertTrue(b.isEmpty)
        assertEquals(0, b.importantCount)
    }

    @Test
    fun overdueTasksCounted() {
        val t = TaskEntity(id = 1, title = "x", dueAt = yesterday, status = TaskStatus.PLANNED)
        val b = BriefingEngine.build(listOf(t), now = startToday)
        assertEquals(1, b.overdueCount)
    }

    @Test
    fun todayTasksCountedAsImportant() {
        val t = TaskEntity(id = 1, title = "x", dueAt = startToday + 36_00000, status = TaskStatus.PLANNED, priority = com.ordia.app.data.local.TaskPriority.HIGH)
        val b = BriefingEngine.build(listOf(t), now = startToday)
        assertTrue(b.importantCount >= 1)
    }

    @Test
    fun overloadSuggestionProvided() {
        val tasks = (1..5).map {
            TaskEntity(id = it.toLong(), title = "t$it", dueAt = startToday + it * 36_00000, status = TaskStatus.PLANNED)
        }
        val b = BriefingEngine.build(tasks, now = startToday)
        assertTrue(b.suggestion != null)
    }

    @Test
    fun clearDayHasNoSuggestion() {
        val b = BriefingEngine.build(emptyList())
        assertNull(b.suggestion)
    }
}
