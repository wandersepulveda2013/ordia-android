package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TaskRulesTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 7, 29)

    @Test
    fun dueToday_matchesCalendarDay() {
        val today = LocalDate.of(2026, 7, 29)
        val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val due = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(TaskRules.isDueToday(TaskEntity(title = "Tarea", dueAt = due), now, zone))
    }

    @Test
    fun completedTask_isNotOverdue() {
        val task = TaskEntity(title = "Lista", dueAt = 1, completed = true)
        assertFalse(TaskRules.isOverdue(task, 10))
    }

    @Test
    fun nextBestTask_prefersHighPriority() {
        val normal = TaskEntity(id = 1, title = "Normal", priority = TaskPriority.NORMAL)
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        assertEquals(high, TaskRules.nextBestTask(listOf(normal, high), 100))
    }

    @Test
    fun nextBestTask_prefersTaskHappeningNowOverUrgent() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val inProgress = TaskEntity(
            id = 1, title = "En curso",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            durationMinutes = 60
        )
        val urgent = TaskEntity(id = 2, title = "Urgente", priority = TaskPriority.URGENT)
        assertEquals(inProgress, TaskRules.nextBestTask(listOf(urgent, inProgress), now, zone))
    }

    @Test
    fun nextBestTask_prefersDueTodayOverUrgentDueTomorrow() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val dueToday = TaskEntity(
            id = 1, title = "Vence hoy", priority = TaskPriority.NORMAL,
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone)
        )
        val urgentTomorrow = TaskEntity(
            id = 2, title = "Urgente mañana", priority = TaskPriority.URGENT,
            dueAt = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(18, 0), zone)
        )
        assertEquals(dueToday, TaskRules.nextBestTask(listOf(urgentTomorrow, dueToday), now, zone))
    }

    @Test
    fun nextBestTask_prefersImminentStartOverInbox() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val imminent = TaskEntity(
            id = 1, title = "Reunión en 5 min",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 5), zone),
            durationMinutes = 30
        )
        val inbox = TaskEntity(id = 2, title = "Bandeja", priority = TaskPriority.HIGH)
        assertEquals(imminent, TaskRules.nextBestTask(listOf(inbox, imminent), now, zone))
    }

    @Test
    fun nextBestTask_startOutsideImminentWindowStaysDeprioritized() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val later = TaskEntity(
            id = 1, title = "Reunión en 2 h",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone),
            durationMinutes = 30
        )
        val inbox = TaskEntity(id = 2, title = "Bandeja", priority = TaskPriority.HIGH)
        assertEquals(inbox, TaskRules.nextBestTask(listOf(later, inbox), now, zone))
    }

    @Test
    fun nextBestTask_overdueBeatsImminentStart() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val overdue = TaskEntity(
            id = 1, title = "Atrasada",
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        )
        val imminent = TaskEntity(
            id = 2, title = "Reunión en 5 min",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 5), zone),
            durationMinutes = 30
        )
        assertEquals(overdue, TaskRules.nextBestTask(listOf(imminent, overdue), now, zone))
    }

    @Test
    fun focusClock_formatsMinutesAndSeconds() {
        assertEquals("25:00", FocusClock.format(1500))
        assertEquals("00:00", FocusClock.format(-2))
    }

    @Test
    fun deferToNextDay_returnsNullWithoutDueAt() {
        val task = TaskEntity(id = 1, title = "Sin fecha")
        assertNull(TaskRules.deferToNextDay(task, now = 0L, zone))
    }

    @Test
    fun deferToNextDay_movesDueToTomorrowSameTime() {
        val due = DateRules.toEpochMillis(date, LocalTime.of(18, 30), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Reunión", dueAt = due)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertEquals(date.plusDays(1), DateRules.toLocalDate(deferred.dueAt!!, zone))
        assertEquals(LocalTime.of(18, 30), DateRules.toLocalTime(deferred.dueAt, zone))
        assertEquals(now, deferred.updatedAt)
    }

    @Test
    fun deferToNextDay_preservesReminderOffset() {
        val due = DateRules.toEpochMillis(date, LocalTime.of(18, 30), zone)
        val reminder = due - 30 * 60_000L // 30 min antes
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Recordada", dueAt = due, reminderAt = reminder)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertEquals(deferred.dueAt!! - 30 * 60_000L, deferred.reminderAt)
        // sigue siendo 30 min antes del nuevo vencimiento
        assertEquals(date.plusDays(1), DateRules.toLocalDate(deferred.reminderAt!!, zone))
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(deferred.reminderAt, zone))
    }

    @Test
    fun deferToNextDay_shiftsStartBySameDelta() {
        val start = DateRules.toEpochMillis(date, LocalTime.of(17, 0), zone)
        val due = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone) // 1 h después del inicio
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Con inicio", startAt = start, dueAt = due, durationMinutes = 60)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertEquals(deferred.dueAt!! - 60 * 60_000L, deferred.startAt) // gap 1 h conservado
        assertEquals(date.plusDays(1), DateRules.toLocalDate(deferred.startAt!!, zone))
    }

    @Test
    fun deferToNextDay_doesNotMutateOriginal() {
        val due = DateRules.toEpochMillis(date, LocalTime.of(18, 30), zone)
        val task = TaskEntity(id = 1, title = "Original", dueAt = due)
        TaskRules.deferToNextDay(task, now = 0L, zone)

        assertEquals(due, task.dueAt)
        assertNull(task.startAt)
    }

    @Test
    fun deferToNextDay_keepsNullFieldsNull() {
        val due = DateRules.toEpochMillis(date, LocalTime.of(18, 30), zone)
        val task = TaskEntity(id = 1, title = "Solo vencimiento", dueAt = due)
        val deferred = TaskRules.deferToNextDay(task, now = 0L, zone)!!

        assertNull(deferred.startAt)
        assertNull(deferred.reminderAt)
    }

    // --- Past-safe al posponer una tarea vencida (hilo c.187→c.190) ---
    // Posponer "a mañana" una tarea vencida por >1 día no debe dejarla TODAVÍA
    // vencida: "mañana a la misma hora" relativa al vencimiento caería hoy o
    // antes. Un pospuesto que no adelanta la tarea es un olvido disfrazado.

    @Test
    fun deferToNextDay_overdueByMoreThanADay_landsInFuture() {
        // Venció hace 2 días a las 18:30; ahora es hoy 10:00.
        val due = DateRules.toEpochMillis(date.minusDays(2), LocalTime.of(18, 30), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Muy vencida", dueAt = due)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertTrue("El vencimiento pospuesto debe quedar en el futuro, no seguir vencido",
            deferred.dueAt!! > now)
    }

    @Test
    fun deferToNextDay_overdueByMoreThanADay_reminderStaysScheduled() {
        // Recordatorio 30 min antes del vencimiento original (ya disparado).
        val due = DateRules.toEpochMillis(date.minusDays(2), LocalTime.of(18, 30), zone)
        val reminder = due - 30 * 60_000L
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Muy vencida", dueAt = due, reminderAt = reminder)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        // ReminderSync.triggers descarta los recordatorios pasados: si el
        // recordatorio trasladado queda en el pasado, la tarea pierde su aviso
        // y se olvida de nuevo. Debe quedar futuro (o nulo si no cabe margen),
        // pero nunca en el pasado.
        val trigger = deferred.reminderAt ?: deferred.dueAt
        assertTrue("El disparo pospuesto no debe caer en el pasado (olvido silencioso)",
            trigger == null || trigger > now)
    }

    @Test
    fun deferToNextDay_nonOverdue_preservesReminderOffsetExactly() {
        // Tarea sana (vence hoy a la tarde): el offset debe conservarse tal cual,
        // sin recurrir al default past-safe. Regresión del caso común.
        val due = DateRules.toEpochMillis(date, LocalTime.of(18, 30), zone)
        val reminder = due - 90 * 60_000L // 90 min antes
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Sana", dueAt = due, reminderAt = reminder)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertEquals(deferred.dueAt!! - 90 * 60_000L, deferred.reminderAt)
        assertEquals(date.plusDays(1), DateRules.toLocalDate(deferred.reminderAt!!, zone))
    }

    @Test
    fun deferToNextDay_overdueWithHugeOffset_reminderNeverPast() {
        // Offset enorme (25 días "antes") sobre una tarea vencida: el instante
        // trasladado cae en el pasado aunque el vencimiento se adelantara al
        // futuro. Debe caer al default past-safe (futuro o nulo), nunca pasado.
        val due = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 30), zone)
        val reminder = due - 25L * 24 * 60 * 60_000L // 25 días antes
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Offset enorme", dueAt = due, reminderAt = reminder)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertTrue(deferred.dueAt!! > now)
        assertTrue("Recordatorio nunca en el pasado",
            deferred.reminderAt == null || deferred.reminderAt!! > now)
    }

    // --- Past-safe del INICIO al posponer (simétrico al reminder c.187/c.190 y a
    // RecurrenceEngine.pastSafeStart c.189) ---
    // Antes solo el recordatorio se hacía past-safe; el `startAt` se trasladaba
    // por `delta` sin más. En una tarea vencida con antelación grande (p. ej.
    // empieza 25 h antes del vencimiento), el inicio trasladado caía en el PASADO
    // y la tarea pospuesta nacía como "inicio perdido" (isMissedStart) sin que el
    // usuario la hubiese empezado todavía. El inicio debe quedar futuro (o nulo)
    // y nunca posterior al vencimiento (invariante de backup startAt <= dueAt).

    @Test
    fun deferToNextDay_overdueWithLargeLead_startNeverPast() {
        // Inicio 25 h antes del vencimiento; vencida por 2 días. Al posponer, el
        // inicio trasladado (startAt + delta) cae en el pasado → la tarea nacería
        // como "inicio perdido", igual que ocurría en RecurrenceEngine antes de c.189.
        val start = DateRules.toEpochMillis(date.minusDays(3), LocalTime.of(9, 0), zone)
        val due = DateRules.toEpochMillis(date.minusDays(2), LocalTime.of(10, 0), zone) // antelación 25 h
        val now = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone)
        val task = TaskEntity(id = 1, title = "Lead grande vencida", startAt = start, dueAt = due, durationMinutes = 60)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertTrue("El vencimiento pospuesto debe quedar en el futuro", deferred.dueAt!! > now)
        assertTrue("El inicio pospuesto no debe caer en el pasado (inicio perdido)",
            deferred.startAt == null || deferred.startAt!! > now)
        if (deferred.startAt != null) {
            assertTrue("startAt <= dueAt (invariante de backup)", deferred.startAt!! <= deferred.dueAt!!)
        }
    }

    @Test
    fun deferToNextDay_nonOverdueLargeLead_preservesStartOffset() {
        // Tarea sana con antelación grande: el inicio trasladado sigue siendo
        // futuro, así que se conserva el offset exacto (regresión del caso común).
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val due = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone) // antelación 1 h, sana
        val now = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(12, 0), zone) // ayer
        val task = TaskEntity(id = 1, title = "Sana con lead", startAt = start, dueAt = due, durationMinutes = 60)
        val deferred = TaskRules.deferToNextDay(task, now, zone)!!

        assertEquals(deferred.dueAt!! - 60 * 60_000L, deferred.startAt)
        assertTrue(deferred.startAt!! > now)
    }

    @Test
    fun completedRootCount_countsCompletedRootsAndExcludesSubtasks() {
        val root = TaskEntity(id = 1, title = "Raíz", completed = true)
        val subtask = TaskEntity(id = 2, title = "Sub", completed = true, parentTaskId = 1)
        val pending = TaskEntity(id = 3, title = "Pendiente")

        assertEquals(1, TaskRules.completedRootCount(listOf(root, subtask, pending)))
    }

    @Test
    fun completedRootCount_excludesArchivedCompleted() {
        val visible = TaskEntity(id = 1, title = "Visible", completed = true)
        val archived = TaskEntity(id = 2, title = "Archivada", completed = true, archived = true)

        assertEquals(1, TaskRules.completedRootCount(listOf(visible, archived)))
    }

    @Test
    fun completedRootCount_excludesCancelledCompleted() {
        // Defensa en profundidad: aunque hoy nada produce CANCELLED+completed, una
        // tarea descartada nunca debe contar como logro completado visible.
        val done = TaskEntity(id = 1, title = "Hecha", completed = true)
        val cancelled = TaskEntity(
            id = 2, title = "Descartada", completed = true, status = TaskStatus.CANCELLED
        )

        assertEquals(1, TaskRules.completedRootCount(listOf(done, cancelled)))
    }

    // --- isActive: predicado canónico anti-fuga de CANCELLED (c.169/170) ---
    // La causa raíz de los bugs c.169/c.170 fue repetir `!completed && !archived`
    // olvidando `status != CANCELLED`. isActive centraliza ese trio; estos tests
    // anclan el contrato y garantizan que isOverdue/isDueToday/isDueOn lo heredan.

    @Test
    fun isActive_trueForPlainTask() {
        assertTrue(TaskRules.isActive(TaskEntity(title = "Activa")))
    }

    @Test
    fun isActive_falseWhenCompleted() {
        assertFalse(TaskRules.isActive(TaskEntity(title = "Hecha", completed = true)))
    }

    @Test
    fun isActive_falseWhenArchived() {
        assertFalse(TaskRules.isActive(TaskEntity(title = "Archivada", archived = true)))
    }

    @Test
    fun isActive_falseWhenCancelled() {
        // El caso que se colaba antes de c.169/c.170.
        assertFalse(TaskRules.isActive(TaskEntity(title = "Cancelada", status = TaskStatus.CANCELLED)))
    }

    @Test
    fun isOverdue_excludesCancelledEvenIfPastDue() {
        val task = TaskEntity(title = "Vencida cancelada", dueAt = 1, status = TaskStatus.CANCELLED)
        assertFalse(TaskRules.isOverdue(task, now = 10))
    }

    @Test
    fun isDueToday_excludesCancelledEvenIfDueToday() {
        val today = LocalDate.of(2026, 7, 29)
        val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val due = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(title = "Hoy cancelada", dueAt = due, status = TaskStatus.CANCELLED)
        assertFalse(TaskRules.isDueToday(task, now, zone))
    }

    @Test
    fun isDueOn_excludesCancelledEvenIfDueOnDate() {
        val due = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val task = TaskEntity(title = "En fecha cancelada", dueAt = due, status = TaskStatus.CANCELLED)
        assertFalse(TaskRules.isDueOn(task, date, zone))
    }

    @Test
    fun nextBestTask_excludesCancelled() {
        val cancelled = TaskEntity(id = 1, title = "Cancelada", priority = TaskPriority.URGENT, status = TaskStatus.CANCELLED)
        val normal = TaskEntity(id = 2, title = "Normal", priority = TaskPriority.NORMAL)
        // Si isActive fallara, la URGENT cancelada ganaría; debe quedar excluida.
        assertEquals(normal, TaskRules.nextBestTask(listOf(cancelled, normal), now = 100))
    }

    @Test
    fun dueAtForPlannedSlot_preservaDueNulo() {
        assertNull(TaskRules.dueAtForPlannedSlot(null, 100, 200))
    }

    @Test
    fun dueAtForPlannedSlot_preservaDuePosteriorAlSlot() {
        val due = 300L
        assertEquals(due, TaskRules.dueAtForPlannedSlot(due, 100, 200))
    }

    @Test
    fun dueAtForPlannedSlot_preservaDueEnElInicioDelSlot() {
        // due == start: no viola startAt <= dueAt, se conserva intacto.
        assertEquals(100L, TaskRules.dueAtForPlannedSlot(100L, 100, 200))
    }

    @Test
    fun dueAtForPlannedSlot_mueveDueAlFinDelSlotCuandoElSlotEmpiezaDespues() {
        // due (80) < start (100): conservar el due dejaría startAt > dueAt
        // (estado que BackupManager rechaza al restaurar). El due sigue al fin del slot.
        assertEquals(200L, TaskRules.dueAtForPlannedSlot(80L, 100, 200))
    }

    @Test
    fun dueAtForPlannedSlot_nuncaProduceStartDespuesDeDue() {
        // Contrato: para cualquier due no nulo, el resultado es >= start.
        listOf(1L, 50L, 99L, 100L, 150L, 200L, 500L).forEach { due ->
            val result = TaskRules.dueAtForPlannedSlot(due, 100, 200)!!
            assertTrue("due=$due resultó en ${result} < start 100", result >= 100L)
        }
    }

    @Test
    fun coerceStartAt_preservaStartNulo() {
        assertNull(TaskRules.coerceStartAt(null, 200L))
    }

    @Test
    fun coerceStartAt_preservaStartCuandoDueEsNulo() {
        // Sin vencimiento, el inicio es libre (no hay invariante que violar).
        assertEquals(100L, TaskRules.coerceStartAt(100L, null))
    }

    @Test
    fun coerceStartAt_preservaStartCoherenteConDue() {
        // startAt <= dueAt: caso común, inalterado.
        assertEquals(100L, TaskRules.coerceStartAt(100L, 200L))
    }

    @Test
    fun coerceStartAt_preservaStartIgualADue() {
        // startAt == dueAt: límite, no viola el invariante, se conserva.
        assertEquals(200L, TaskRules.coerceStartAt(200L, 200L))
    }

    @Test
    fun coerceStartAt_descartaStartPosteriorADue() {
        // startAt > dueAt: estado que BackupManager rechaza al restaurar
        // ("Una tarea comienza después de su vencimiento" → backup irrestaurable).
        // El editor expone dueAt pero no startAt: editar el vencimiento a un instante
        // anterior al startAt heredado de la planificación dejaría startAt > dueAt.
        // Se descarta el startAt incoherente (null).
        assertNull(TaskRules.coerceStartAt(300L, 200L))
    }

    @Test
    fun coerceStartAt_nuncaProduceStartDespuesDeDue() {
        // Contrato: para cualquier (start, due) no nulos, el resultado es null o <= due.
        listOf(1L to 100L, 50L to 100L, 100L to 100L, 150L to 100L, 300L to 100L, 999L to 100L).forEach { (start, due) ->
            val result = TaskRules.coerceStartAt(start, due)
            assertTrue("start=$start due=$due resultó en ${result} > due $due", result == null || result <= due)
        }
    }

    // --- isMissedStart: recuperación de tareas con hueco planificado olvidado ---

    @Test
    fun isMissedStart_trueCuandoElHuecoPasoSinDue() {
        // start 10:00, duración 30 min → ventana hasta 10:30. now 11:00 rebasó la
        // ventana; sin dueAt no es atrasada, pero el compromiso agendado se le pasó.
        val start = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        val task = TaskEntity(id = 1, title = "Llamar médico", startAt = start, durationMinutes = 30)
        assertTrue(TaskRules.isMissedStart(task, now))
    }

    @Test
    fun isMissedStart_trueConDueFuturoAunRecuperable() {
        // Hueco pasado pero el plazo aún no vuela (due mañana): caso limpiamente
        // recuperable, es exactamente lo que este predicado debe señalar.
        val start = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        val due = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(18, 0), zone)
        val task = TaskEntity(id = 1, title = "Borrador", startAt = start, dueAt = due, durationMinutes = 30)
        assertTrue(TaskRules.isMissedStart(task, now))
    }

    @Test
    fun isMissedStart_falseCuandoAunDentroDeLaVentana() {
        // start 10:00, duración 60 min → ventana hasta 11:00. now 10:45 aún dentro:
        // sigue "en curso ahora mismo", no es un olvido.
        val start = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 45), zone)
        val task = TaskEntity(id = 1, title = "Reunión", startAt = start, durationMinutes = 60)
        assertFalse(TaskRules.isMissedStart(task, now))
    }

    @Test
    fun isMissedStart_falseCuandoElInicioEsFuturo() {
        // start futuro: programada para más tarde, el turno aún no llegó.
        val start = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val task = TaskEntity(id = 1, title = "Cita", startAt = start, durationMinutes = 30)
        assertFalse(TaskRules.isMissedStart(task, now))
    }

    @Test
    fun isMissedStart_falseCuandoEstaVencida_overdueTomaPrecedencia() {
        // Partición con isOverdue: si el due también pasó, es atrasada (señal más
        // fuerte), no missed-start. Evita doble señalización.
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val due = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        val task = TaskEntity(id = 1, title = "Vencida", startAt = start, dueAt = due, durationMinutes = 30)
        assertTrue(TaskRules.isOverdue(task, now))
        assertFalse(TaskRules.isMissedStart(task, now))
    }

    @Test
    fun isMissedStart_falseCuandoEstaMarcadaEnCurso() {
        // El usuario la puso en curso a mano: está sobre ella, no es un olvido aunque
        // la ventana planificada haya expirado.
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        val task = TaskEntity(
            id = 1, title = "Trabajándola", startAt = start, durationMinutes = 30,
            status = TaskStatus.IN_PROGRESS
        )
        assertFalse(TaskRules.isMissedStart(task, now))
    }

    @Test
    fun isMissedStart_falseParaCompletadaCanceladaArchivada() {
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        assertFalse(TaskRules.isMissedStart(TaskEntity(id = 1, title = "Hecha", startAt = start, durationMinutes = 30, completed = true), now))
        assertFalse(TaskRules.isMissedStart(TaskEntity(id = 2, title = "Cancelada", startAt = start, durationMinutes = 30, status = TaskStatus.CANCELLED), now))
        assertFalse(TaskRules.isMissedStart(TaskEntity(id = 3, title = "Archivada", startAt = start, durationMinutes = 30, archived = true), now))
    }

    @Test
    fun isMissedStart_falseCuandoNoHayStartAt() {
        // Sin hueco planificado no hay "turno" que olvidar: es una tarea de bandeja.
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        assertFalse(TaskRules.isMissedStart(TaskEntity(id = 1, title = "Inbox", durationMinutes = 25), now))
    }

    // --- isInProgressNow: coherencia con plannedDuration (fuente única de verdad) ---

    @Test
    fun isInProgressNow_ventanaCappedAMaxPlanParaDuracionOversized() {
        // "congreso 10 horas" → 600 min. El planificador (DayPlanner) y el resumen
        // (SummaryEngine) acotan a 180 min (MAX_PLAN_MINUTES). isInProgressNow debe
        // usar la misma fuente: a las 13:00 (4h tras inicio 09:00) ya rebasó el
        // bloque planificable real (09:00+180=12:00), así que NO sigue "en curso".
        // Antes del fix la ventana llegaba hasta las 19:00 y silenciaba el olvido.
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(13, 0), zone)
        val task = TaskEntity(id = 1, title = "Congreso", startAt = start, durationMinutes = 600)
        assertEquals(180, TaskRules.plannedDuration(task))
        assertFalse(TaskRules.isInProgressNow(task, now))
    }

    @Test
    fun isInProgressNow_trueDentroDelBloquePlanificableCapped() {
        // Mismo congreso 600 min: a las 11:00 sigue dentro del slot planificable
        // (09:00–12:00 tras el cap a 180). Sigue "en curso ahora mismo": el rank 5
        // de What Now y el guardián no deben señalarlo como olvido todavía.
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        val task = TaskEntity(id = 1, title = "Congreso", startAt = start, durationMinutes = 600)
        assertTrue(TaskRules.isInProgressNow(task, now))
    }

    @Test
    fun isMissedStart_trueParaOversizedTrasRebasarBloquePlanificable() {
        // Recuperación del olvido silenciado: el congreso (600→180 capped) cuyo
        // start 09:00 ya pasó y cuyo bloque 09:00–12:00 se rebasó a las 13:00,
        // sin dueAt vencido, ES un inicio olvidado recuperable. Antes del fix
        // permanecía "en curso" hasta las 19:00 y ocultaba este compromiso 7h.
        val start = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone)
        val now = DateRules.toEpochMillis(date, LocalTime.of(13, 0), zone)
        val due = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(18, 0), zone)
        val task = TaskEntity(id = 1, title = "Congreso", startAt = start, dueAt = due, durationMinutes = 600)
        assertTrue(TaskRules.isMissedStart(task, now))
    }
}
