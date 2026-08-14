package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class GuardianCoachTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    // now = 12:00 PM
    private val now = DateRules.toEpochMillis(today, LocalTime.NOON, zone)

    @Test
    fun overdueWorkWinsOverEverythingElse() {
        val overdue = TaskEntity(
            id = 1,
            title = "Enviar informe",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val urgentToday = TaskEntity(
            id = 2,
            title = "Llamar proveedor",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(15, 0), zone),
            priority = TaskPriority.URGENT
        )

        val insight = GuardianCoach.insight(listOf(urgentToday, overdue), emptyList(), emptyList(), now, zone)

        assertEquals(1L, insight.taskId)
        assertEquals(GuardianCoach.Tone.GENTLE, insight.tone)
    }

    @Test
    fun dayPlannerSchedulesPendingTaskInCurrentBlock() {
        val task1 = TaskEntity(id = 1, title = "Task 1", durationMinutes = 180, priority = TaskPriority.NORMAL) // 540 -> 720
        val task2 = TaskEntity(id = 2, title = "Task 2", durationMinutes = 60, priority = TaskPriority.NORMAL) // 730 -> 790 (with 10 min break)

        val insight = GuardianCoach.insight(listOf(task1, task2), emptyList(), emptyList(), now, zone)

        assertEquals(2L, insight.taskId)
        assertEquals("SIGUIENTE EN EL PLAN", insight.eyebrow)
    }

    @Test
    fun dayPlannerSchedulesPendingTaskInCurrentBlockActive() {
        val task1 = TaskEntity(id = 1, title = "Task 1", durationMinutes = 180, priority = TaskPriority.NORMAL) // 540 -> 720
        val task2 = TaskEntity(id = 2, title = "Task 2", durationMinutes = 60, priority = TaskPriority.NORMAL) // 730 -> 790

        // Let's say now is 12:30 PM (750 mins)
        val now1230 = DateRules.toEpochMillis(today, LocalTime.of(12, 30), zone)
        val insight = GuardianCoach.insight(listOf(task1, task2), emptyList(), emptyList(), now1230, zone)

        assertEquals(2L, insight.taskId)
        assertEquals("EN ESTE MOMENTO", insight.eyebrow)
        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
    }

    @Test
    fun pendingHabitIsSuggestedWhenTasksAreClear() {
        val habit = HabitEntity(id = 7, title = "Leer diez minutos")
        val insight = GuardianCoach.insight(emptyList(), listOf(habit), emptyList(), now, zone)

        assertEquals("Leer diez minutos", insight.title)
        assertNotNull(insight.message)
    }
}
