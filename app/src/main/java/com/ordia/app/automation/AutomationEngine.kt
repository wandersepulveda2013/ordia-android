package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationRuleResult
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.repository.AutomationRuleRepository
import com.ordia.app.data.repository.ConversationRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.TaskRules
import com.ordia.app.domain.TaskSnapshotCodec
import com.ordia.app.reminders.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
                    byId[block.taskId]?.copy(
                        startAt = DateRules.toEpochMillis(date, LocalTime.of(block.startMinute / 60, block.startMinute % 60), zone),
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
                    task.copy(
                        startAt = null,
                        dueAt = due,
                        reminderAt = task.reminderAt?.let { due - 60 * 60_000L },
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
                    task.copy(startAt = start, status = TaskStatus.PLANNED, updatedAt = now)
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

data class AutomationRunOutcome(
    val result: AutomationRuleResult,
    val message: String,
    val logId: Long = 0,
    val changed: Boolean = false
)

class AutomationEngine(
    private val rules: AutomationRuleRepository,
    private val tasks: TaskRepository,
    private val conversations: ConversationRepository,
    private val reminderScheduler: ReminderScheduler
) {
    private val mutex = Mutex()

    suspend fun runTrigger(trigger: AutomationTrigger, chainDepth: Int = 0): List<AutomationRunOutcome> =
        rules.enabledFor(trigger).map { runRule(it, chainDepth = chainDepth) }

    suspend fun runRule(
        rule: AutomationRuleEntity,
        manual: Boolean = false,
        test: Boolean = false,
        chainDepth: Int = 0,
        now: Long = System.currentTimeMillis()
    ): AutomationRunOutcome = mutex.withLock {
        val dayStart = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val guard = AutomationExecutionGuard.evaluate(
            rule, now, rules.countRuns(rule.id, dayStart), chainDepth, manual, test
        )
        if (!guard.allowed) {
            return@withLock AutomationRunOutcome(
                AutomationRuleResult.SKIPPED,
                when (guard.reason) {
                    AutomationBlockReason.DISABLED -> "La automatización está desactivada."
                    AutomationBlockReason.FREQUENCY_LIMIT -> "Se omitió por su límite de frecuencia."
                    AutomationBlockReason.DAILY_LIMIT -> "Alcanzó su límite diario."
                    AutomationBlockReason.LOOP_GUARD -> "Se bloqueó una cadena recursiva."
                    null -> "No se ejecutó."
                }
            )
        }

        return@withLock runCatching {
            val allTasks = tasks.getAllNow()
            val pending = conversations.getCommitmentsNow().count { it.reviewStatus == CommitmentReviewStatus.PENDING }
            val plan = AutomationActionPlanner.build(rule, allTasks, pending, now)
            if (test) {
                rules.update(rule.copy(lastResult = AutomationRuleResult.TESTED, lastError = "", updatedAt = now))
                val logId = rules.log(
                    AutomationLogEntity(type = "test:${rule.id}", description = "Prueba: ${plan.message}")
                )
                return@runCatching AutomationRunOutcome(AutomationRuleResult.TESTED, plan.message, logId)
            }

            val before = plan.updates.associateBy { it.id }.mapValues { (id, _) -> allTasks.first { it.id == id } }
            plan.updates.forEach { task ->
                tasks.update(task)
                if (task.reminderAt != null || task.dueAt != null) reminderScheduler.schedule(task)
            }
            val createdIds = plan.creates.mapNotNull { task ->
                tasks.add(task).takeIf { it > 0L }
            }
            val affected = plan.updates.map { it.id } + createdIds
            val logId = rules.log(
                AutomationLogEntity(
                    type = "rule:${rule.id}",
                    description = plan.message,
                    affectedTaskIdsJson = TaskSnapshotCodec.encodeIds(affected),
                    undoPayloadJson = TaskSnapshotCodec.encodeMap(before)
                )
            )
            val result = if (plan.matched) AutomationRuleResult.SUCCESS else AutomationRuleResult.SKIPPED
            rules.update(
                rule.copy(lastRunAt = now, lastResult = result, lastError = "", updatedAt = now)
            )
            AutomationRunOutcome(result, plan.message, logId, affected.isNotEmpty())
        }.getOrElse { error ->
            val safe = error.message.orEmpty().take(180).ifBlank { "Error local inesperado." }
            val logId = rules.log(
                AutomationLogEntity(type = "rule:${rule.id}", description = "Error: $safe")
            )
            rules.update(
                rule.copy(lastRunAt = now, lastResult = AutomationRuleResult.FAILED, lastError = safe, updatedAt = now)
            )
            AutomationRunOutcome(AutomationRuleResult.FAILED, safe, logId)
        }
    }
}
