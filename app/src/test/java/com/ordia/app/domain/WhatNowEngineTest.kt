package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun dueTodayScheduledLaterStillBeatsInbox() {
        // start 15:00, vence 16:00 HOY (now=10:00): aunque empiece más tarde,
        // TIENE plazo de hoy y debe quedar por encima del inbox sin fecha — de lo
        // contrario el usuario puede olvidar un compromiso de hoy. Antes esta tarea
        // se hundía a rank -1 por isScheduledLater evaluado antes que isDueToday
        // (c.363 corrige el orden); ahora reason() y timeRank coinciden en DUE_TODAY.
        val later = task(1, "Más tarde pero vence hoy").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )
        val inbox = task(2, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(later, inbox), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
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
    fun dueTodayOutsideImminentWindowStillSurfacesAboveInbox() {
        // Empieza en 30 min (> ventana inminente de 15) pero VENCE hoy (11:00):
        // al tener plazo de hoy se mantiene por encima del inbox. Antes se hundía
        // a último recurso (-1) por isScheduledLater evaluado antes que isDueToday;
        // c.363 invierte el orden para evitar olvidar compromisos con plazo de hoy.
        val soonButNotImminent = task(1, "En 30 min, vence hoy").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone)
        )
        val inbox = task(2, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(soonButNotImminent, inbox), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
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
     * Guardián de priorización: una tarea que VENCE HOY pero está programada para
     * empezar más tarde (startAt futuro, dueAt hoy) DEBE quedar por encima de una
     * captura del inbox sin fecha. Antes, [TaskRules.timeRank] evaluaba
     * `isScheduledLater` ANTES que `isDueToday`, así que la tarea vencida-hoy
     * recibía rank -1 (último recurso) mientras el inbox sin fecha recibía rank 0:
     * What Now sugería una idea aleatoria en lugar de la que vence hoy — el usuario
     * podía olvidar una tarea con plazo de hoy (inconsistencia entre la etiqueta,
     * que ya decía "vence hoy", y el ranking, que la hundía). El fix alinea el
     * orden de timeRank con el de reason(): isDueToday por encima de isScheduledLater.
     */
    @Test
    fun dueTodayScheduledLaterBeatsInboxWithoutDate() {
        val dueTodayScheduledLater = task(1, "Reunión tarde").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )
        val inbox = task(2, "Idea sin fecha")

        val suggestion = WhatNowEngine.suggest(listOf(inbox, dueTodayScheduledLater), now = now, zone = zone)

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

    // --- c.364: paridad etiqueta ↔ ranking para "programada para más tarde" ---

    /**
     * Guardián de paridad etiqueta ↔ ranking (c.364). Una tarea programada para
     * empezar más tarde (startAt futuro) Y urgente, sin vencimiento hoy, queda
     * enterrada bajo el inbox en [TaskRules.timeRank] (isScheduledLater -> -1 se
     * evalúa antes que URGENT -> 2): el usuario le dio un hueco futuro y Ordía
     * respeta esa decisión. Cuando hay una captura del inbox sin fecha, What Now
     * debe sugerir ésta (rank 0 > -1), NO la programada-urgente.
     */
    @Test
    fun scheduledLaterUrgentBuriedBelowInboxEvenWhenUrgent() {
        val scheduledUrgent = task(1, "Reunión tarde urgente", TaskPriority.URGENT).copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone)
        )
        val inbox = task(2, "Idea sin fecha")

        val suggestion = WhatNowEngine.suggest(listOf(scheduledUrgent, inbox), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    /**
     * c.364: si la única pendiente es una tarea programada para más tarde (aunque
     * sea urgente), el label honesto es "está programada para más tarde", NO "es
     * urgente". Antes reason() comprobaba la prioridad antes que isScheduledLater y
     * mostraba "es urgente" para una tarea que el ranking hundía por programada —
     * contradicción etiqueta ↔ ranking e IA deshonesta (animaba a hacerla ahora
     * contra la propia planificación del usuario).
     */
    @Test
    fun scheduledLaterUrgentLabeledScheduledLaterNotUrgent() {
        val scheduledUrgent = task(1, "Reunión tarde urgente", TaskPriority.URGENT).copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(scheduledUrgent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.SCHEDULED_LATER, suggestion.reason)
        assertEquals("está programada para más tarde", WhatNowEngine.reasonLabel(suggestion.reason))
    }

    /**
     * c.364: ídem para prioridad ALTA programada para más tarde.
     */
    @Test
    fun scheduledLaterHighLabeledScheduledLaterNotHighPriority() {
        val scheduledHigh = task(1, "Reunión tarde alta", TaskPriority.HIGH).copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(scheduledHigh), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.SCHEDULED_LATER, suggestion.reason)
    }

    /**
     * c.364: la prioridad sigue mandando cuando NO hay startAt futuro. Una tarea
     * urgente sin programar (bandeja) se sugiere y se etiqueta como urgente — el
     * reordenamiento de isScheduledLater en reason() NO degrada la prioridad pura.
     */
    @Test
    fun urgentWithoutScheduledStartStillLabeledUrgent() {
        val urgent = task(1, "Urgente sin fecha", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.URGENT, suggestion.reason)
    }

    /**
     * c.373: paridad etiqueta↔ranking cuando una tarea falló su arranque
     * (startAt pasado) PERO vence hoy (dueAt hoy, futuro). timeRank() la eleva
     * por isDueToday (rank 3) —por encima de una URGENTE pura (rank 2)— pero
     * reason() la etiquetaba MISSED_START ("se pasó el arranque") porque
     * evaluaba isMissedStart ANTES que isDueToday, contradiciendo el orden de
     * timeRank() (donde isDueToday(3) es rango explícito y isMissedStart cae a
     * prioridad 0/1/2). El usuario veía "se pasó el hueco" sin enterarse de que
     * además vence hoy — el dato que justifica que vaya primera. Divergencia
     * etiqueta↔ranking, misma clase que c.372. Fix: reason() evalúa isDueToday
     * ANTES que isMissedStart, igualando el orden de timeRank(). El label honesto
     * pasa a "vence hoy".
     */
    @Test
    fun missedStartButDueTodayLabeledDueTodayNotMissedStart() {
        // Hueco 09:00 (ya pasado, now=10:00), plazo hoy 18:00 (futuro, dueToday).
        val missedAndDue = task(1, "Llamada con deadline hoy").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone),
            durationMinutes = 30
        )

        val suggestion = WhatNowEngine.suggest(listOf(missedAndDue), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
        assertEquals("vence hoy", WhatNowEngine.reasonLabel(suggestion.reason))
    }

    /**
     * c.373: confirmación de que el ranking coincide con el label corregido.
     * La tarea missed-start+dueToday (rank 3) debe ir por delante de una URGENTE
     * pura sin fecha (rank 2) — porque vence hoy — y la sugerencia etiqueta su
     * razón como DUE_TODAY, no como URGENT ni MISSED_START.
     */
    @Test
    fun missedStartDueTodayBeatsUrgentAndLabeledDueToday() {
        val missedAndDue = task(1, "Con plazo hoy").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone),
            durationMinutes = 30
        )
        val urgent = task(2, "Urgente sin fecha", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(missedAndDue, urgent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
    }

    /**
     * c.373: el caso missed-start puro (sin dueAt, o dueAt futuro no-hoy) sigue
     * etiquetándose como MISSED_START — el reordenamiento de isDueToday no
     * degrada la recuperación de inicio olvidado cuando NO hay plazo de hoy.
     * Regresión de c.202.
     */
    @Test
    fun pureMissedStartWithoutDueTodayStillLabeledMissedStart() {
        val missed = task(1, "Hueco olvidado sin deadline").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 0), zone),
            durationMinutes = 30
        )

        val suggestion = WhatNowEngine.suggest(listOf(missed), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.MISSED_START, suggestion.reason)
    }

    /**
     * c.419: una captura de la bandeja arrinconada (≥ 7 días sin dueAt ni
     * startAt, prioridad NORMAL) se etiqueta como STALE_INBOX en What Now, no
     * como el neutro NEXT_INBOX. What Now es la superficie principal de «haz
     * esto ahora»: antes ocultaba el tercer olvido bajo «es lo siguiente de la
     * bandeja», subestimando el riesgo de perder la idea del todo.
     */
    @Test
    fun staleInboxCaptureLabeledStaleNotNeutralInbox() {
        val stale = task(1, "Idea arrinconada").copy(createdAt = now - 10 * 86_400_000L)

        val suggestion = WhatNowEngine.suggest(listOf(stale), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.STALE_INBOX, suggestion.reason)
    }

    /**
     * c.419: una captura FRESCA (creada hoy, sin fecha) sigue siendo
     * NEXT_INBOX — la etiqueta de olvido no debe dispararse para capturas
     * que aún no han envejecido (anti falso positivo).
     */
    @Test
    fun freshInboxCaptureStillNeutralNextInbox() {
        val fresh = task(1, "Idea nueva").copy(createdAt = now)

        val suggestion = WhatNowEngine.suggest(listOf(fresh), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    /**
     * c.419: una captura arrinconada marcada URGENTE se etiqueta URGENT, no
     * STALE_INBOX — la prioridad explícita del usuario prevalece sobre la
     * antigüedad; el olvido ya está cubierto por la etiqueta de urgencia.
     */
    @Test
    fun urgentStaleCaptureLabeledUrgentNotStale() {
        val staleUrgent = task(1, "Idea urgente y vieja", TaskPriority.URGENT)
            .copy(createdAt = now - 30 * 86_400_000L)

        val suggestion = WhatNowEngine.suggest(listOf(staleUrgent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.URGENT, suggestion.reason)
    }

    /**
     * c.419: reasonLabel(STALE_INBOX) describe honestamente el olvido (menciona
     * «bandeja»), para que el asistente («qué hago ahora?») no llame IA a
     * un mero «es lo siguiente» cuando en realidad está recuperando una
     * captura a punto de perderse.
     */
    @Test
    fun staleInboxReasonLabelIsHonest() {
        val label = WhatNowEngine.reasonLabel(WhatNowReason.STALE_INBOX)

        assertTrue(label.contains("bandeja"))
    }

    // --- c.551: contexto "te quedan ~N min hasta tu próxima cita" ---

    /**
     * Cuando lo sugerido es una tarea del inbox y hay una cita agendada a 20 min,
     * What Now aporta el contexto: 20 min (redondeado a 5) hasta esa cita.
     * Ayuda a elegir una tarea que quepa en el hueco en vez de arrancar algo
     * que la cita interrumpirá. Determinista, no IA.
     */
    @Test
    fun contextMinutesUntilNextCommitmentWhenFutureCitationExists() {
        val inbox = task(1, "Idea").copy(createdAt = now)
        val meeting = task(2, "Reunión").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 20), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(inbox, meeting), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(20, suggestion.minutesUntilNextCommitment)
    }

    /** Sin ninguna cita futura, no hay contexto que aportar → null (sin ruido). */
    @Test
    fun contextMinutesUntilNextCommitmentNullWhenNoFutureStart() {
        // Una atrasada (rank 4) gana la sugerencia; no aporta startAt futuro →
        // no hay "próxima cita" → null. Lo que se verifica es el contexto, no quién gana.
        val urgent = task(1, "Urgente", TaskPriority.URGENT)
        val overdue = task(2, "Atrasada")
            .copy(dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone))

        val suggestion = WhatNowEngine.suggest(listOf(urgent, overdue), now = now, zone = zone)

        assertNull(suggestion!!.minutesUntilNextCommitment)
    }

    /**
     * Si la propia sugerencia ES la cita más cercana (p. ej. inminente/programada
     * y no hay nada más urgente que la eleve a primera), no se repite "te queda
     * X hasta ella": null. La sugerencia ya es esa cita.
     */
    @Test
    fun contextMinutesNullWhenSuggestedTaskIsTheNextCommitment() {
        val meeting = task(1, "Reunión").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 20), zone)
        )
        // Sin inbox/urgente que la supere: la cita programada para HOY temprano
        // es la candidata (startAt futuro cercano). No hay OTRA cita, y aunque la
        // hubiera, la sugerida se excluye de su propio conteo.
        val suggestion = WhatNowEngine.suggest(listOf(meeting), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertNull(suggestion.minutesUntilNextCommitment)
    }

    /** Entre varias citas futuras, queda la MÁS próxima (mínimo startAt). */
    @Test
    fun contextMinutesUsesSoonestFutureCommitment() {
        // Urgente sin fechas (rank 2) gana la sugerencia; dos citas programadas
        // (rank -1, "más tarde") compiten como "próxima cita" → gana la cercana.
        val urgent = task(1, "Urgente", TaskPriority.URGENT)
        val soon = task(2, "Próxima").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 30), zone)
        )
        val later = task(3, "Lejana").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(11, 30), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(urgent, soon, later), now = now, zone = zone)

        // Sugerencia = urgente (rank superior); contexto = cita más cercana (30 min).
        assertEquals(1L, suggestion!!.task.id)
        assertEquals(30, suggestion.minutesUntilNextCommitment)
    }

    /**
     * Las subtareas (parentTaskId != null) NO son "citas" del usuario: aunque
     * tengan startAt futuro, no deben contar como próxima cita (paridad con
     * [isCandidate]/ordered, que sólo consideran raíces). Aquí la única cita
     * "futura" es una subtarea → null.
     */
    @Test
    fun contextMinutesIgnoresSubtasksAsCommitments() {
        val inbox = task(1, "Idea").copy(createdAt = now)
        val subtask = task(2, "Subtarea cita").copy(
            parentTaskId = 99L,
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 20), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(inbox, subtask), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertNull(suggestion.minutesUntilNextCommitment)
    }

    /**
     * Fuera del horizonte útil (más de 4 h) no se muestra: desde "¿qué hago
     * ahora?" una cita dentro de 5 h no cambia la decisión inmediata y sólo
     * añadiría ruido → null. Evita invitar a planificar lejos desde esta tarjeta.
     */
    @Test
    fun contextMinutesNullBeyondUsefulHorizon() {
        val inbox = task(1, "Idea").copy(createdAt = now)
        val farMeeting = task(2, "Reunión lejana").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 30), zone) // 5h30
        )

        val suggestion = WhatNowEngine.suggest(listOf(inbox, farMeeting), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertNull(suggestion.minutesUntilNextCommitment)
    }

    /**
     * Redondeo honesto a múltiplo de 5: 22 min reales → 20. La cifra es
     * orientativa ("≈N min"), no un cronómetro; la precisión falsa molesta.
     */
    @Test
    fun contextMinutesRoundedToFiveForHonesty() {
        val inbox = task(1, "Idea").copy(createdAt = now)
        val meeting = task(2, "Reunión").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 22), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(inbox, meeting), now = now, zone = zone)

        assertEquals(20, suggestion!!.minutesUntilNextCommitment)
    }

    /**
     * Hueco inminente (< 5 min): una cita que empieza en 3 min NO se trunca a
     * 0 (lo que la descartaba y silenciaba el aviso). Es justo la cita que el
     * usuario necesita ver: si la sugerida dura más de 3 min, la cita la
     * interrumpirá. Para huecos inminentes se conserva el valor exacto (la
     * precisión importa al decidir ahora); para >= 5 min sigue el truncado a
     * múltiplo de 5. PRE-fix este test daba null (truncado 3→0→fuera de `1..`).
     */
    @Test
    fun contextMinutesSurfacesImminentCommitmentUnderFiveMin() {
        // Tarea en curso (rank 5/6) gana la sugerencia; su startAt 9:30 ya
        // pasó y NO cuenta como "próxima cita". La cita a las 10:03 (3 min,
        // inminente, rank 4) queda como próxima cita → 3 min exacto.
        val inProgress = task(1, "En curso").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone),
            durationMinutes = 60
        )
        val imminent = task(2, "Cita en 3 min").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 3), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(inProgress, imminent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(3, suggestion.minutesUntilNextCommitment)
    }

    /**
     * 4 min también es inminente y se conserva exacto (no se trunca a 0).
     * Límite inferior del comportamiento corregido.
     */
    @Test
    fun contextMinutesSurfacesFourMinCommitment() {
        val inProgress = task(1, "En curso").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone),
            durationMinutes = 60
        )
        val imminent = task(2, "Cita en 4 min").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 4), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(inProgress, imminent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(4, suggestion.minutesUntilNextCommitment)
    }

    /**
     * 5 min es el primer valor truncado a múltiplo de 5: 5→5 (sin cambio).
     * Confirma la frontera entre "exacto" (<5) y "truncado" (>=5).
     */
    @Test
    fun contextMinutesFiveMinStillRoundedToFive() {
        val inProgress = task(1, "En curso").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone),
            durationMinutes = 60
        )
        val meeting = task(2, "Cita en 5 min").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 5), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(inProgress, meeting), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(5, suggestion.minutesUntilNextCommitment)
    }

    /**
     * Una cita en el pasado (startAt ya ocurrido, p. ej. missed-start) NO es
     * "próxima cita": ya empezó. No debe contar → aquí la única startAt es
     * pasada, así que null (la tarea missed-start puede ser la sugerida, pero
     * su hueco ya pasó y no aporta "te queda X hasta ella").
     */
    @Test
    fun contextMinutesExcludesPastStartAt() {
        val missed = task(1, "Se me pasó").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(8, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone)
        )

        val suggestion = WhatNowEngine.suggest(listOf(missed), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertNull(suggestion.minutesUntilNextCommitment)
    }

}
