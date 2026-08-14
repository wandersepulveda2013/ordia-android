package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskRules {
    data class BestTask(val task: TaskEntity, val reason: String)

    fun nextBestTask(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): BestTask? {
        val nextTask = tasks.asSequence()
            .filter { !it.completed && !it.archived && it.parentTaskId == null }
            .sortedWith(
                compareByDescending<TaskEntity> { isOverdue(it, now) }
                    .thenByDescending { priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.createdAt }
            )
            .firstOrNull() ?: return null

        val reason = generateReason(nextTask, now)
        return BestTask(nextTask, reason)
    }

    private fun generateReason(task: TaskEntity, now: Long): String {
        if (isOverdue(task, now)) {
            val overdueByDays = task.dueAt?.let { (now - it) / (1000 * 60 * 60 * 24) } ?: 0L
            return if (overdueByDays > 0) "Haz esto ahora porque lleva $overdueByDays días de retraso."
            else "Haz esto ahora porque está atrasada y debe resolverse cuanto antes."
        }
        if (isDueToday(task, now)) {
            return when (task.priority) {
                TaskPriority.URGENT, TaskPriority.HIGH -> "Haz esto ahora porque vence hoy y es de alta prioridad."
                else -> "Haz esto ahora porque vence hoy y es el siguiente paso lógico."
            }
        }
        return when (task.priority) {
            TaskPriority.URGENT, TaskPriority.HIGH -> "Haz esto ahora porque es importante adelantar esta tarea de alta prioridad."
            else -> "Haz esto ahora porque, entre tus tareas disponibles, es el mejor uso de tu tiempo."
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
