package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneId

/** Explica por qué Ordia sugiere hacer ahora esa tarea. */
enum class WhatNowReason {
    IN_PROGRESS_NOW,
    OVERDUE,
    IMMINENT_START,
    MISSED_START,
    DUE_TODAY,
    URGENT,
    HIGH_PRIORITY,
    STALE_INBOX,
    NEXT_INBOX,
    SCHEDULED_LATER
}

/** Resultado de "¿Qué hago ahora?": la tarea más importante para este momento. */
data class WhatNowSuggestion(
    val task: TaskEntity,
    val reason: WhatNowReason,
    /**
     * Minutos aproximados hasta la SIGUIENTE cita agendada (la tarea raíz
     * activa con `startAt` futuro más próxima que NO es la sugerida). Null
     * cuando no hay ninguna, o cuando la propia sugerencia es esa cita (ya
     * empieza enseguida / está en curso: no aportaría nada repetirlo).
     *
     * Contexto honesto y determinista (no IA): "¿qué hago ahora?" gana una
     * segunda pista —"te quedan ~20 min hasta tu reunión de las 15:00"— para
     * que el usuario elija una tarea que QUEPA en ese hueco en vez de arrancar
     * algo que la cita interrumpirá. Potencia sin nueva pantalla/botón: la
     * tarjeta ya existe, sólo añade una línea secundaria cuando de verdad
     * ayuda (hay un compromiso cercano y no es la sugerida). Se acota a un
     * horizonte razonable para no invitar a planificar lejos desde "ahora".
     * Fuente única de verdad: `startAt` de las propias tareas activas.
     */
    val minutesUntilNextCommitment: Int? = null
)

/**
 * Decide qué tarea conviene hacer ahora mismo de forma determinista y local.
 *
 * Orden de prioridad (explicable al usuario):
 * 1. Tarea en curso ahora mismo (startAt <= ahora <= fin estimado).
 * 2. Tareas atrasadas (dueAt anterior a ahora).
 * 3. Tareas que vencen hoy.
 * 4. Urgentes sin fecha.
 * 5. Alta prioridad sin fecha.
 * 6. Primera de la Bandeja.
 * Las tareas ya programadas para más tarde (startAt futuro) se respetan y se
 * sugieren solo si no hay otra cosa pendiente.
 * Desempates: prioridad, fecha límite más próxima, hora prevista, orden y creación
 * (mismo criterio que [TaskRules.nextBestTask], para que What Now y el widget
 * sugieran exactamente la misma tarea).
 */
object WhatNowEngine {

    /** Horizonte (min) hasta el que se muestra "te quedan N min hasta tu próxima cita". */
    private const val NEXT_COMMITMENT_WINDOW_MINUTES = 4 * 60

    fun suggest(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): WhatNowSuggestion? {
        val chosen = ordered(tasks, now, zone).firstOrNull() ?: return null
        return WhatNowSuggestion(chosen, reason(chosen, now, zone), minutesUntilNextCommitment(tasks, chosen, now))
    }

    /**
     * Ranking determinista y local de todas las tareas candidatas (la misma
     * ordenación que usa [suggest], sin elegir una sola). Punto único para que
     * el asistente, el plan mínimo y el widget muestren el mismo orden.
     */
    fun ordered(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TaskEntity> =
        tasks.filter { isCandidate(it) }.sortedWith(
            compareByDescending<TaskEntity> { TaskRules.timeRank(it, now, zone) }
                .thenByDescending { TaskRules.priorityScore(it.priority) }
                .thenByDescending { TaskRules.isMissedStart(it, now) }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { it.startAt ?: Long.MAX_VALUE }
                .thenBy { it.sortOrder }
                .thenBy { it.createdAt }
        )

    /** Frase corta y honesta que explica por qué esta tarea va primero. */
    fun reasonLabel(r: WhatNowReason): String = when (r) {
        WhatNowReason.IN_PROGRESS_NOW -> "ya está en curso"
        WhatNowReason.OVERDUE -> "está vencida"
        WhatNowReason.IMMINENT_START -> "empieza enseguida"
        WhatNowReason.MISSED_START -> "tenía su hueco y se pasó"
        WhatNowReason.DUE_TODAY -> "vence hoy"
        WhatNowReason.URGENT -> "es urgente"
        WhatNowReason.HIGH_PRIORITY -> "es prioritaria"
        WhatNowReason.STALE_INBOX -> "lleva una semana o más en la bandeja sin agendar"
        WhatNowReason.SCHEDULED_LATER -> "está programada para más tarde"
        WhatNowReason.NEXT_INBOX -> "es lo siguiente de la bandeja"
    }

    private fun isCandidate(task: TaskEntity): Boolean =
        TaskRules.isActive(task) && task.parentTaskId == null

    private fun reason(task: TaskEntity, now: Long, zone: ZoneId): WhatNowReason = when {
        task.status == TaskStatus.IN_PROGRESS -> WhatNowReason.IN_PROGRESS_NOW
        isInProgressNow(task, now) -> WhatNowReason.IN_PROGRESS_NOW
        TaskRules.isOverdue(task, now) -> WhatNowReason.OVERDUE
        isImminentStart(task, now) -> WhatNowReason.IMMINENT_START
        // isDueToday va ANTES que isMissedStart (c.373), en sintonía con
        // [TaskRules.timeRank] donde isDueToday (rank 3) es rango explícito y
        // isMissedStart cae a prioridad (0/1/2). Una tarea que falló su arranque
        // (startAt pasado) PERO vence hoy se eleva por isDueToday — por encima de
        // una URGENTE pura (rank 2)—; antes reason() evaluaba isMissedStart primero
        // y mostraba "se pasó el arranque", ocultando que además vence hoy: el dato
        // que justifica que vaya primera. Divergencia etiqueta ↔ ranking e IA
        // deshonesta (misma clase que c.372). El label honesto es "vence hoy".
        // El missed-start puro (sin plazo de hoy) sigue cayendo a MISSED_START más
        // abajo, recuperando el arranque olvidado como en c.202.
        TaskRules.isDueToday(task, now, zone) -> WhatNowReason.DUE_TODAY
        TaskRules.isMissedStart(task, now) -> WhatNowReason.MISSED_START
        // isScheduledLater va ANTES que la prioridad (c.372), en sintonía con
        // [TaskRules.timeRank] donde isScheduledLater (-1) se evalúa antes que
        // URGENT/HIGH: una tarea programada para empezar más tarde queda enterrada
        // bajo el inbox aunque sea urgente — el usuario le dio un hueco futuro y
        // Ordía respeta esa decisión. Antes reason() comprobaba la prioridad primero
        // y mostraba "es urgente" para una tarea que el ranking hundía: contradicción
        // etiqueta ↔ ranking e IA deshonesta (animaba a hacerla ahora contra la propia
        // planificación). Si llega a sugerirse (único candidato), el label honesto es
        // "está programada para más tarde".
        isScheduledLater(task, now) -> WhatNowReason.SCHEDULED_LATER
        task.priority == TaskPriority.URGENT -> WhatNowReason.URGENT
        task.priority == TaskPriority.HIGH -> WhatNowReason.HIGH_PRIORITY
        // Una captura de la bandeja arrinconada (sin dueAt/startAt y ≥ 7 días
        // esperando) se etiqueta como olvidada antes de caer al neutro NEXT_INBOX:
        // What Now es la superficie principal de «haz esto ahora» y ocultar el
        // tercer olvido bajo «es lo siguiente de la bandeja» subestimaba el riesgo
        // de perderla del todo. Coincide con [TaskRules.timeRank] (rank 0: stale o
        // inbox caen al mismo escalón; aquí desempata la honestidad del motivo).
        // Urgente/Alta ya se etiquetaron arriba: la prioridad explícita del usuario
        // prevalece sobre la antigüedad para una captura priorizada.
        TaskRules.isStaleInbox(task, now, zone) -> WhatNowReason.STALE_INBOX
        else -> WhatNowReason.NEXT_INBOX
    }

    private fun isInProgressNow(task: TaskEntity, now: Long): Boolean =
        TaskRules.isInProgressNow(task, now)

    /** Compromiso a punto de empezar: delega en [TaskRules.isImminentStart] (fuente única de verdad). */
    private fun isImminentStart(task: TaskEntity, now: Long): Boolean =
        TaskRules.isImminentStart(task, now)

    private fun isScheduledLater(task: TaskEntity, now: Long): Boolean =
        task.startAt != null && task.startAt > now

    /**
     * Minutos hasta la siguiente cita agendada (raíz activa con `startAt`
     * futuro) que NO sea [suggested]. Devuelve null si no hay ninguna en los
     * próximos [NEXT_COMMITMENT_WINDOW_MINUTES], o si la única cita cercana es
     * la propia [suggested] (ya es la sugerencia: repetir "te queda X hasta
     * ella" no aporta nada). Raíces, igual que [ordered]/[isCandidate]: las
     * subtareas anidadas no son "citas" del usuario. Determinista, sin random.
     */
    private fun minutesUntilNextCommitment(
        tasks: List<TaskEntity>,
        suggested: TaskEntity,
        now: Long
    ): Int? {
        val nextStart = tasks
            .asSequence()
            .filter { TaskRules.isActive(it) && it.parentTaskId == null && it.id != suggested.id }
            .mapNotNull { it.startAt }
            .filter { it > now }
            .minOrNull()
            ?: return null
        val minutes = ((nextStart - now) / 60_000L).toInt()
        // Redondea a múltiplo de 5 min: la cifra es orientativa ("≈N min"),
        // no un cronómetro exacto; evitar precisión falsa es más honesto.
        val rounded = (minutes / 5) * 5
        return if (rounded in 1..NEXT_COMMITMENT_WINDOW_MINUTES) rounded else null
    }
}
