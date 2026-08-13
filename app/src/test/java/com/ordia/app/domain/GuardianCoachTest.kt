package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun mildlyOverdueSameDayStaysGentle() {
        val lateToday = TaskEntity(
            id = 1,
            title = "Llamar al dentista",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(8, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(lateToday), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.GENTLE, insight.tone)
        assertEquals(1L, insight.taskId)
    }

    @Test
    fun forgottenOverdueTaskBecomesFocusedAndSurfacesAge() {
        val forgotten = TaskEntity(
            id = 1,
            title = "Enviar propuesta",
            dueAt = DateRules.toEpochMillis(today.minusDays(4), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(forgotten), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertEquals(1L, insight.taskId)
        assertTrue(insight.message.contains("4 días"))
    }

    @Test
    fun forgottenOverdueUsesWeeksLabelPastSevenDays() {
        val forgotten = TaskEntity(
            id = 1,
            title = "Renovar póliza",
            dueAt = DateRules.toEpochMillis(today.minusDays(14), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val insight = GuardianCoach.insight(listOf(forgotten), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        assertTrue(insight.message.contains("2 semanas"))
    }

    @Test
    fun forgottenOverdueGroupSurfacesOldestAge() {
        val oldest = TaskEntity(
            id = 1,
            title = "Tarea muy atrasada",
            dueAt = DateRules.toEpochMillis(today.minusDays(3), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val recent = TaskEntity(
            id = 2,
            title = "Tarea recién atrasada",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.URGENT
        )
        val insight = GuardianCoach.insight(listOf(oldest, recent), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Tone.FOCUSED, insight.tone)
        // nextBestTask desempata por prioridad: la URGENT (aunque menos atrasada)
        // se sugiere primero; el mensaje surface la edad de la MÁS antigua.
        assertEquals(2L, insight.taskId)
        assertTrue(insight.message.contains("3 días"))
        assertTrue(insight.message.contains("2"))
    }

    @Test
    fun pendingHabitIsSuggestedWhenTasksAreClear() {
        val habit = HabitEntity(id = 7, title = "Leer diez minutos")
        val insight = GuardianCoach.insight(emptyList(), listOf(habit), emptyList(), now, zone)

        assertEquals("Leer diez minutos", insight.title)
        assertNotNull(insight.message)
    }
}
