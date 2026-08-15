package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskRules {
    data class BestTask(val task: TaskEntity, val reason: String?)

    fun nextBestTask(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): BestTask? {
        val task = tasks.asSequence()
            .filter { !it.completed && !it.archived && it.parentTaskId == null }
            .sortedWith(
                compareByDescending<TaskEntity> { isOverdue(it, now) }
                    .thenByDescending { priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt }
            )
            .firstOrNull() ?: return null

        val reason = generateReason(task, now)
        return BestTask(task, reason)
    }

    private fun generateReason(task: TaskEntity, now: Long): String? {
        val isOverdue = isOverdue(task, now)
        return when {
            isOverdue && task.priority == TaskPriority.URGENT -> "Es una tarea urgente que ya está atrasada."
            isOverdue -> "Esta tarea está atrasada."
            task.priority == TaskPriority.URGENT -> "Es una tarea urgente para hoy."
            task.priority == TaskPriority.HIGH -> "Es una prioridad alta."
            task.dueAt != null -> "Es la próxima tarea programada."
            else -> "Es la tarea más importante disponible."
        }
    }

    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        !task.completed && task.dueAt?.let { it < now } == true

    fun isDueToday(task: TaskEntity, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val due = task.dueAt ?: return false
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    }

    fun isDueOn(task: TaskEntity, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date } ?: false

    fun completionRate(tasks: List<TaskEntity>): Int {
        val relevant = tasks.filter { !it.archived && it.parentTaskId == null }
        if (relevant.isEmpty()) return 0
        return ((relevant.count { it.completed } * 100.0) / relevant.size).toInt()
    }

    private fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0
        TaskPriority.NORMAL -> 1
        TaskPriority.HIGH -> 2
        TaskPriority.URGENT -> 3
    }
}
