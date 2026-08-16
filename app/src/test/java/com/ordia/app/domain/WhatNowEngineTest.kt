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

}
