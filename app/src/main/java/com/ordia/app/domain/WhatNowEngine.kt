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
    NEXT_INBOX,
    SCHEDULED_LATER
}

/** Resultado de "¿Qué hago ahora?": la tarea más importante para este momento. */
data class WhatNowSuggestion(
    val task: TaskEntity,
    val reason: WhatNowReason
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

    fun suggest(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): WhatNowSuggestion? {
        val chosen = ordered(tasks, now, zone).firstOrNull() ?: return null
        return WhatNowSuggestion(chosen, reason(chosen, now, zone))
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
        TaskRules.isMissedStart(task, now) -> WhatNowReason.MISSED_START
        TaskRules.isDueToday(task, now, zone) -> WhatNowReason.DUE_TODAY
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
        else -> WhatNowReason.NEXT_INBOX
    }

    private fun isInProgressNow(task: TaskEntity, now: Long): Boolean =
        TaskRules.isInProgressNow(task, now)

    /** Compromiso a punto de empezar: delega en [TaskRules.isImminentStart] (fuente única de verdad). */
    private fun isImminentStart(task: TaskEntity, now: Long): Boolean =
        TaskRules.isImminentStart(task, now)

    private fun isScheduledLater(task: TaskEntity, now: Long): Boolean =
        task.startAt != null && task.startAt > now
}
