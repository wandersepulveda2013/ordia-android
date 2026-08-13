package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SummaryEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int = 9): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        dueAt: Long? = null,
        startAt: Long? = null,
        completed: Boolean = false,
        completedAt: Long? = null,
        status: TaskStatus = TaskStatus.PLANNED,
        durationMinutes: Int = 25,
        priority: TaskPriority = TaskPriority.NORMAL,
        title: String = "T$id"
    ) = TaskEntity(
        id = id,
        title = title,
        dueAt = dueAt,
        startAt = startAt,
        durationMinutes = durationMinutes,
        status = status,
        completed = completed,
        completedAt = completedAt,
        priority = priority
    )

    @Test
    fun emptyListYieldsZeroSummary() {
        val s = SummaryEngine.summarize(emptyList(), now, zone)

        assertEquals(0, s.completedToday)
        assertEquals(0, s.remainingToday)
        assertEquals(0, s.remainingMinutesToday)
        assertEquals(0, s.overdue)
        assertEquals(0, s.inboxPending)
        assertEquals(0, s.completedThisWeek)
        assertEquals(0f, s.weekDailyAverage)
    }

    @Test
    fun countsCompletedTodayAndRemainingWithMinutes() {
        val tasks = listOf(
            task(1, dueAt = at(today, 9), completed = true, completedAt = at(today, 11)),
            task(2, dueAt = at(today, 14), durationMinutes = 30),
            task(3, startAt = at(today, 16), durationMinutes = 45),
            task(4, dueAt = at(today.minusDays(2), 9))
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.completedToday)
        assertEquals(2, s.remainingToday)
        assertEquals(75, s.remainingMinutesToday)
        // task 4 venció hace dos días y sigue pendiente
        assertEquals(1, s.overdue)
    }

    @Test
    fun overdueUsesInjectedNowAndIgnoresCompletedArchivedCancelled() {
        val tasks = listOf(
            task(1, dueAt = at(today.minusDays(1), 8)),
            task(2, dueAt = at(today.minusDays(1), 8), completed = true, completedAt = at(today.minusDays(1), 20)),
            task(3, dueAt = at(today.minusDays(1), 8), status = TaskStatus.CANCELLED),
            task(4, dueAt = at(today.minusDays(1), 8)).copy(archived = true)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.overdue)
    }

    @Test
    fun countsInboxPendingReview() {
        val tasks = listOf(
            task(1, status = TaskStatus.INBOX),
            task(2, status = TaskStatus.INBOX),
            task(3, status = TaskStatus.INBOX).copy(archived = true),
            task(4, status = TaskStatus.PLANNED)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(2, s.inboxPending)
    }

    @Test
    fun completedThisWeekIncludesLastSevenDays() {
        val tasks = listOf(
            task(1, completed = true, completedAt = at(today)),
            task(2, completed = true, completedAt = at(today.minusDays(3))),
            task(3, completed = true, completedAt = at(today.minusDays(6), 23)),
            task(4, completed = true, completedAt = at(today.minusDays(7))),
            task(5, completed = true, completedAt = at(today.minusDays(30)))
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(3, s.completedThisWeek)
        assertEquals(3f / 7f, s.weekDailyAverage)
    }

    @Test
    fun remainingTodayCountsStartAtWhenNoDueAt() {
        val tasks = listOf(
            task(1, startAt = at(today, 14)),
            task(2, startAt = at(today, 18)),
            task(3, startAt = at(today.minusDays(1), 14)).copy(completed = true, completedAt = at(today.minusDays(1), 15))
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(2, s.remainingToday)
        assertEquals(0, s.overdue) // ninguna tiene dueAt vencido
    }

    @Test
    fun remainingMinutesCoercesPerTaskToPlanBounds() {
        // La badge de minutos debe ser coherente con el plan del día:
        // una tarea sin duración (5m por defecto) cuenta como el mínimo del
        // plan (10m) y una tarea enorme (600m) se acota al máximo del plan (180m),
        // igual que hace DayPlanner. Así el resumen no muestra minutos que el
        // plan no podría acomodar.
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 600), // cap 180
            task(2, dueAt = at(today, 11), durationMinutes = 5)   // floor 10
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(2, s.remainingToday)
        assertEquals(180 + 10, s.remainingMinutesToday)
    }

    @Test
    fun remainingMinutesMatchesDayPlannerScheduledMinutes() {
        // Coherencia plan vs resumen: para un día despejado, la suma de
        // plannedDuration de las tareas de hoy (resumen) coincide con la
        // suma de duraciones de los bloques que el plan logra agendar.
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 30),
            task(2, dueAt = at(today, 11), durationMinutes = 45),
            task(3, dueAt = at(today, 14), durationMinutes = 60)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)
        val plan = DayPlanner.build(tasks, today, now = now, zone = zone)

        assertEquals(s.remainingMinutesToday, plan.scheduledMinutes - (plan.blocks.size - 1) * 10)
    }

    @Test
    fun priorityIsNotPartOfSummary() {
        val tasks = listOf(
            task(1, dueAt = at(today, 9), status = TaskStatus.INBOX).copy(priority = TaskPriority.URGENT)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.remainingToday)
        assertEquals(1, s.inboxPending)
    }

    @Test
    fun subtasksDoNotInflateOverdueCount() {
        // Un padre atrasado con dos subtareas también atrasadas debe contar
        // como 1 sola tarea raíz atrasada, no 3 (las subtareas son anidadas).
        val tasks = listOf(
            task(1, dueAt = at(today.minusDays(1), 8)),
            task(2, dueAt = at(today.minusDays(1), 8)).copy(parentTaskId = 1),
            task(3, dueAt = at(today.minusDays(1), 8)).copy(parentTaskId = 1)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.overdue)
    }

    @Test
    fun subtasksDoNotInflateRemainingToday() {
        // Un padre con dueAt hoy y dos subtareas también hoy cuenta como 1
        // pendiente de hoy; la duración es la del padre, no la suma de las tres.
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 30),
            task(2, dueAt = at(today, 9), durationMinutes = 20).copy(parentTaskId = 1),
            task(3, dueAt = at(today, 9), durationMinutes = 15).copy(parentTaskId = 1)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.remainingToday)
        assertEquals(30, s.remainingMinutesToday)
    }

    @Test
    fun subtasksDoNotInflateCompletedToday() {
        // Completar las dos subtareas auto-completa al padre: el usuario hizo
        // 2 acciones reales; el resumen debe mostrar 1 completada hoy (raíz),
        // no 3 (padre + 2 subtareas).
        val tasks = listOf(
            task(1, dueAt = at(today, 9), completed = true, completedAt = at(today, 12)),
            task(2, completed = true, completedAt = at(today, 11)).copy(parentTaskId = 1),
            task(3, completed = true, completedAt = at(today, 12)).copy(parentTaskId = 1)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.completedToday)
        assertEquals(1, s.completedThisWeek)
    }

    @Test
    fun dayLoad_isLightWhenNothingRemaining() {
        val tasks = listOf(
            task(1, dueAt = at(today, 9), completed = true, completedAt = at(today, 11))
        )

        val s = SummaryEngine.summarize(tasks, now, zone) // now=12:00

        assertEquals(DayLoad.LIGHT, s.dayLoad)
    }

    @Test
    fun dayLoad_isOnTrackWhenRemainingFitsWithinHalfTheFreeDay() {
        // now=12:00 → jornada 9:00-18:00 → quedan 6h=360 min libres.
        // remainingMinutes 120 ≤ 180 (mitad) → ON_TRACK.
        val tasks = listOf(
            task(1, dueAt = at(today, 14), durationMinutes = 60),
            task(2, dueAt = at(today, 16), durationMinutes = 60)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(120, s.remainingMinutesToday)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    @Test
    fun dayLoad_isFullWhenRemainingFitsButExceedsHalfTheFreeDay() {
        // quedan 360 min libres; remainingMinutes 240 > 180 (mitad) y ≤ 360 → FULL.
        val tasks = listOf(
            task(1, dueAt = at(today, 14), durationMinutes = 120),
            task(2, dueAt = at(today, 16), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(240, s.remainingMinutesToday)
        assertEquals(DayLoad.FULL, s.dayLoad)
    }

    @Test
    fun dayLoad_isOverloadedWhenRemainingExceedsFreeDay() {
        // quedan 360 min libres; remainingMinutes 480 > 360 → OVERLOADED.
        val tasks = listOf(
            task(1, dueAt = at(today, 14), durationMinutes = 180),
            task(2, dueAt = at(today, 16), durationMinutes = 180),
            task(3, dueAt = at(today, 17), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(480, s.remainingMinutesToday)
        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
    }

    @Test
    fun dayLoad_isOverloadedPastWorkingHoursEvenIfLittleWorkRemains() {
        // A las 19:00 (pasado el fin de jornada 18:00) cualquier trabajo restante
        // no cabe en el día → OVERLOADED, sin importar lo pequeña que sea.
        val lateNow = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 10)
        )

        val s = SummaryEngine.summarize(tasks, lateNow, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
    }

    @Test
    fun dayLoad_isOnTrackAtStartOfDayWithModestWork() {
        // 9:00 → jornada entera libre (540 min); remainingMinutes 90 ≤ 270 → ON_TRACK.
        val morningNow = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, dueAt = at(today, 11), durationMinutes = 45),
            task(2, dueAt = at(today, 15), durationMinutes = 45)
        )

        val s = SummaryEngine.summarize(tasks, morningNow, zone)

        assertEquals(90, s.remainingMinutesToday)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    @Test
    fun deferralSuggestion_nullWhenNotOverloaded() {
        val tasks = listOf(
            task(1, dueAt = at(today, 14), durationMinutes = 60),
            task(2, dueAt = at(today, 16), durationMinutes = 60)
        )

        val s = SummaryEngine.summarize(tasks, now, zone) // ON_TRACK

        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
        assertEquals(null, s.deferralSuggestion)
    }

    @Test
    fun deferralSuggestion_picksLowestPriorityTaskWhenOverloaded() {
        // now=12:00 → 360 min libres; 4×120=480 > 360 → OVERLOADED.
        // Candidatas no vencidas: URGENT(1), HIGH(2), NORMAL(3), LOW(4) → sugiere LOW.
        val tasks = listOf(
            task(1, dueAt = at(today, 14), durationMinutes = 120, priority = TaskPriority.URGENT, title = "Urgente"),
            task(2, dueAt = at(today, 15), durationMinutes = 120, priority = TaskPriority.HIGH, title = "Alta"),
            task(3, dueAt = at(today, 16), durationMinutes = 120, priority = TaskPriority.NORMAL, title = "Normal"),
            task(4, dueAt = at(today, 17), durationMinutes = 120, priority = TaskPriority.LOW, title = "Posponerme")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(4L, sug?.taskId)
        assertEquals("Posponerme", sug?.title)
    }

    @Test
    fun deferralSuggestion_atSamePriorityPicksLatestDueToMaximizeMargin() {
        // now=12:00 → 360 min libres; 3×120=360 == 360 → FULL. Añadimos una 4ª
        // tarea NORMAL para saturar (4×120=480 > 360 → OVERLOADED). Todas NORMAL:
        // gana la que vence más tarde (más margen, más segura de aplazar).
        val tasks = listOf(
            task(1, dueAt = at(today, 13), durationMinutes = 120, title = "Temprana"),
            task(2, dueAt = at(today, 14), durationMinutes = 120, title = "Media"),
            task(3, dueAt = at(today, 16), durationMinutes = 120, title = "Tardia"),
            task(4, dueAt = at(today, 17), durationMinutes = 120, title = "Ultima")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(4L, sug?.taskId)
        assertEquals("Ultima", sug?.title)
    }

    @Test
    fun deferralSuggestion_neverSuggestsOverdueTask() {
        // A las 19:00 (pasado fin de jornada) cualquier trabajo restante satura.
        // Hay una tarea vencida (ayer) y una de hoy NO vencida. La sugerencia
        // debe apuntar a la de hoy, jamás a la vencida.
        val lateNow = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, dueAt = at(today.minusDays(1), 9), durationMinutes = 10, priority = TaskPriority.LOW, title = "Vencida"),
            task(2, dueAt = at(today, 23), durationMinutes = 10, priority = TaskPriority.URGENT, title = "DeHoy")
        )

        val s = SummaryEngine.summarize(tasks, lateNow, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(2L, sug?.taskId)
        assertEquals("DeHoy", sug?.title)
    }

    @Test
    fun deferralSuggestion_whenAllRemainingTasksAreOverdue_returnsNull() {
        // A las 19:00 la única tarea restante de hoy está vencida → OVERLOADED
        // pero no hay nada posponible sin empeorar un retraso → sin sugerencia.
        val lateNow = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, dueAt = at(today, 8), durationMinutes = 10, priority = TaskPriority.LOW, title = "VencidaHoy")
        )

        val s = SummaryEngine.summarize(tasks, lateNow, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        assertEquals(null, s.deferralSuggestion)
    }
}
