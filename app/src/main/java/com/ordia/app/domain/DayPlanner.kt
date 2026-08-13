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

        val candidates = tasks.asSequence()
            .filter { !it.completed && !it.archived && it.parentTaskId == null }
            .filter { task ->
                val dueOnDate = TaskRules.isDueOn(task, date, zone)
                val overdueByDate = task.dueAt?.let { DateRules.toLocalDate(it, zone).isBefore(date) } == true
                dueOnDate || overdueByDate || (includeInbox && task.dueAt == null)
            }
            .toList()

        val blocks = mutableListOf<Block>()
        val unscheduled = mutableListOf<Long>()

        // 1. First pass: Fixed time tasks (tasks that have a dueAt and it falls on 'date' or is overdue)
        // Note: For now, if a task has dueAt we treat its time as fixed, unless we only care about the date.
        // Actually, in the original model, dueAt is an epochMillis which has both date and time.
        // If it's on this date, we extract the time.
        val fixedTasks = candidates.filter { task ->
            task.dueAt != null && DateRules.toLocalDate(task.dueAt, zone) == date
        }

        fixedTasks.forEach { task ->
            val time = DateRules.toLocalTime(task.dueAt!!, zone)
            val startMinute = time.hour * 60 + time.minute
            val duration = task.durationMinutes.coerceIn(10, 180)
            val endMinute = startMinute + duration
            // Only add if it starts on or after dayStartMinute and ends before dayEndMinute
            // Or just add it if it's explicitly scheduled for today and doesn't overlap completely outside the day boundaries
            if (startMinute >= 0 && endMinute <= 24 * 60) {
                blocks += Block(
                    taskId = task.id,
                    title = task.title,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    priority = task.priority,
                    overdue = TaskRules.isOverdue(task, now)
                )
            }
        }

        // Sort fixed blocks so we can schedule flexible tasks around them
        blocks.sortBy { it.startMinute }

        // 2. Second pass: Flexible tasks
        val flexibleTasks = candidates.filter { task ->
            !fixedTasks.contains(task)
        }.sortedWith(
            compareByDescending<TaskEntity> { TaskRules.isOverdue(it, now) }
                .thenByDescending { priorityScore(it.priority) }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { it.sortOrder }
                .thenBy { it.createdAt }
        )

        var cursor = dayStartMinute

        flexibleTasks.forEach { task ->
            val duration = task.durationMinutes.coerceIn(10, 180)

            // Find next available slot
            var slotFound = false
            while (cursor + duration <= dayEndMinute) {
                val proposedStart = cursor
                val proposedEnd = proposedStart + duration

                // Check for overlaps with existing blocks
                val overlap = blocks.firstOrNull { block ->
                    proposedStart < block.endMinute + breakMinutes && proposedEnd > block.startMinute - breakMinutes
                }

                if (overlap == null) {
                    blocks += Block(
                        taskId = task.id,
                        title = task.title,
                        startMinute = proposedStart,
                        endMinute = proposedEnd,
                        priority = task.priority,
                        overdue = TaskRules.isOverdue(task, now)
                    )
                    cursor = proposedEnd + breakMinutes
                    blocks.sortBy { it.startMinute } // Keep sorted
                    slotFound = true
                    break
                } else {
                    cursor = overlap.endMinute + breakMinutes
                }
            }

            if (!slotFound) {
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

    private fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0
        TaskPriority.NORMAL -> 1
        TaskPriority.HIGH -> 2
        TaskPriority.URGENT -> 3
    }
}
