package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Explica por qué Ordia colocó una tarea en esa posición del plan. */
enum class PlanReason { OVERDUE, URGENT, HIGH_PRIORITY, DUE_TODAY, DUE_ON_DATE, SCHEDULED_TIME, INBOX }

/** Advertencia del planificador sobre cambios sobre datos previos del usuario. */
enum class PlanConflictKind { MOVED_FROM_SCHEDULED_TIME }

/** Builds a realistic local day plan from existing tasks without changing user data. */
object DayPlanner {
    data class Block(
        val taskId: Long,
        val title: String,
        val startMinute: Int,
        val endMinute: Int,
        val priority: TaskPriority,
        val overdue: Boolean,
        val reason: PlanReason = PlanReason.DUE_TODAY
    ) {
        val durationMinutes: Int get() = endMinute - startMinute
    }

    data class PlanConflict(val taskId: Long, val kind: PlanConflictKind)

    data class Plan(
        val date: LocalDate,
        val blocks: List<Block>,
        val unscheduledTaskIds: List<Long>,
        val availableMinutes: Int,
        val scheduledMinutes: Int,
        val conflicts: List<PlanConflict> = emptyList()
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
        includeScheduledOnDate: Boolean = false,
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
                val scheduledOnDate = includeScheduledOnDate && task.startAt?.let {
                    DateRules.toLocalDate(it, zone) == date
                } == true
                dueOnDate || overdueByDate || scheduledOnDate || (includeInbox && task.dueAt == null)
            }
            .sortedWith(
                compareByDescending<TaskEntity> { TaskRules.isOverdue(it, now) }
                    .thenByDescending { TaskRules.priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt }
            )
            .toList()

        val tasksById = candidates.associateBy { it.id }
        val blocks = mutableListOf<Block>()
        val unscheduled = mutableListOf<Long>()
        var cursor = dayStartMinute

        candidates.forEach { task ->
            val duration = TaskRules.plannedDuration(task)
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
                    overdue = TaskRules.isOverdue(task, now),
                    reason = planReason(task, now, date, zone)
                )
                cursor = proposedEnd
            } else {
                unscheduled += task.id
            }
        }

        // Conflictos: el plan mueve tareas que ya tenían hora prevista ese día.
        // Solo se compara la hora cuando `startAt` cae realmente en el día del plan:
        // una tarea cuyo `startAt` es otro día (p. ej. empezada ayer, vence hoy) no
        // tiene hora prevista "ese día" y no debe marcarse como movida.
        val conflicts = buildList {
            blocks.forEach { block ->
                val task = tasksById[block.taskId]
                if (task?.startAt != null) {
                    val original = Instant.ofEpochMilli(task.startAt).atZone(zone)
                    if (original.toLocalDate() == date) {
                        val originalMinute = original.hour * 60 + original.minute
                        if (originalMinute != block.startMinute) {
                            add(PlanConflict(block.taskId, PlanConflictKind.MOVED_FROM_SCHEDULED_TIME))
                        }
                    }
                }
            }
        }

        return Plan(
            date = date,
            blocks = blocks,
            unscheduledTaskIds = unscheduled,
            availableMinutes = dayEndMinute - dayStartMinute,
            scheduledMinutes = blocks.sumOf { it.durationMinutes } +
                ((blocks.size - 1).coerceAtLeast(0) * breakMinutes),
            conflicts = conflicts
        )
    }

    /**
     * Razón de colocación. "Vence hoy" solo es cierto cuando la tarea vence
     * el día real de hoy; en un plan construido para otra fecha, una tarea
     * que vence ese día se etiqueta como "vence este día" para no fingir
     * urgencia de hoy.
     */
    private fun planReason(task: TaskEntity, now: Long, date: LocalDate, zone: ZoneId): PlanReason = when {
        TaskRules.isOverdue(task, now) -> PlanReason.OVERDUE
        task.priority == TaskPriority.URGENT -> PlanReason.URGENT
        task.priority == TaskPriority.HIGH -> PlanReason.HIGH_PRIORITY
        task.startAt != null -> PlanReason.SCHEDULED_TIME
        task.dueAt != null -> {
            val dueDate = DateRules.toLocalDate(task.dueAt, zone)
            val today = DateRules.toLocalDate(now, zone)
            if (dueDate == today) PlanReason.DUE_TODAY else PlanReason.DUE_ON_DATE
        }
        else -> PlanReason.INBOX
    }
}
