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
    DUE_TODAY,
    URGENT,
    HIGH_PRIORITY,
    NEXT_INBOX,
    SCHEDULED_LATER
}

/** Resultado de "¿Qué hago ahora?": la tarea más importante para este momento. */
data class WhatNowSuggestion(
    val task: TaskEntity,
    val reason: WhatNowReason,
    /** Explicación breve y dinámica de por qué toca esta tarea ahora. */
    val detail: String
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
        val reason = reason(chosen, now, today, zone)
        return WhatNowSuggestion(chosen, reason, detail(chosen, reason, now, today, zone))
    }

    private fun isCandidate(task: TaskEntity): Boolean =
        !task.completed && !task.archived && task.status != TaskStatus.CANCELLED && task.parentTaskId == null

    private fun rank(task: TaskEntity, now: Long, today: LocalDate, zone: ZoneId): Int = when {
        task.status == TaskStatus.IN_PROGRESS -> 6
        isInProgressNow(task, now) -> 5
        TaskRules.isOverdue(task, now) -> 4
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
        isScheduledLater(task, now) -> WhatNowReason.SCHEDULED_LATER
        isDueToday(task, today, zone) -> WhatNowReason.DUE_TODAY
        task.priority == TaskPriority.URGENT -> WhatNowReason.URGENT
        task.priority == TaskPriority.HIGH -> WhatNowReason.HIGH_PRIORITY
        else -> WhatNowReason.NEXT_INBOX
    }

    private fun detail(
        task: TaskEntity,
        reason: WhatNowReason,
        now: Long,
        today: LocalDate,
        zone: ZoneId
    ): String = when (reason) {
        WhatNowReason.IN_PROGRESS_NOW -> "En curso ahora mismo."
        WhatNowReason.OVERDUE -> overdueDetail(task, today, zone)
        WhatNowReason.DUE_TODAY -> task.dueAt?.let { "Vence hoy a las ${timeLabel(it, zone)}." } ?: "Vence hoy."
        WhatNowReason.URGENT -> "Urgente y sin fecha límite."
        WhatNowReason.HIGH_PRIORITY -> "Alta prioridad y sin fecha límite."
        WhatNowReason.NEXT_INBOX -> "Primera en tu Bandeja."
        WhatNowReason.SCHEDULED_LATER -> task.startAt?.let { "Programada para las ${timeLabel(it, zone)}." } ?: "Programada más tarde."
    }

    private fun overdueDetail(task: TaskEntity, today: LocalDate, zone: ZoneId): String {
        val due = task.dueAt ?: return "Atrasada."
        val days = java.time.temporal.ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(due).atZone(zone).toLocalDate(),
            today
        )
        return when {
            days <= 0L -> "Atrasada desde hoy."
            days == 1L -> "Atrasada 1 día."
            days <= 9L -> "Atrasada $days días."
            else -> "Atrasada desde hace más de una semana."
        }
    }

    private fun timeLabel(epochMillis: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "%02d:%02d".format(time.hour, time.minute)
    }

    private fun isInProgressNow(task: TaskEntity, now: Long): Boolean {
        val start = task.startAt ?: return false
        if (now < start) return false
        val duration = task.durationMinutes.coerceAtLeast(10) * 60_000L
        return now <= start + duration
    }

    private fun isScheduledLater(task: TaskEntity, now: Long): Boolean =
        task.startAt != null && task.startAt > now

    private fun isDueToday(task: TaskEntity, today: LocalDate, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate() == today
    }
}
