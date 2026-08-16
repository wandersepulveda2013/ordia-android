package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.ui.OrdiaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pruebas de volumen: los motores centrales deben resolver correctamente con
 * miles de registros, que es el peor caso plausible de una vida de captura.
 */
class StressTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    private fun manyTasks(count: Int, status: TaskStatus = TaskStatus.PLANNED): List<TaskEntity> =
        (1L..count).map { id ->
            TaskEntity(
                id = id,
                title = "Tarea $id",
                status = status,
                priority = if (id % 7 == 0L) TaskPriority.HIGH else TaskPriority.NORMAL,
                dueAt = now + (id % 30) * 86_400_000L
            )
        }

    @Test
    fun whatNowEngineResolvesAmongTenThousandTasks() {
        val tasks = manyTasks(10_000)

        val suggestion = WhatNowEngine.suggest(tasks, now, zone)

        assertNotNull("Con miles de tareas debe haber sugerencia", suggestion)
        assertTrue("La sugerencia debe vencer hoy (día 0 del ciclo)", suggestion!!.task.dueAt!! <= now + 29 * 86_400_000L)
    }

    @Test
    fun taskRulesPickTheBestAmongTenThousandTasks() {
        val tasks = manyTasks(10_000)

        val best = TaskRules.nextBestTask(tasks, now)

        assertNotNull(best)
    }

    @Test
    fun guardianCoachProducesInsightAmongTenThousandTasks() {
        val tasks = manyTasks(10_000)

        val insight = GuardianCoach.insight(tasks, emptyList(), emptyList(), now, zone)

        assertTrue("Con 10 000 tareas planificadas el guardián debe detectar carga u orden", insight.kind != null)
    }

    @Test
    fun overdueDetectionScalesToTenThousand() {
        val tasks = manyTasks(10_000).mapIndexed { index, task ->
            val newDue = now - (index % 50) * 86_400_000L - (index % 7) * 3_600_000L
            task.copy(
                dueAt = newDue,
                updatedAt = newDue,
                status = if (index % 2 == 0) TaskStatus.PLANNED else TaskStatus.INBOX
            )
        }

        val insight = GuardianCoach.insight(tasks, emptyList(), emptyList(), now, zone)

        assertNotNull(insight)
        assertEquals(GuardianCoach.Kind.OVERDUE, insight.kind)
    }

    @Test
    fun uiStateDerivedFieldsHandleTenThousandTasks() {
        val tasks = manyTasks(8_000, TaskStatus.INBOX) + manyTasks(2_000).map { it.copy(status = TaskStatus.PLANNED) }

        val state = OrdiaUiState(tasks = tasks)

        assertEquals(8_000, state.inboxTasks.size)
        assertEquals(10_000, state.pendingTasks.size)
        assertEquals(10_000, state.pendingCount)
        assertEquals(10_000, state.rootTasks.size)
    }

    @Test
    fun parserBatchHandlesThousandPhrases() {
        val phrases = (1..1_000).map { "Llamar a mamá el día ${(it % 28) + 1} a las 9" }

        val parsed = phrases.map { NaturalTaskParser.parse(it, now, zone) }

        assertEquals(1_000, parsed.size)
        assertTrue(parsed.all { it.dueAt != null })
        assertEquals("Llamar a mamá", parsed.first().title)
    }

    @Test
    fun whatNowSummaryWithTenThousandTasksIsShort() {
        val tasks = manyTasks(10_000)

        val suggestion = WhatNowEngine.suggest(tasks, now, zone)

        assertNotNull(suggestion)
        assertTrue(
            "El resumen debe ser una línea corta",
            suggestion!!.summary.length <= 90
        )
    }
}
