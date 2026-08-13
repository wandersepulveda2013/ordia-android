package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Explica por qué Ordia sugiere hacer ahora esa tarea. */
enum class WhatNowReason {
    IN_PROGRESS_NOW,
    OVERDUE,
    IMMINENT_START,
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
 * Desempates: fecha límite más próxima, hora prevista, orden y creación.
 */
object WhatNowEngine {

    fun suggest(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): WhatNowSuggestion? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val candidates = tasks.filter { isCandidate(it) }
        if (candidates.isEmpty()) return null
        val chosen = candidates.sortedWith(
            compareByDescending<TaskEntity> { rank(it, now, today, zone) }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { it.startAt ?: Long.MAX_VALUE }
                .thenBy { it.sortOrder }
                .thenBy { it.createdAt }
        ).first()
        return WhatNowSuggestion(chosen, reason(chosen, now, today, zone))
    }

    private fun isCandidate(task: TaskEntity): Boolean =
        !task.completed && !task.archived && task.status != TaskStatus.CANCELLED && task.parentTaskId == null

    private fun rank(task: TaskEntity, now: Long, today: LocalDate, zone: ZoneId): Int = when {
        task.status == TaskStatus.IN_PROGRESS -> 6
        isInProgressNow(task, now) -> 5
        TaskRules.isOverdue(task, now) -> 4
        isImminentStart(task, now) -> 4
        isScheduledLater(task, now) -> -1
        isDueToday(task, today, zone) -> 3
        task.priority == TaskPriority.URGENT -> 2
        task.priority == TaskPriority.HIGH -> 1
        else -> 0
    }

    private fun reason(task: TaskEntity, now: Long, today: LocalDate, zone: ZoneId): WhatNowReason = when {
        task.status == TaskStatus.IN_PROGRESS -> WhatNowReason.IN_PROGRESS_NOW
        isInProgressNow(task, now) -> WhatNowReason.IN_PROGRESS_NOW
        TaskRules.isOverdue(task, now) -> WhatNowReason.OVERDUE
        isImminentStart(task, now) -> WhatNowReason.IMMINENT_START
        isScheduledLater(task, now) -> WhatNowReason.SCHEDULED_LATER
        isDueToday(task, today, zone) -> WhatNowReason.DUE_TODAY
        task.priority == TaskPriority.URGENT -> WhatNowReason.URGENT
        task.priority == TaskPriority.HIGH -> WhatNowReason.HIGH_PRIORITY
        else -> WhatNowReason.NEXT_INBOX
    }

    private fun isInProgressNow(task: TaskEntity, now: Long): Boolean {
        val start = task.startAt ?: return false
        if (now < start) return false
        val duration = task.durationMinutes.coerceAtLeast(10) * 60_000L
        return now <= start + duration
    }

    /**
     * Compromiso a punto de empezar: startAt futuro pero dentro de [IMMINENT_WINDOW_MINUTES].
     * Una reunion/llamada/cita que comienza en pocos minutos es exactamente "que hago ahora",
     * aunque aun no haya arrancado: la elevamos por encima de la Bandeja para no olvidarla.
     * Las que empiezan mas tarde siguen como ultimo recurso (isScheduledLater).
     */
    private fun isImminentStart(task: TaskEntity, now: Long): Boolean {
        val start = task.startAt ?: return false
        return start > now && (start - now) <= IMMINENT_WINDOW_MINUTES * 60_000L
    }

    private fun isScheduledLater(task: TaskEntity, now: Long): Boolean =
        task.startAt != null && task.startAt > now

    private fun isDueToday(task: TaskEntity, today: LocalDate, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate() == today
    }

    /** Ventana en la que un compromiso programado futuro se considera "ahora mismo". */
    private const val IMMINENT_WINDOW_MINUTES = 15
}
