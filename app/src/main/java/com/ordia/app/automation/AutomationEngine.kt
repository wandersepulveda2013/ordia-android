package com.ordia.app.automation

import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationRuleResult
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.repository.AutomationRuleRepository
import com.ordia.app.data.repository.ConversationRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.domain.TaskSnapshotCodec
import com.ordia.app.reminders.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    suspend fun runTrigger(
        trigger: AutomationTrigger,
        chainDepth: Int = 0,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<AutomationRunOutcome> =
        rules.enabledFor(trigger).map { runRule(it, chainDepth = chainDepth, zone = zone) }

    suspend fun runRule(
        rule: AutomationRuleEntity,
        manual: Boolean = false,
        test: Boolean = false,
        chainDepth: Int = 0,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AutomationRunOutcome = mutex.withLock {
        val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
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
            val plan = AutomationActionPlanner.build(rule, allTasks, pending, now, zone)
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
