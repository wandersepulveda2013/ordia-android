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

    @Test
    fun fixedTasksAreRespectedAndFlexibleTasksScheduleAroundThem() {
        val flexible = TaskEntity(id = 1, title = "Flexible", durationMinutes = 60)
        // Fixed at 10:00, duration 60 mins -> blocks 10:00 to 11:00
        val fixedTime = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val fixed = TaskEntity(id = 2, title = "Fixed", durationMinutes = 60, dueAt = fixedTime)

        // Plan from 9:30 to 12:00
        val plan = DayPlanner.build(listOf(flexible, fixed), date, 9 * 60 + 30, 12 * 60, breakMinutes = 0, now = now, zone = zone)

        assertEquals(2, plan.blocks.size)

        // The fixed task must be at 10:00 (600 mins)
        val fixedBlock = plan.blocks.first { it.taskId == 2L }
        assertEquals(600, fixedBlock.startMinute)
        assertEquals(660, fixedBlock.endMinute)

        // Flexible task cannot fit before 10:00 (only 30 mins available)
        // It must be scheduled after the fixed task, so starting at 11:00 (660 mins)
        val flexibleBlock = plan.blocks.first { it.taskId == 1L }
        assertEquals(660, flexibleBlock.startMinute)
        assertEquals(720, flexibleBlock.endMinute)
    }
}
