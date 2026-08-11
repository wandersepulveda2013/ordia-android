package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RoutineRulesTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    private val yesterdayEpoch = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val todayEpoch = today.atStartOfDay(zone).toInstant().toEpochMilli() + 1_000L

    private fun routineTask(id: Long, createdAt: Long, details: String = "Rutina: Mañana", status: TaskStatus = TaskStatus.INBOX) =
        TaskEntity(id = id, title = "Paso", details = details, createdAt = createdAt, status = status)

    @Test
    fun wasRunTodayTrueWhenCreatedTodayAndPending() {
        val tasks = listOf(routineTask(1, todayEpoch))

        assertTrue(RoutineRules.wasRunToday(tasks, "Mañana", today, zone))
    }

    @Test
    fun wasRunTodayFalseWhenCreatedYesterday() {
        val tasks = listOf(routineTask(1, yesterdayEpoch))

        assertFalse(RoutineRules.wasRunToday(tasks, "Mañana", today, zone))
    }

    @Test
    fun wasRunTodayTrueWhenCreatedTodayAndCompleted() {
        // Completar la tanda de hoy significa que la rutina ya se ejecutó:
        // un nuevo disparo no debe duplicar tareas en la bandeja.
        val tasks = listOf(routineTask(1, todayEpoch, status = TaskStatus.COMPLETED).copy(completed = true))

        assertTrue(RoutineRules.wasRunToday(tasks, "Mañana", today, zone))
    }

    @Test
    fun wasRunTodayFalseWhenTaskArchivedOrCancelled() {
        val archived = routineTask(1, todayEpoch).copy(archived = true)
        val cancelled = routineTask(2, todayEpoch, status = TaskStatus.CANCELLED)

        assertFalse(RoutineRules.wasRunToday(listOf(archived), "Mañana", today, zone))
        assertFalse(RoutineRules.wasRunToday(listOf(cancelled), "Mañana", today, zone))
    }

    @Test
    fun wasRunTodayTrueWhenTodayBatchPartiallyCompleted() {
        // Escenario del bug: el usuario completó parte de la rutina de hoy y un
        // re-disparo no debe duplicar los pasos restantes.
        val tasks = listOf(
            routineTask(1, todayEpoch, status = TaskStatus.COMPLETED).copy(completed = true),
            routineTask(2, todayEpoch, status = TaskStatus.INBOX)
        )

        assertTrue(RoutineRules.wasRunToday(tasks, "Mañana", today, zone))
    }

    @Test
    fun tasksFromRoutineFiltersByExactDetail() {
        val fromRoutine = routineTask(1, todayEpoch)
        val other = routineTask(2, todayEpoch, details = "Otra cosa")

        assertEquals(listOf(1L), RoutineRules.tasksFromRoutine(listOf(fromRoutine, other), "Mañana").map { it.id })
        assertTrue(RoutineRules.isCreatedByRoutine(fromRoutine, "Mañana"))
        assertFalse(RoutineRules.isCreatedByRoutine(other, "Mañana"))
        assertEquals("Rutina: Mañana", RoutineRules.routineDetail("Mañana"))
    }
}
