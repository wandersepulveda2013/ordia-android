package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import java.time.LocalDate
import java.time.ZoneId

/** Builds a realistic local day plan from existing tasks without changing user data. */
object DayPlanner {
    data class Block(
        val taskId: Long,
        val title: String,
        val startMinute: Int,
        val endMinute: Int,
        val priority: TaskPriority,
        val overdue: Boolean
    ) {
        val durationMinutes: Int get() = endMinute - startMinute
    }

    data class Plan(
        val date: LocalDate,
        val blocks: List<Block>,
        val unscheduledTaskIds: List<Long>,
        val availableMinutes: Int,
        val scheduledMinutes: Int
    ) {
        val remainingMinutes: Int get() = (availableMinutes - scheduledMinutes).coerceAtLeast(0)
    }

    fun build(
        tasks: List<TaskEntity>,
        date: LocalDate,
        dayStartMinute: Int = 9 * 60,
        dayEndMinute: Int = 18 * 60,
        breakMinutes: Int = 10,
        includeInbox: Boolean = true,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Plan {
        require(dayStartMinute in 0 until 24 * 60)
        require(dayEndMinute in 1..24 * 60)
        require(dayEndMinute > dayStartMinute)
        require(breakMinutes in 0..60)

        val candidates = getCandidates(tasks, date, includeInbox, now, zone)

        val blocks = mutableListOf<Block>()
        val unscheduled = mutableListOf<Long>()
        var cursor = dayStartMinute

        candidates.forEach { task ->
            val duration = task.durationMinutes.coerceIn(10, 180)
            val gap = if (blocks.isEmpty()) 0 else breakMinutes
            val proposedStart = cursor + gap
            val proposedEnd = proposedStart + duration
            if (proposedEnd <= dayEndMinute) {
                blocks += Block(
                    taskId = task.id,
                    title = task.title,
                    startMinute = proposedStart,
                    endMinute = proposedEnd,
                    priority = task.priority,
                    overdue = TaskRules.isOverdue(task, now)
                )
                cursor = proposedEnd
            } else {
                unscheduled += task.id
            }
        }

        return Plan(
            date = date,
            blocks = blocks,
            unscheduledTaskIds = unscheduled,
            availableMinutes = dayEndMinute - dayStartMinute,
            scheduledMinutes = blocks.sumOf { it.durationMinutes } +
                ((blocks.size - 1).coerceAtLeast(0) * breakMinutes)
        )
    }

    private fun getCandidates(
        tasks: List<TaskEntity>,
        date: LocalDate,
        includeInbox: Boolean,
        now: Long,
        zone: ZoneId
    ): List<TaskEntity> {
        return tasks.asSequence()
            .filter { !it.completed && !it.archived && it.parentTaskId == null }
            .filter { task ->
                val dueOnDate = TaskRules.isDueOn(task, date, zone)
                val overdueByDate = task.dueAt?.let { DateRules.toLocalDate(it, zone).isBefore(date) } == true
                dueOnDate || overdueByDate || (includeInbox && task.dueAt == null)
            }
            .sortedWith(
                compareByDescending<TaskEntity> { TaskRules.isOverdue(it, now) }
                    .thenByDescending { priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt }
            )
            .toList()
    }

    private fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0
        TaskPriority.NORMAL -> 1
        TaskPriority.HIGH -> 2
        TaskPriority.URGENT -> 3
    }
}
