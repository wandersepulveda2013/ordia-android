package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskRules {
    /** Puntuación única de prioridad para todas las decisiones del sistema. */
    fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0
        TaskPriority.NORMAL -> 1
        TaskPriority.HIGH -> 2
        TaskPriority.URGENT -> 3
    }

    /**
     * Orden único de programación, compartido por el planificador, el What Now,
     * el Guardián y la Bandeja: atrasadas primero, luego prioridad, fecha límite,
     * hora prevista, orden manual y fecha de creación.
     */
    fun schedulingComparator(now: Long): Comparator<TaskEntity> =
        compareByDescending<TaskEntity> { isOverdue(it, now) }
            .thenByDescending { priorityScore(it.priority) }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
            .thenBy { it.startAt ?: Long.MAX_VALUE }
            .thenBy { it.sortOrder }
            .thenBy { it.createdAt }

    fun nextBestTask(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): TaskEntity? =
        tasks.asSequence()
            .filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED && it.parentTaskId == null }
            .sortedWith(schedulingComparator(now))
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
}
