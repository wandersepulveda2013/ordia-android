package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.TaskRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class AutomationPlan(
    val updates: List<TaskEntity> = emptyList(),
    val creates: List<TaskEntity> = emptyList(),
    val message: String,
    val matched: Boolean = true
)

object AutomationActionPlanner {
    fun build(
        rule: AutomationRuleEntity,
        tasks: List<TaskEntity>,
        pendingCommitments: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AutomationPlan {
        val active = tasks.filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED }
        val overdue = active.filter { TaskRules.isOverdue(it, now) && it.parentTaskId == null }
        val quick = active.filter { it.durationMinutes <= 10 && it.parentTaskId == null }
        val conditionMet = when (rule.condition) {
            AutomationCondition.ALWAYS -> true
            AutomationCondition.HAS_OVERDUE_TASKS -> overdue.isNotEmpty()
            AutomationCondition.HAS_INBOX_TASKS -> active.any { it.status == TaskStatus.INBOX || it.dueAt == null }
            AutomationCondition.HAS_QUICK_TASKS -> quick.isNotEmpty()
            AutomationCondition.HAS_PENDING_COMMITMENTS -> pendingCommitments > 0
        }
        if (!conditionMet) return AutomationPlan(message = "La condición no se cumple; no se cambió nada.", matched = false)

        return when (rule.action) {
            AutomationAction.PLAN_DAY -> {
                val date = LocalDate.now(zone)
                val plan = DayPlanner.build(active, date, now = now, zone = zone)
                val byId = tasks.associateBy { it.id }
                val updates = plan.blocks.take(12).mapNotNull { block ->
                    val start = DateRules.toEpochMillis(date, LocalTime.of(block.startMinute / 60, block.startMinute % 60), zone)
                    byId[block.taskId]?.copy(
                        startAt = start,
                        // Un slot planificado sin recordatorio previo obtiene uno en su inicio,
                        // pero SOLO si es futuro: un slot ya pasado (plan a media mañana) no debe
                        // disparar un recordatorio tardío. No pisa un reminderAt previo.
                        reminderAt = byId[block.taskId]?.reminderAt ?: if (start > now) start else null,
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas preparadas para hoy.", matched = updates.isNotEmpty())
            }
            AutomationAction.RESCHEDULE_OVERDUE -> {
                val base = LocalDate.now(zone)
                val updates = overdue.sortedBy { it.dueAt }.take(8).mapIndexed { index, task ->
                    val due = DateRules.toEpochMillis(base.plusDays(1L + index / 3), LocalTime.of(18, 0), zone)
                    // Recordatorio:
                    // - Si la tarea ya tenía uno, se conserva el OFFSET original del usuario
                    //   (dueAt - reminderAt), aplicándolo al nuevo vencimiento. Esto evita
                    //   corromper la cadencia de recordatorios recurrentes, que [RecurrenceEngine]
                    //   reutiliza como offset para todas las ocurrencias futuras.
                    // - Si no tenía recordatorio, se añade uno 1 h antes del nuevo vencimiento
                    //   (siempre futuro): una tarea vencida reprogramada no debe quedar al olvido.
                    //   Coherente con PLAN_DAY/BATCH_QUICK_TASKS (c.129), que añaden un recordatorio
                    //   por defecto en el inicio cuando no existía.
                    val reminder = if (task.reminderAt != null && task.dueAt != null) {
                        val offset = task.dueAt - task.reminderAt
                        due - offset
                    } else {
                        due - 60 * 60_000L
                    }
                    task.copy(
                        startAt = null,
                        dueAt = due,
                        reminderAt = reminder,
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas vencidas reprogramadas.", matched = updates.isNotEmpty())
            }
            AutomationAction.BATCH_QUICK_TASKS -> {
                val current = Instant.ofEpochMilli(now).atZone(zone)
                val date = if (current.hour >= 20) current.toLocalDate().plusDays(1) else current.toLocalDate()
                val firstMinute = if (date != current.toLocalDate()) 9 * 60 else (((current.hour * 60 + current.minute + 29) / 15) * 15)
                var cursor = firstMinute.coerceAtMost(21 * 60)
                val updates = quick.sortedBy { it.dueAt ?: Long.MAX_VALUE }.take(8).map { task ->
                    val start = DateRules.toEpochMillis(date, LocalTime.of(cursor / 60, cursor % 60), zone)
                    cursor += task.durationMinutes.coerceIn(5, 10) + 5
                    task.copy(
                        startAt = start,
                        // Recordatorio en el inicio del slot solo si es futuro y no hay uno previo.
                        reminderAt = task.reminderAt ?: if (start > now) start else null,
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas rápidas agrupadas.", matched = updates.isNotEmpty())
            }
            AutomationAction.REVIEW_COMMITMENTS -> {
                val marker = "Ordía · revisión automática de compromisos"
                if (active.any { marker in it.details }) {
                    AutomationPlan(message = "Ya existe una revisión pendiente; no se creó un duplicado.", matched = false)
                } else {
                    AutomationPlan(
                        creates = listOf(
                            TaskEntity(
                                title = "Revisar $pendingCommitments compromisos de mensajes",
                                details = marker,
                                durationMinutes = 10,
                                status = TaskStatus.INBOX,
                                createdAt = now,
                                updatedAt = now
                            )
                        ),
                        message = "Se creó una revisión de compromisos."
                    )
                }
            }
        }
    }
}
