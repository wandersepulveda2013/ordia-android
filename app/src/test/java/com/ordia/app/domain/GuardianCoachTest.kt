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
    fun pendingHabitIsSuggestedWhenTasksAreClear() {
        val habit = HabitEntity(id = 7, title = "Leer diez minutos")
        val insight = GuardianCoach.insight(emptyList(), listOf(habit), emptyList(), now, zone)

        assertEquals("Leer diez minutos", insight.title)
        assertNotNull(insight.message)
    }

    @Test
    fun `insight recommends overdue task with multiple overdue message`() {
        val overdue1 = TaskEntity(id = 1, title = "T1", dueAt = now - 10000, durationMinutes = 15, status = com.ordia.app.data.local.TaskStatus.PLANNED)
        val overdue2 = TaskEntity(id = 2, title = "T2", dueAt = now - 5000, durationMinutes = 30, status = com.ordia.app.data.local.TaskStatus.PLANNED)

        val insight = GuardianCoach.insight(
            tasks = listOf(overdue1, overdue2),
            habits = emptyList(),
            habitLogs = emptyList(),
            now = now,
            zone = zone
        )

        assertEquals("RECUPERA EL CONTROL", insight.eyebrow)
        assertEquals(1L, insight.taskId) // T1 is older, so it should be prioritized based on TaskRules
        org.junit.Assert.assertTrue(insight.message.contains("15 min"))
        org.junit.Assert.assertTrue(insight.message.lowercase().contains("atrasada"))
        org.junit.Assert.assertTrue(insight.message.contains("2 tareas atrasadas"))
    }

    @Test
    fun `insight recommends urgent task due today`() {
        val urgent = TaskEntity(id = 3, title = "Urgent T", dueAt = now + 10000, durationMinutes = 45, priority = TaskPriority.URGENT, status = com.ordia.app.data.local.TaskStatus.PLANNED)
        val normal = TaskEntity(id = 4, title = "Normal T", dueAt = now + 5000, durationMinutes = 20, priority = TaskPriority.NORMAL, status = com.ordia.app.data.local.TaskStatus.PLANNED)

        val insight = GuardianCoach.insight(
            tasks = listOf(urgent, normal),
            habits = emptyList(),
            habitLogs = emptyList(),
            now = now,
            zone = zone
        )

        assertEquals("PROTEGE TU DÍA", insight.eyebrow)
        assertEquals(3L, insight.taskId)
        org.junit.Assert.assertTrue(insight.message.contains("45 min"))
        org.junit.Assert.assertTrue(insight.message.lowercase().contains("prioridad alta para hoy"))
        org.junit.Assert.assertTrue(insight.message.lowercase().contains("urgente"))
    }

    @Test
    fun `insight recommends next best task due today`() {
        val todayTask = TaskEntity(id = 5, title = "Today T", dueAt = now + 10000, durationMinutes = 10, status = com.ordia.app.data.local.TaskStatus.PLANNED)

        val insight = GuardianCoach.insight(
            tasks = listOf(todayTask),
            habits = emptyList(),
            habitLogs = emptyList(),
            now = now,
            zone = zone
        )

        assertEquals("SIGUIENTE PASO", insight.eyebrow)
        assertEquals(5L, insight.taskId)
        org.junit.Assert.assertTrue(insight.message.contains("10 min"))
        org.junit.Assert.assertTrue(insight.message.lowercase().contains("vence hoy"))
    }
}
