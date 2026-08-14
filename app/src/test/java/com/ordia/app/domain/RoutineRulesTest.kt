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

    // --- relinkAfterRename: tras renombrar una rutina, las tareas generadas hoy
    //     aún llevan el nombre viejo en `details` y wasRunToday no las reconoce →
    //     la rutina se dispara de nuevo y duplica tareas en la bandeja. ---

    @Test
    fun relinkAfterRename_returnsTodayActiveRoutineTasksWithNewDetail() {
        val tasks = listOf(routineTask(1, todayEpoch, details = "Rutina: Gym"))

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym matutino", today, zone)

        assertEquals(listOf(RoutineRules.RoutineTaskRelink(1L, "Rutina: Gym matutino")), relinks)
    }

    @Test
    fun relinkAfterRename_emptyWhenNoTaskWithOldName() {
        val tasks = listOf(routineTask(1, todayEpoch, details = "Rutina: Lectura"))

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym matutino", today, zone)

        assertTrue(relinks.isEmpty())
    }

    @Test
    fun relinkAfterRename_skipsYesterdayTasks() {
        // Solo importa "hoy": una tarea de ayer con el nombre viejo no afecta al
        // dedup de hoy y no se reetiqueta (evita reescribir histórico).
        val tasks = listOf(routineTask(1, yesterdayEpoch, details = "Rutina: Gym"))

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym matutino", today, zone)

        assertTrue(relinks.isEmpty())
    }

    @Test
    fun relinkAfterRename_skipsArchivedTasks() {
        val tasks = listOf(routineTask(1, todayEpoch, details = "Rutina: Gym").copy(archived = true))

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym matutino", today, zone)

        assertTrue(relinks.isEmpty())
    }

    @Test
    fun relinkAfterRename_skipsCancelledTasks() {
        val tasks = listOf(routineTask(1, todayEpoch, details = "Rutina: Gym", status = TaskStatus.CANCELLED))

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym matutino", today, zone)

        assertTrue(relinks.isEmpty())
    }

    @Test
    fun relinkAfterRename_emptyWhenNameUnchanged() {
        val tasks = listOf(routineTask(1, todayEpoch, details = "Rutina: Gym"))

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym", today, zone)

        assertTrue(relinks.isEmpty())
    }

    @Test
    fun relinkAfterRename_relinksMultipleTodayTasks() {
        val tasks = listOf(
            routineTask(1, todayEpoch, details = "Rutina: Gym"),
            routineTask(2, todayEpoch, details = "Rutina: Gym"),
            routineTask(3, todayEpoch, details = "Rutina: Lectura")
        )

        val relinks = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym M", today, zone)

        assertEquals(setOf(1L, 2L), relinks.map { it.taskId }.toSet())
        assertTrue(relinks.all { it.newDetails == "Rutina: Gym M" })
    }

    @Test
    fun relinkAfterRename_fixesWasRunTodayAfterRename() {
        // BUG: la rutina "Gym" se ejecutó hoy (tareas con details "Rutina: Gym") y
        // luego se renombró a "Gym M". wasRunToday(newName) no ve las tareas de hoy
        // → devolvía false → un re-disparo duplicaba los pasos en la bandeja.
        val tasks = listOf(routineTask(1, todayEpoch, details = "Rutina: Gym"))

        // Antes del relink: el run de hoy queda oculto tras el renombrado.
        assertFalse(RoutineRules.wasRunToday(tasks, "Gym M", today, zone))

        // Aplicar el relink: reetiquetar las tareas de hoy al nombre nuevo.
        val byId = RoutineRules.relinkAfterRename(tasks, "Gym", "Gym M", today, zone).associateBy { it.taskId }
        val relabeled = tasks.map { t -> byId[t.id]?.let { t.copy(details = it.newDetails) } ?: t }

        // Tras el relink: el dedup vuelve a ver el run de hoy → no se duplica.
        assertTrue(RoutineRules.wasRunToday(relabeled, "Gym M", today, zone))
    }
}
