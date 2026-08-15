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
                // La fecha del plan se deriva del `now` inyectado (igual que
                // BATCH_QUICK_TASKS), no del reloj del sistema: así la decisión es
                // determinista y verificable con un `now` fijo. Usar LocalDate.now
                // hacía que el plan dependiera del instante real y que ningún test
                // pudiera afirmar la fecha exacta de los slots.
                val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val plan = DayPlanner.build(active, date, now = now, zone = zone)
                val byId = tasks.associateBy { it.id }
                val updates = plan.blocks.take(12).mapNotNull { block ->
                    val start = DateRules.toEpochMillis(date, LocalTime.of(block.startMinute / 60, block.startMinute % 60), zone)
                    val end = DateRules.toEpochMillis(date, LocalTime.of(block.endMinute / 60, block.endMinute % 60), zone)
                    byId[block.taskId]?.copy(
                        startAt = start,
                        // Vencimiento coherente con el slot: si este empieza después del due
                        // original (tarea vencida/temprana en un bloque posterior), el due
                        // sigue al fin del slot. Evita `startAt > dueAt`, estado que
                        // [BackupManager] rechaza al restaurar (backup irrestaurable).
                        dueAt = TaskRules.dueAtForPlannedSlot(byId[block.taskId]?.dueAt, start, end),
                        // Recordatorio past-safe: se respeta un aviso previo del usuario SOLO si sigue
                        // siendo futuro. Si era pasado (tarea vencida cuyo aviso ya disparó), conservarlo
                        // tal cual lo dejaría SIN nudge para el nuevo slot: [ReminderSync] descarta
                        // trigger <= now, así la tarea planificada volvería a olvidarse. En ese caso se
                        // recae al default (inicio del slot, si es futuro). Simétrico con
                        // RESCHEDULE_OVERDUE (c.187) y [ReminderRules.resolveReminderAt] (c.183).
                        reminderAt = planReminder(byId[block.taskId]?.reminderAt, start, now),
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas preparadas para hoy.", matched = updates.isNotEmpty())
            }
            AutomationAction.RESCHEDULE_OVERDUE -> {
                // El día base de reprogramación se deriva del `now` inyectado, no del
                // reloj del sistema: coherente con PLAN_DAY/BATCH_QUICK_TASKS y
                // determinista. Antes LocalDate.now(zone) ignoraba el `now` fijo de
                // los tests (que solo pasaban porque "mañana real" > now inyectado).
                val base = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
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
                    // - Si el offset trasladado cae en el PASADO (offset grande, p. ej. "recuérdame
                    //   2 días antes" sobre una vencida de ayer → reminder ~27 h atrás), cae al
                    //   mismo default de 1 h antes (futuro). Un reminder pasado lo descarta
                    //   [ReminderSync] (trigger <= now → null), así sin este respaldo la tarea
                    //   reprogramada volvería a quedar SIN aviso → el usuario la olvidaba otra
                    //   vez, justo lo que esta acción debe evitar. Simétrico con
                    //   [ReminderRules.resolveReminderAt] (c.183), que también recae a un default
                    //   past-safe cuando la traslación del offset no cabe en el futuro.
                    val reminder = if (task.reminderAt != null && task.dueAt != null) {
                        val offset = task.dueAt - task.reminderAt
                        val translated = due - offset
                        if (translated > now) translated else due - 60 * 60_000L
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
                    val duration = task.durationMinutes.coerceIn(5, 10)
                    val start = DateRules.toEpochMillis(date, LocalTime.of(cursor / 60, cursor % 60), zone)
                    val end = start + duration * 60_000L
                    cursor += duration + 5
                    task.copy(
                        startAt = start,
                        // Vencimiento coherente con el slot (igual que PLAN_DAY): si el slot
                        // empieza después del due original, el due sigue al fin del slot. Evita
                        // `startAt > dueAt` que [BackupManager] rechaza al restaurar.
                        dueAt = TaskRules.dueAtForPlannedSlot(task.dueAt, start, end),
                        // Recordatorio past-safe (igual que PLAN_DAY): se respeta un aviso previo
                        // futuro, pero uno pasado se reemplaza por el default del slot futuro. Sin
                        // esto, una tarea rápida vencida agrupada en un slot futuro quedaría sin aviso.
                        reminderAt = planReminder(task.reminderAt, start, now),
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

    /**
     * Recordatorio para una tarea que pasa a un slot planificado: respeta un aviso previo
     * del usuario solo si sigue siendo futuro; si era pasado (ya disparó) recae al inicio
     * del slot cuando este es futuro, y a `null` cuando el slot ya pasó (sin avisos tardíos).
     * Contrato past-safe compartido por PLAN_DAY y BATCH_QUICK_TASKS; simétrico con
     * RESCHEDULE_OVERDUE y con [com.ordia.app.domain.ReminderRules.resolveReminderAt].
     */
    private fun planReminder(existing: Long?, start: Long, now: Long): Long? = when {
        existing != null && existing > now -> existing
        start > now -> start
        else -> null
    }
}
