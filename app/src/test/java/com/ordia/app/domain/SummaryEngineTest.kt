package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
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

    // ---- El veredicto del día cuenta el trabajo vencido como carga real ----
    //
    // El tiempo que queda de jornada es un recurso finito compartido por el
    // trabajo de hoy Y por las tareas vencidas que aún no se han hecho. Antes,
    // assessDayLoad solo comparaba "minutos de hoy" contra minutos libres: un día
    // con una tarea fácil de hoy y varias vencidas decía "ON_TRACK", ocultando
    // que el usuario está realmente saturado. El veredicto ahora es honesto.

    @Test
    fun dayLoad_countsOverdueWorkAsLoad_soOverloadedEvenWhenTodayFits() {
        // now=12:00 → 360 min libres. Una tarea de hoy (60 min) cabría holgada
        // (ON_TRACK), pero hay 5 tareas vencidas de 120 min (600 min): el trabajo
        // real de hoy (hoy + vencidas) = 660 > 360 → OVERLOADED. Antes el veredicto
        // ignoraba las vencidas y mentía "ON_TRACK".
        val tasks = listOf(
            task(1, dueAt = at(today, 17), durationMinutes = 60),
            task(2, dueAt = at(today.minusDays(1), 9), durationMinutes = 120),
            task(3, dueAt = at(today.minusDays(2), 9), durationMinutes = 120),
            task(4, dueAt = at(today.minusDays(3), 9), durationMinutes = 120),
            task(5, dueAt = at(today.minusDays(4), 9), durationMinutes = 120),
            task(6, dueAt = at(today.minusDays(5), 9), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(5, s.overdue)
        // La badge de minutos de hoy sigue siendo solo la de hoy (60), sin inflar.
        assertEquals(60, s.remainingMinutesToday)
        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
    }

    @Test
    fun dayLoad_overduePushesOnTrackToFullWhenTodayAloneWouldFit() {
        // now=12:00 → 360 min libres. Hoy: 60 min (≤ mitad 180 → ON_TRACK solo).
        // Vencidas: 240 min. Carga real = 300; 180 < 300 ≤ 360 → FULL.
        val tasks = listOf(
            task(1, dueAt = at(today, 17), durationMinutes = 60),
            task(2, dueAt = at(today.minusDays(1), 9), durationMinutes = 120),
            task(3, dueAt = at(today.minusDays(2), 9), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(2, s.overdue)
        assertEquals(DayLoad.FULL, s.dayLoad)
    }

    @Test
    fun dayLoad_overdueOnlyDayIsNotLight() {
        // Solo hay tareas vencidas (ninguna "de hoy"): antes el veredicto era
        // LIGHT (ignoraba las vencidas), ocultando que sí hay trabajo. Ahora
        // 25 min de vencidas + 360 libres cabe holgado → ON_TRACK (no LIGHT).
        val tasks = listOf(
            task(1, dueAt = at(today.minusDays(1), 9), durationMinutes = 25)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.overdue)
        assertEquals(0, s.remainingToday)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    // ---------------------------------------------------------------------------
    // c.247 — el veredicto de carga del día cuenta el "olvido silencioso"
    // (missed-start) como trabajo que compite por la jornada, igual que ya cuenta
    // las vencidas. Simetría con DayPlanner (c.246 recupera missed-start en el
    // plan de hoy) y con GuardianCoach (c.243 lo reencuadra como RECUPERA EL
    // CONTROL). Antes el veredicto ignoraba un compromiso agendado cuyo hueco
    // (startAt) ya pasó y que aún no vence (dueAt futuro): el plan lo agendaba
    // hoy (c.246) pero la tarjeta/badge decía "ON_TRACK, cabe con holgura" — dos
    // verdades divergentes. Y mostDeferrableTask ya EXCLUYE missed-start de las
    // candidatas a posponer ("posponer un olvido lo agrava", l.250): el dominio
    // trataba el olvido como "hazlo, no lo aplaces", pero loadMinutes no le
    // asignaba sitio. c.247 cierra la contradicción contándolo como carga.
    // ---------------------------------------------------------------------------

    @Test
    fun dayLoad_countsMissedStartWorkAsLoad_soOverloadedEvenWhenTodayFits() {
        // now=12:00 → 360 min libres. Una tarea de hoy (60 min) cabría holgada
        // (ON_TRACK), pero hay 5 compromisos olvidados (startAt ayer, dueAt
        // mañana → isMissedStart puro, no vencidos) de 120 min (600 min): el
        // trabajo real de hoy (hoy + olvidados) = 660 > 360 → OVERLOADED. Antes
        // el veredicto ignoraba los olvidos y mentía "ON_TRACK" aunque el plan
        // (c.246) ya los agendaba hoy.
        val tasks = listOf(
            task(1, dueAt = at(today, 17), durationMinutes = 60),
            task(2, startAt = at(today.minusDays(1), 15), dueAt = at(today.plusDays(1), 18), durationMinutes = 120),
            task(3, startAt = at(today.minusDays(2), 9), dueAt = at(today.plusDays(2), 18), durationMinutes = 120),
            task(4, startAt = at(today.minusDays(3), 9), dueAt = at(today.plusDays(3), 18), durationMinutes = 120),
            task(5, startAt = at(today.minusDays(4), 9), dueAt = at(today.plusDays(4), 18), durationMinutes = 120),
            task(6, startAt = at(today.minusDays(5), 9), dueAt = at(today.plusDays(5), 18), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        // Los olvidos NO son vencidos (dueAt futuro) → overdue sigue 0.
        assertEquals(0, s.overdue)
        // Y no son "de hoy" (dueAt/startAt no caen hoy) → remainingToday y la
        // badge de minutos de hoy siguen siendo solo la tarea de hoy (60).
        assertEquals(1, s.remainingToday)
        assertEquals(60, s.remainingMinutesToday)
        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
    }

    @Test
    fun dayLoad_missedStartPushesOnTrackToFullWhenTodayAloneWouldFit() {
        // now=12:00 → 360 min libres. Hoy: 60 min (≤ mitad 180 → ON_TRACK solo).
        // 2 olvidados de 120 min (startAt ayer, dueAt futuro). Carga real =
        // 300; 180 < 300 ≤ 360 → FULL. El olvido empuja de ON_TRACK a FULL sin
        // llegar a saturar.
        val tasks = listOf(
            task(1, dueAt = at(today, 17), durationMinutes = 60),
            task(2, startAt = at(today.minusDays(1), 15), dueAt = at(today.plusDays(1), 18), durationMinutes = 120),
            task(3, startAt = at(today.minusDays(2), 9), dueAt = at(today.plusDays(2), 18), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(0, s.overdue)
        assertEquals(DayLoad.FULL, s.dayLoad)
    }

    @Test
    fun dayLoad_missedStartOnlyDayIsNotLight() {
        // Solo hay compromisos olvidados (ninguno "de hoy", ninguno vencido):
        // antes el veredicto era LIGHT (loadMinutes=0, no había ni hoy ni
        // vencidas), ocultando que el plan de hoy (c.246) ya recupera estos
        // olvidos. Ahora 25 min de olvidado + 360 libres cabe holgado → ON_TRACK
        // (no LIGHT): hay trabajo real, no "día vacío".
        val tasks = listOf(
            task(1, startAt = at(today.minusDays(1), 15), dueAt = at(today.plusDays(1), 18), durationMinutes = 25)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(0, s.overdue)
        assertEquals(0, s.remainingToday)
        assertEquals(0, s.remainingMinutesToday)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    @Test
    fun deferralSuggestion_firesWhenOverdueFillsDayEvenIfTodayFits() {
        // now=12:00 → 360 min libres. Hoy: 1 tarea LOW de 60 min (caber, cabe).
        // Pero 5 vencidas de 120 min saturan el día → OVERLOADED por las vencidas,
        // y la sugerencia nombra la tarea de hoy (no vencida) para reprogramar.
        val tasks = listOf(
            task(1, dueAt = at(today, 17), durationMinutes = 60, priority = TaskPriority.LOW, title = "Posponerme"),
            task(2, dueAt = at(today.minusDays(1), 9), durationMinutes = 120),
            task(3, dueAt = at(today.minusDays(2), 9), durationMinutes = 120),
            task(4, dueAt = at(today.minusDays(3), 9), durationMinutes = 120),
            task(5, dueAt = at(today.minusDays(4), 9), durationMinutes = 120),
            task(6, dueAt = at(today.minusDays(5), 9), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(1L, sug?.taskId)
        assertEquals("Posponerme", sug?.title)
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
    fun deferralSuggestion_atSamePriorityPicksTaskThatFreesMostCapacity() {
        // now=12:00 → 360 min libres. Forzamos OVERLOADED (390 min > 360). Dos
        // tareas LOW posponibles (no vencidas, no en curso, no inminentes): una
        // grande de 120 min (vence 14:00) y una pequeña de 30 min (vence 17:00).
        // El PROPÓSITO de posponer bajo OVERLOADED es recuperar capacidad para
        // que el día quepe: posponer la pequeña libera 30 min (sigue saturado,
        // consejo inútil); posponer la grande libera 120 min (resuelve la
        // saturación). Debe sugerir la que más capacidad libera aunque venza
        // antes (la grande apenas cabe con el resto; la pequeña cabe holgada).
        val tasks = listOf(
            task(1, dueAt = at(today, 14), durationMinutes = 120, priority = TaskPriority.LOW, title = "Grande"),
            task(2, dueAt = at(today, 17), durationMinutes = 30, priority = TaskPriority.LOW, title = "Pequena"),
            task(3, dueAt = at(today, 16), durationMinutes = 120, priority = TaskPriority.NORMAL, title = "A"),
            task(4, dueAt = at(today, 17), durationMinutes = 120, priority = TaskPriority.NORMAL, title = "B")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad) // 120+30+120+120 = 390 > 360
        val sug = s.deferralSuggestion
        assertEquals(1L, sug?.taskId)
        assertEquals("Grande", sug?.title)
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

    @Test
    fun deferralSuggestion_neverSuggestsInProgressTask() {
        // now=12:00 → 360 min libres; 4×120=480 > 360 → OVERLOADED.
        // La LOW (más posponible) está EN CURSO ahora (start 11:55, dur 120 → 11:55-13:55):
        // posponerla sería absurdo (es lo que el usuario hace ahora). La sugerencia
        // debe saltarla y apuntar a la siguiente más posponible: la NORMAL.
        val inProgressStart = today.atTime(11, 55).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, startAt = inProgressStart, durationMinutes = 120, priority = TaskPriority.LOW, title = "EnCurso"),
            task(2, dueAt = at(today, 14), durationMinutes = 120, priority = TaskPriority.NORMAL, title = "Posponible"),
            task(3, dueAt = at(today, 15), durationMinutes = 120, priority = TaskPriority.HIGH, title = "Alta"),
            task(4, dueAt = at(today, 16), durationMinutes = 120, priority = TaskPriority.URGENT, title = "Urgente")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(2L, sug?.taskId)
        assertEquals("Posponible", sug?.title)
    }

    @Test
    fun deferralSuggestion_neverSuggestsImminentStartTask() {
        // now=12:00 → 360 min libres; 4×120=480 > 360 → OVERLOADED.
        // La LOW (más posponible) es un compromiso que EMPIEZA EN 5 MIN (12:05):
        // posponer una cita a punto de arrancar es un consejo dañino. La sugerencia
        // debe saltarla y apuntar a la NORMAL.
        val imminentStart = today.atTime(12, 5).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, startAt = imminentStart, durationMinutes = 120, priority = TaskPriority.LOW, title = "Inminente"),
            task(2, dueAt = at(today, 14), durationMinutes = 120, priority = TaskPriority.NORMAL, title = "Posponible"),
            task(3, dueAt = at(today, 15), durationMinutes = 120, priority = TaskPriority.HIGH, title = "Alta"),
            task(4, dueAt = at(today, 16), durationMinutes = 120, priority = TaskPriority.URGENT, title = "Urgente")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(2L, sug?.taskId)
        assertEquals("Posponible", sug?.title)
    }

    @Test
    fun deferralSuggestion_whenOnlyPosponiblesAreInProgressOrImminent_returnsNull() {
        // A las 19:00 (pasado jornada) cualquier trabajo restante satura. La única
        // tarea de hoy posponible sin empeorar retraso es una reunión EN CURSO
        // (empezó 18:50, dura hasta 19:50). Posponer lo que se está viviendo no
        // ayuda → sin sugerencia.
        val lateNow = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val inProgressStart = today.atTime(18, 50).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, startAt = inProgressStart, durationMinutes = 60, priority = TaskPriority.LOW, title = "EnCurso")
        )

        val s = SummaryEngine.summarize(tasks, lateNow, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        assertEquals(null, s.deferralSuggestion)
    }

    // ---- Ventana de jornada aprendida (LearningProfile) ----
    //
    // El veredicto del día debe usar la ventana REAL del usuario cuando se le
    // pasa un perfil aprendido, no el 9–18 fijo. Esto es lo que evita que la
    // tarjeta de hoy mienta para horarios no estándar.

    @Test
    fun dayLoad_usesLearnedWindow_lateSleeperNotOverloadedAt17() {
        // A las 17:00 con jornada aprendida 9–23: quedan 6 h libres (360 min).
        // 3 tareas de 60 min = 180 min ≤ 360/2=180 → ON_TRACK.
        // Con la ventana fija 9–18, a las 17:00 solo quedarían 60 min → OVERLOADED.
        val at17 = today.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 60),
            task(2, dueAt = at(today, 10), durationMinutes = 60),
            task(3, dueAt = at(today, 11), durationMinutes = 60)
        )
        val profile = LearningProfile(dayStartMinute = 9 * 60, dayEndMinute = 23 * 60)
        val s = SummaryEngine.summarize(tasks, at17, zone, profile)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    @Test
    fun dayLoad_usesLearnedWindow_earlyRiserOverloadedPastTheirEnd() {
        // Jornada aprendida 6–14. A las 13:00 ya no cabe trabajo nuevo: solo
        // queda 1 h (60 min). 2 tareas de 60 min = 120 min > 60 → OVERLOADED.
        // Con la ventana fija 9–18, a las 13:00 quedarían 5 h → ON_TRACK (mentira).
        val at13 = today.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, dueAt = at(today, 6), durationMinutes = 60),
            task(2, dueAt = at(today, 7), durationMinutes = 60)
        )
        val profile = LearningProfile(dayStartMinute = 6 * 60, dayEndMinute = 14 * 60)
        val s = SummaryEngine.summarize(tasks, at13, zone, profile)
        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
    }

    @Test
    fun dayLoad_nullProfileFallsBackToDefaultWindow() {
        // Sin perfil, comportamiento idéntico al 9–18 fijo de siempre.
        val at12 = now // 12:00
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 60)
        )
        val withProfile = SummaryEngine.summarize(tasks, at12, zone, null)
        val withExplicitDefaults = SummaryEngine.summarize(tasks, at12, zone)
        assertEquals(withExplicitDefaults.dayLoad, withProfile.dayLoad)
    }

    @Test
    fun dayLoad_learnedWindowDoesNotAffectCounts() {
        // La ventana aprendida solo cambia el veredicto, no los conteos.
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 60),
            task(2, dueAt = at(today, 10), durationMinutes = 60, completed = true, completedAt = at(today, 9))
        )
        val profile = LearningProfile(dayStartMinute = 6 * 60, dayEndMinute = 23 * 60)
        val s = SummaryEngine.summarize(tasks, now, zone, profile)
        assertEquals(1, s.completedToday)
        assertEquals(1, s.remainingToday)
        assertEquals(60, s.remainingMinutesToday)
    }

    @Test
    fun deferralSuggestion_neverNamesTaskThatCannotBeDeferred() {
        // now=12:00 → 360 min libres. Una vencida (ayer, 180→180) satura el día
        // junto con dos tareas de hoy de 180 cada una → 540 > 360 → OVERLOADED.
        // La candidata TOP por la heurística (menor prioridad LOW) es una tarea
        // agendada con `startAt` hoy y SIN `dueAt`. `TaskRules.deferToNextDay`
        // devuelve null cuando no hay `dueAt`, así que esa tarea NO es posponible
        // con la acción "mover a mañana": sugerirla entrega al usuario un consejo
        // no accionable (canDefer=false → texto pasivo, sin tap) mientras existe
        // otra tarea de hoy CON vencimiento que SÍ podría moverse. La vencida
        // queda fuera de la sugerencia (posponer lo atrasado empeora el retraso),
        // así que la única posponible-real es la de vencimiento: debe ser ella.
        val tasks = listOf(
            task(1, startAt = at(today, 14), durationMinutes = 180, priority = TaskPriority.LOW, title = "SinVenc"),
            task(2, dueAt = at(today, 14), durationMinutes = 180, priority = TaskPriority.NORMAL, title = "ConVenc"),
            task(3, dueAt = at(today.minusDays(1), 9), durationMinutes = 180, priority = TaskPriority.LOW, title = "Vencida")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(2L, sug?.taskId)
        assertEquals("ConVenc", sug?.title)
        assertEquals(true, sug?.canDefer)
    }

    @Test
    fun deferralSuggestion_neverSuggestsMissedStartCommitment() {
        // now=12:00 → 360 min libres; 4×120=480 > 360 → OVERLOADED.
        // La LOW (más posponible por prioridad) es un compromiso AGENDADO cuyo
        // hueco ya pasó (start 09:00, dur 120 → ventana 09:00-11:00 cerrada a
        // las 12:00). Tiene `dueAt` hoy 23:00 (NO vencido a las 12:00), así que
        // Pasa el filtro de dueAt accionable (c.207) y NO es vencida: sin el
        // filtro de olvido sería la sugerida. Es exactamente el "olvido
        // silencioso" de [TaskRules.isMissedStart]: el usuario le dio hora y se
        // le pasó. Posponerlo a mañana es RE-OLVIDAR el compromiso que el
        // guardián (c.201), What Now (c.203) y el asistente "¿qué olvidé?"
        // (c.206) se esfuerzan en RECUPERAR — el mismo consejo dañino que la
        // exclusión de vencidas evita ("posponerlas empeora el retraso"). Debe
        // saltarla y apuntar a la NORMAL posponible.
        val missedStart = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, startAt = missedStart, dueAt = at(today, 23), durationMinutes = 120, priority = TaskPriority.LOW, title = "Olvidada"),
            task(2, dueAt = at(today, 14), durationMinutes = 120, priority = TaskPriority.NORMAL, title = "Posponible"),
            task(3, dueAt = at(today, 15), durationMinutes = 120, priority = TaskPriority.HIGH, title = "Alta"),
            task(4, dueAt = at(today, 16), durationMinutes = 120, priority = TaskPriority.URGENT, title = "Urgente")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        val sug = s.deferralSuggestion
        assertEquals(2L, sug?.taskId)
        assertEquals("Posponible", sug?.title)
    }

    @Test
    fun deferralSuggestion_whenOnlyPosponibleIsMissedStart_returnsNull() {
        // A las 19:00 (pasado jornada) cualquier trabajo restante satura. La única
        // tarea de hoy posponible SIN ser vencida/en-curso/inminente/sin-dueAt es
        // un compromiso cuyo hueco ya pasó (start 09:00, dur 60 → 09:00-10:00
        // cerrado) y con `dueAt` hoy 23:00 (no vencido a las 19:00): pasa el filtro
        // accionable de c.207, así que sin el filtro de olvido sería sugerida.
        // No debe sugerirse aplazar un compromiso olvidado: igual que no se sugiere
        // posponer lo vencido, no se sugiere posponer lo olvidado.
        val lateNow = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val missedStart = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            task(1, startAt = missedStart, dueAt = at(today, 23), durationMinutes = 60, priority = TaskPriority.LOW, title = "Olvidada")
        )

        val s = SummaryEngine.summarize(tasks, lateNow, zone)

        assertEquals(DayLoad.OVERLOADED, s.dayLoad)
        assertEquals(null, s.deferralSuggestion)
    }

    // ---- El tiempo YA gastado en una tarea en curso no vuelve a contar ----
    //
    // `freeMinutes` (la capacidad contra la que se compara la carga) se mide
    // desde AHORA hasta el fin de jornada: excluye el tiempo ya transcurrido.
    // Pero `loadMinutes` sumaba la `plannedDuration` COMPLETA de toda tarea de
    // hoy, incluida la que está EN CURSO: el minuto ya vivido trabajando esa
    // tarea se contaba dos veces (una consumida, otra como carga pendiente),
    // sobrestimando la saturación. Un día que cabía holgado aparecía FULL e
    // incluso podía sugerir posponer algo innecesariamente. El veredicto ahora
    // descuenta, de cada tarea en curso, los minutos ya transcurridos.

    @Test
    fun remainingMinutes_deductsTimeAlreadySpentOnInProgressTask() {
        // now=12:00; jornada 9-18 → 360 min libres (mitad 180).
        // Tarea A en curso desde 11:00, duración 120 → 60 min ya consumidos,
        // quedan 60. Tarea B (sin empezar) 120 min.
        // Carga REAL restante = 60 + 120 = 180 (≤ mitad 180 → ON_TRACK).
        // Antes se contaba la duración COMPLETA de A (120): 120+120=240 > 180 →
        // FULL, un veredicto pesimista que cuenta tiempo ya gastado.
        val tasks = listOf(
            task(1, startAt = at(today, 11), durationMinutes = 120),
            task(2, dueAt = at(today, 16), durationMinutes = 120)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(180, s.remainingMinutesToday)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    @Test
    fun remainingMinutes_loneInProgressTaskCountsOnlyRemaining() {
        // now=12:00; jornada 9-18 → 360 min libres.
        // Tarea en curso desde 10:00, duración 180 → 120 min ya consumidos,
        // quedan 60. La badge debe reflejar el trabajo que VERDADERAMENTE
        // falta (60), no el total planificado (180).
        val tasks = listOf(
            task(1, startAt = at(today, 10), durationMinutes = 180)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(60, s.remainingMinutesToday)
        assertEquals(DayLoad.ON_TRACK, s.dayLoad)
    }

    @Test
    fun dayLoad_deductingInProgressTimeAvoidsSpuriousDeferralSuggestion() {
        // now=12:00; jornada 9-18 → 360 min libres (mitad 180).
        // Tarea A en curso desde 09:00, duración 180: a las 12:00 está JUSTO al
        // final de su ventana (09:00-12:00) → 180 min ya consumidos, quedan 0.
        // Tareas B y C (no empezadas) 180 min cada una.
        // Carga REAL restante = 0 + 180 + 180 = 360 ≤ 360 → FULL (cabe, justo) →
        // NO se sugiere posponer nada.
        // Antes se contaba la duración COMPLETA de A (180): 180+180+180=540 > 360
        // → OVERLOADED, y se aconsejaba posponer B aunque el día en realidad cabía:
        // el tiempo ya totalmente gastado en A se contaba como carga pendiente.
        val tasks = listOf(
            task(1, startAt = at(today, 9), durationMinutes = 180, priority = TaskPriority.LOW, title = "EnCurso"),
            task(2, dueAt = at(today, 17), durationMinutes = 180, priority = TaskPriority.LOW, title = "Posponible"),
            task(3, dueAt = at(today, 18), durationMinutes = 180, priority = TaskPriority.NORMAL, title = "Otra")
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(360, s.remainingMinutesToday)
        assertEquals(DayLoad.FULL, s.dayLoad)
        assertEquals(null, s.deferralSuggestion)
    }

    // --- Paridad "completadas hoy" (c.284): fuente única TaskRules.completedTodayCount ---
    // SummaryEngine antes contaba canceladas y archivadas con completedAt hoy,
    // divergiendo del guardián y de la tarjeta de insight (que las excluían).

    @Test
    fun completedToday_ignoresCancelledCompletedToday() {
        val cancelled = task(1, completed = true, completedAt = at(today, 11), status = TaskStatus.CANCELLED)
        val s = SummaryEngine.summarize(listOf(cancelled), now, zone)
        assertEquals(0, s.completedToday)
    }

    @Test
    fun completedToday_ignoresArchivedCompletedToday() {
        val archived = task(1, completed = true, completedAt = at(today, 11)).copy(archived = true)
        val s = SummaryEngine.summarize(listOf(archived), now, zone)
        assertEquals(0, s.completedToday)
    }

    // --- "4º olvido" en el resumen del día (c.297): compromisos vencidos de
    // conversaciones como cola informativa honesta, sin inflar la carga/veredicto
    // (decisión c.246: un compromiso no es tarea hasta convertirse). Paridad con
    // el asistente (c.286), el nudge del guardián (c.288), el insight (c.289) y
    // la planificación (c.294): antes la tarjeta de resumen callaba un
    // compromiso vencido aunque mostrara "0 vencidas" / "El día va a tiempo".

    private fun commitment(
        id: Long,
        dueAt: Long? = null,
        status: CommitmentReviewStatus = CommitmentReviewStatus.PENDING,
        createdAt: Long = now
    ) = CommitmentEntity(
        id = id,
        conversationId = 1,
        kind = CommitmentKind.SELF_COMMITMENT,
        owner = CommitmentOwner.SELF,
        actor = "yo",
        action = "te llamo el viernes",
        dueAt = dueAt,
        confidence = 0.8f,
        reviewStatus = status,
        fingerprint = "fp$id",
        createdAt = createdAt
    )

    @Test
    fun overdueCommitments_zeroWhenNoCommitments() {
        val s = SummaryEngine.summarize(emptyList(), now, zone, commitments = emptyList())
        assertEquals(0, s.overdueCommitments)
    }

    @Test
    fun overdueCommitments_countsPendingDueBeforeNow() {
        val overdue = commitment(1, dueAt = at(today.minusDays(2), 9))
        val s = SummaryEngine.summarize(emptyList(), now, zone, commitments = listOf(overdue))
        assertEquals(1, s.overdueCommitments)
    }

    @Test
    fun overdueCommitments_ignoresConvertedOrDismissed() {
        val converted = commitment(1, dueAt = at(today.minusDays(2), 9), status = CommitmentReviewStatus.CONVERTED)
        val dismissed = commitment(2, dueAt = at(today.minusDays(3), 9), status = CommitmentReviewStatus.DISMISSED)
        val s = SummaryEngine.summarize(emptyList(), now, zone, commitments = listOf(converted, dismissed))
        assertEquals(0, s.overdueCommitments)
    }

    @Test
    fun overdueCommitments_ignoresPendingNotYetDue() {
        val future = commitment(1, dueAt = at(today.plusDays(1), 9))
        val noDue = commitment(2, dueAt = null)
        val s = SummaryEngine.summarize(emptyList(), now, zone, commitments = listOf(future, noDue))
        assertEquals(0, s.overdueCommitments)
    }

    @Test
    fun overdueCommitments_countsMany() {
        val cs = listOf(
            commitment(1, dueAt = at(today.minusDays(3), 9)),
            commitment(2, dueAt = at(today.minusDays(1), 9)),
            commitment(3, dueAt = at(today.plusDays(1), 9))
        )
        val s = SummaryEngine.summarize(emptyList(), now, zone, commitments = cs)
        assertEquals(2, s.overdueCommitments)
    }

    @Test
    fun overdueCommitments_doesNotAffectLoadVerdictOrOverdueTasks() {
        // Un día despejado de tareas pero con un compromiso vencido: la carga
        // sigue LIGHT (el compromiso no cuenta como trabajo de hoy) y overdue
        // (tareas) sigue 0, pero overdueCommitments lo nombra. Así no se miente
        // por omisión ni se infla el veredicto.
        val overdue = commitment(1, dueAt = at(today.minusDays(2), 9))
        val s = SummaryEngine.summarize(emptyList(), now, zone, commitments = listOf(overdue))
        assertEquals(0, s.overdue)
        assertEquals(0, s.remainingToday)
        assertEquals(DayLoad.LIGHT, s.dayLoad)
        assertEquals(1, s.overdueCommitments)
    }

    @Test
    fun overdueCommitments_defaultsToZeroWhenOmitted() {
        // Backward-compat: el parámetro commitments es opcional.
        val s = SummaryEngine.summarize(emptyList(), now, zone)
        assertEquals(0, s.overdueCommitments)
    }
}
