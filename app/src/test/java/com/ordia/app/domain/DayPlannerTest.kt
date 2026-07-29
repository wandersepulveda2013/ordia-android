package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DayPlannerTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 7, 29)
    private val now = DateRules.toEpochMillis(date, LocalTime.of(8, 0), zone)

    @Test
    fun urgentTasksAreScheduledBeforeNormalTasks() {
        val normal = TaskEntity(id = 1, title = "Normal", durationMinutes = 30, priority = TaskPriority.NORMAL)
        val urgent = TaskEntity(id = 2, title = "Urgente", durationMinutes = 30, priority = TaskPriority.URGENT)

        val plan = DayPlanner.build(listOf(normal, urgent), date, 9 * 60, 11 * 60, now = now, zone = zone)

        assertEquals(2L, plan.blocks.first().taskId)
        assertEquals(2, plan.blocks.size)
    }

    @Test
    fun tasksThatDoNotFitAreReported() {
        val tasks = (1L..4L).map { id -> TaskEntity(id = id, title = "Tarea $id", durationMinutes = 60) }

        val plan = DayPlanner.build(tasks, date, 9 * 60, 11 * 60, breakMinutes = 10, now = now, zone = zone)

        assertEquals(1, plan.blocks.size)
        assertEquals(3, plan.unscheduledTaskIds.size)
        assertTrue(plan.remainingMinutes >= 0)
    }
}
