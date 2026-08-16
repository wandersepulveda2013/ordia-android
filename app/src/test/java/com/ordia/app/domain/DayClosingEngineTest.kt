package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayClosingEngineTest {

    private val zone = java.time.ZoneId.systemDefault()
    private val startToday = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun emptyTasksReturnsEmpty() {
        assertTrue(DayClosingEngine.build(emptyList()).isEmpty)
    }

    @Test
    fun completedTasksExcluded() {
        val t = TaskEntity(id = 1, title = "x", completed = true, status = TaskStatus.COMPLETED)
        assertTrue(DayClosingEngine.build(listOf(t)).isEmpty)
    }

    @Test
    fun plannedTodayIncluded() {
        val t = TaskEntity(id = 1, title = "x", dueAt = startToday + 36_00000, status = TaskStatus.PLANNED)
        val report = DayClosingEngine.build(listOf(t), now = startToday + 72_00000)
        assertEquals(1, report.count)
    }

    @Test
    fun archivedExcluded() {
        val t = TaskEntity(id = 1, title = "x", status = TaskStatus.PLANNED, archived = true)
        assertTrue(DayClosingEngine.build(listOf(t)).isEmpty)
    }

    @Test
    fun sortedByDueDateNullsLast() {
        val a = TaskEntity(id = 1, title = "no date", status = TaskStatus.PLANNED, dueAt = null)
        val b = TaskEntity(id = 2, title = "with date", status = TaskStatus.PLANNED, dueAt = startToday + 1000)
        val report = DayClosingEngine.build(listOf(a, b))
        assertEquals(2L, report.remaining.first().id)
    }
}
