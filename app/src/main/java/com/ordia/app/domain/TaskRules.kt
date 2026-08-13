package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskRules {
    /** Límites de duración de una tarea dentro de un plan de día (coherentes entre plan y resumen). */
    const val MIN_PLAN_MINUTES = 10
    const val MAX_PLAN_MINUTES = 180

    /**
     * Duración planificable de una tarea, acotada a [MIN_PLAN_MINUTES, MAX_PLAN_MINUTES].
     * Fuente única de verdad: tanto DayPlanner (plan del día) como SummaryEngine
     * (badge de minutos pendientes) usan este valor, evitando que el resumen
     * muestre minutos que el plan no podría acomodar (ni una tarea sin duración
     * cuente como 0 cuando el plan la trata como [MIN_PLAN_MINUTES]).
     */
    fun plannedDuration(task: TaskEntity): Int =
        task.durationMinutes.coerceIn(MIN_PLAN_MINUTES, MAX_PLAN_MINUTES)

    /**
     * Siguiente tarea más importante, con la misma prioridad temporal que
     * [WhatNowEngine.suggest] (widget, asistente y "siguiente paso" del guardián
     * comparten esta lógica): lo que ocurre ahora mismo > atrasado > compromiso a
     * punto de empezar (inminente) > vence hoy > urgente > alta > bandeja; las
     * programadas para más tarde quedan al final.
     * Desempate: prioridad, fecha límite más próxima, hora prevista, orden, creación.
     */
    fun nextBestTask(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): TaskEntity? =
        tasks.asSequence()
            .filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED && it.parentTaskId == null }
            .sortedWith(
                compareByDescending<TaskEntity> { timeRank(it, now, zone) }
                    .thenByDescending { priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.startAt ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt }
            )
            .firstOrNull()

    /** Ventana en la que un compromiso programado futuro se considera "ahora mismo". */
    const val IMMINENT_WINDOW_MINUTES = 15

    private fun timeRank(task: TaskEntity, now: Long, zone: ZoneId): Int = when {
        task.status == TaskStatus.IN_PROGRESS -> 6
        isInProgressNow(task, now) -> 5
        isOverdue(task, now) -> 4
        isImminentStart(task, now) -> 4
        isScheduledLater(task, now) -> -1
        isDueToday(task, now, zone) -> 3
        task.priority == TaskPriority.URGENT -> 2
        task.priority == TaskPriority.HIGH -> 1
        else -> 0
    }

    /**
     * Compromiso ocurriendo ahora mismo: `startAt` ya comenzó y no ha rebasado
     * su duración planificada. Fuente única de verdad compartida con
     * [WhatNowEngine] (rank de "ahora mismo") y con [SummaryEngine] (no
     * sugiere posponer lo que se está ejecutando en este instante).
     */
    fun isInProgressNow(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        val start = task.startAt ?: return false
        if (now < start) return false
        val duration = task.durationMinutes.coerceAtLeast(10) * 60_000L
        return now <= start + duration
    }

    /**
     * Compromiso a punto de empezar: `startAt` futuro pero dentro de
     * [IMMINENT_WINDOW_MINUTES]. Una reunión/llamada/cita que comienza en pocos
     * minutos es exactamente "qué hago ahora", aunque aún no haya arrancado: la
     * elevamos por encima de la Bandeja para no olvidarla. Las que empiezan más
     * tarde siguen como último recurso ([isScheduledLater]). Fuente única de
     * verdad compartida con [WhatNowEngine].
     */
    fun isImminentStart(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        val start = task.startAt ?: return false
        return start > now && (start - now) <= IMMINENT_WINDOW_MINUTES * 60_000L
    }

    private fun isScheduledLater(task: TaskEntity, now: Long): Boolean =
        task.startAt != null && task.startAt > now

    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        !task.completed && !task.archived && task.status != TaskStatus.CANCELLED && task.dueAt?.let { it < now } == true

    fun isDueToday(task: TaskEntity, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (task.completed || task.archived || task.status == TaskStatus.CANCELLED) return false
        val due = task.dueAt ?: return false
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    }

    fun isDueOn(task: TaskEntity, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        if (task.completed || task.archived || task.status == TaskStatus.CANCELLED) false
        else task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date } ?: false

    fun completionRate(tasks: List<TaskEntity>): Int {
        val relevant = tasks.filter { !it.archived && it.status != TaskStatus.CANCELLED && it.parentTaskId == null }
        if (relevant.isEmpty()) return 0
        return ((relevant.count { it.completed } * 100.0) / relevant.size).toInt()
    }

    /**
     * Puntaje de prioridad compartido por todas las superficies de decisión
     * (What Now, widget/asistente, planificador). Fuente única de verdad para
     * que el desempate por prioridad sea idéntico en todos lados.
     */
    fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0; TaskPriority.NORMAL -> 1; TaskPriority.HIGH -> 2; TaskPriority.URGENT -> 3
    }
}
