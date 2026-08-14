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
}
