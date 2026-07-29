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
}
