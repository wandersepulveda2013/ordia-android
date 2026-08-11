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

    fun nextBestTask(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): TaskEntity? =
        tasks.asSequence()
            .filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED && it.parentTaskId == null }
            .sortedWith(compareByDescending<TaskEntity> { isOverdue(it, now) }
                .thenByDescending { priorityScore(it.priority) }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { it.createdAt })
            .firstOrNull()

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

    private fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0; TaskPriority.NORMAL -> 1; TaskPriority.HIGH -> 2; TaskPriority.URGENT -> 3
    }
}
