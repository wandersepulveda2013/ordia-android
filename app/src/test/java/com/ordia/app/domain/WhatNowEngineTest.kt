package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WhatNowEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 7, 29)
    private val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)

    private fun task(id: Long, title: String, priority: TaskPriority = TaskPriority.NORMAL) =
        TaskEntity(id = id, title = title, priority = priority)

    @Test
    fun suggestsTaskHappeningNowFirst() {
        val inProgress = task(1, "En curso").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone),
            durationMinutes = 60
        )
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, inProgress), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.IN_PROGRESS_NOW, suggestion.reason)
    }

    @Test
    fun explicitlyStartedTaskWinsWithoutScheduledStart() {
        val started = task(1, "Ya iniciada").copy(status = TaskStatus.IN_PROGRESS)
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, started), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.IN_PROGRESS_NOW, suggestion.reason)
    }

    @Test
    fun prefersOverdueOverUrgentWithoutDate() {
        val overdue = task(1, "Atrasada").copy(dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone))
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, overdue), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.OVERDUE, suggestion.reason)
    }

    @Test
    fun prefersDueTodayOverUrgentWithoutDate() {
        val dueToday = task(1, "Vence hoy").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, dueToday), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
    }

    @Test
    fun picksEarliestDueAmongSameReason() {
        val later = task(1, "Vence tarde").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))
        val earlier = task(2, "Vence pronto").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone))

        val suggestion = WhatNowEngine.suggest(listOf(later, earlier), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
    }

    @Test
    fun picksUrgentOverNormalAmongOverdue() {
        // Dos tareas atrasadas: la NORMAL vence ANTES que la URGENTE.
        // Antes del fix, What Now sugería la normal por su fecha más próxima,
        // mientras el widget (nextBestTask) sugería la urgente. Ahora coinciden:
        // la prioridad desempata antes que la fecha, igual que nextBestTask.
        val normalEarlier = task(1, "Atrasada normal").copy(
            dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(9, 0), zone)
        )
        val urgentLater = task(2, "Atrasada urgente", TaskPriority.URGENT).copy(
            dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(10, 0), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(normalEarlier, urgentLater), now = now, zone = zone)
        val widget = TaskRules.nextBestTask(listOf(normalEarlier, urgentLater), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.OVERDUE, suggestion.reason)
        assertEquals(suggestion.task.id, widget?.id)
    }

    @Test
    fun urgentReasonWhenOnlyUrgentPending() {
        val urgent = task(1, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.URGENT, suggestion.reason)
    }

    @Test
    fun inboxIsLastResort() {
        val inbox = task(1, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(inbox), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    @Test
    fun returnsNullWhenNothingPending() {
        val done = task(1, "Hecha").copy(completed = true, status = TaskStatus.COMPLETED)
        val archived = task(2, "Archivada").copy(archived = true)
        val cancelled = task(3, "Cancelada").copy(status = TaskStatus.CANCELLED)
        val subtask = task(4, "Sub").copy(parentTaskId = 1L)

        assertNull(WhatNowEngine.suggest(listOf(done, archived, cancelled, subtask), now = now, zone = zone))
    }

    @Test
    fun ignoresTaskScheduledLaterToday() {
        val later = task(1, "Más tarde").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )
        val inbox = task(2, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(later, inbox), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    @Test
    fun imminentStartSurfacesAboveInbox() {
        // Reunión/cita que empieza en 5 min: ahora = 10:00, start = 10:05.
        val imminent = task(1, "Reunión inminente").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 5), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone)
        )
        val inbox = task(2, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(imminent, inbox), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.IMMINENT_START, suggestion.reason)
    }

    @Test
    fun startOutsideImminentWindowStillDeprioritized() {
        // Empieza en 30 min (> ventana de 15): sigue como último recurso frente a la Bandeja.
        val soonButNotImminent = task(1, "En 30 min").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        )
        val inbox = task(2, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(soonButNotImminent, inbox), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    @Test
    fun overdueStillBeatsImminentStart() {
        // Una tarea atrasada (ya pasó su hora) tiene prioridad sobre un compromiso que aún no empieza.
        val overdue = task(1, "Atrasada").copy(dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone))
        val imminent = task(2, "Reunión inminente").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 5), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(imminent, overdue), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.OVERDUE, suggestion.reason)
    }

    @Test
    fun imminentStartBeatsDueTodayWithoutStart() {
        val imminent = task(1, "Reunión inminente").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 5), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone)
        )
        val dueToday = task(2, "Vence hoy").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))

        val suggestion = WhatNowEngine.suggest(listOf(dueToday, imminent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.IMMINENT_START, suggestion.reason)
    }

    @Test
    fun scheduledLaterButDueTodayLabeledDueTodayNotScheduledLater() {
        // Tarea programada para empezar a las 15:00 pero que VENCE hoy a las 16:00,
        // siendo la única pendiente (ahora = 10:00). El ranking la elige; la etiqueta
        // debe reflejar la urgencia real ("vence hoy"), no "programada para más tarde".
        // Antes, reason() comprobaba isScheduledLater antes que isDueToday y mostraba
        // "está programada para más tarde" para una tarea que vence hoy: contradictorio
        // y alineado con un ranking (timeRank) que ya la considera prioritaria.
        val dueTodayScheduledLater = task(1, "Reunión tarde").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(dueTodayScheduledLater), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
    }

    /**
     * Guardián de divergencia: What Now (tarjeta/asistente) y el widget
     * (TaskRules.nextBestTask) DEBEN sugerir exactamente la misma tarea para
     * el mismo conjunto, en cualquier instante. Antes de consolidar
     * [TaskRules.timeRank] como fuente única de verdad, el ranking temporal
     * vivía duplicado (privado) en WhatNowEngine y TaskRules; cualquier edición
     * en uno los hacía discrepar silenciosamente (regresión documentada en
     * c.83). Este test falla si vuelven a divergir.
     */
    @Test
    fun whatNowAndWidgetAgreeOnBestTaskAcrossTime() {
        val diverse = listOf(
            task(1, "Atrasada normal").copy(
                dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone),
                durationMinutes = 30
            ),
            task(2, "En curso por estado").copy(
                startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
                durationMinutes = 60,
                status = TaskStatus.IN_PROGRESS
            ),
            task(3, "Inminente").copy(
                startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 8), zone),
                durationMinutes = 20, priority = TaskPriority.LOW
            ),
            task(4, "Programada más tarde").copy(
                startAt = DateRules.toEpochMillis(date, LocalTime.of(17, 0), zone),
                durationMinutes = 20, priority = TaskPriority.URGENT
            ),
            task(5, "Vence hoy").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone)),
            task(6, "Urgente sin fecha", TaskPriority.URGENT),
            task(7, "Alta sin fecha", TaskPriority.HIGH),
            task(8, "Bandeja"),
            task(9, "Subtarea urgente", TaskPriority.URGENT).copy(parentTaskId = 1L)
        )
        val checkPoints = listOf(
            DateRules.toEpochMillis(date, LocalTime.of(8, 0), zone),
            now,
            DateRules.toEpochMillis(date, LocalTime.of(10, 6), zone),
            DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone),
            DateRules.toEpochMillis(date, LocalTime.of(20, 0), zone)
        )
        checkPoints.forEach { instant ->
            val whatNowTop = WhatNowEngine.ordered(diverse, now = instant, zone = zone).firstOrNull()?.id
            val widgetTop = TaskRules.nextBestTask(diverse, now = instant, zone = zone)?.id
            assertEquals("What Now y widget divergen en $instant", widgetTop, whatNowTop)
        }
    }

    // --- c.202: recuperación de inicio olvidado en "¿Qué hago ahora?" ---

    @Test
    fun missedStartIsLabeledHonestlyNotInbox() {
        // Hueco 09:00–09:30 (duración 30), ya = 10:00: el compromiso se le pasó sin
        // deadline vencido. Antes se etiquetaba como "bandeja"/"urgente"; ahora explica
        // la verdad: el hueco planificado se pasó.
        val missed = task(1, "Llamada agendada").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            durationMinutes = 30
        )

        val suggestion = WhatNowEngine.suggest(listOf(missed), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.MISSED_START, suggestion.reason)
        assertEquals("tenía su hueco y se pasó", WhatNowEngine.reasonLabel(suggestion.reason))
    }

    @Test
    fun missedStartSurfacesAboveEqualPriorityInbox() {
        // Misma prioridad NORMAL, misma banda de urgencia (rank 0). El compromiso cuyo
        // hueco pasó debe ir primero (el usuario le dio hora y se le olvidó), por delante
        // de una tarea de bandeja sin planificación. Antes competían por orden/creación.
        val missed = task(1, "Olvidada agendada").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            durationMinutes = 30,
            createdAt = now + 1_000
        )
        val inbox = task(2, "Bandeja").copy(createdAt = now)

        val ordered = WhatNowEngine.ordered(listOf(inbox, missed), now = now, zone = zone)

        assertEquals(1L, ordered.first().id)
    }

    @Test
    fun missedStartDoesNotOverrideHigherPriority() {
        // Un compromiso olvidado NORMAL NO debe saltarse a una URGENTE sin fecha: la
        // importancia (prioridad) sigue mandando sobre el hueco olvidado. El desempate
        // sólo actúa dentro de la misma banda de urgencia + prioridad.
        val missed = task(1, "Olvidada normal agendada").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            durationMinutes = 30
        )
        val urgent = task(2, "Urgente sin fecha", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(missed, urgent), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.URGENT, suggestion.reason)
    }

    @Test
    fun overdueBeatsMissedStart() {
        // Plazo incumplido (dueAt vencido) manda sobre hueco olvidado: la vencida es
        // isOverdue (rank 4); la de inicio olvidado sin deadline vencido decae a su
        // prioridad. La vencida va primero y se etiqueta como tal.
        val overdue = task(1, "Vencida").copy(dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone))
        val missed = task(2, "Hueco olvidado").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            durationMinutes = 30
        )

        val suggestion = WhatNowEngine.suggest(listOf(missed, overdue), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.OVERDUE, suggestion.reason)
    }

    @Test
    fun whatNowAndWidgetAgreeOnMissedStartTiebreak() {
        // Dentro de la misma banda, What Now y el widget deben elegir el mismo compromiso
        // olvidado (c.83: no divergir). Ambos comparten el desempate isMissedStart.
        val missed = task(1, "Olvidada agendada").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            durationMinutes = 30,
            createdAt = now + 1_000
        )
        val inbox = task(2, "Bandeja").copy(createdAt = now)

        val whatNowTop = WhatNowEngine.ordered(listOf(inbox, missed), now = now, zone = zone).firstOrNull()?.id
        val widgetTop = TaskRules.nextBestTask(listOf(inbox, missed), now = now, zone = zone)?.id

        assertEquals(whatNowTop, widgetTop)
        assertEquals(1L, widgetTop)
    }

}
