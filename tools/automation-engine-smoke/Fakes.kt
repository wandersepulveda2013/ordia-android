package com.ordia.app.data.repository

import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.TaskEntity

// Fakes con los nombres reales: se compilan en lugar de Repositories.kt (Room).
class AutomationRuleRepository {
    var rulesForTrigger: List<AutomationRuleEntity> = emptyList()
    val countRunsSince = mutableListOf<Long>()
    var runsToReport = 0
    val logged = mutableListOf<AutomationLogEntity>()
    val updated = mutableListOf<AutomationRuleEntity>()

    suspend fun enabledFor(trigger: AutomationTrigger): List<AutomationRuleEntity> = rulesForTrigger
    suspend fun countRuns(ruleId: Long, since: Long): Int { countRunsSince += since; return runsToReport }
    suspend fun update(rule: AutomationRuleEntity) { updated += rule }
    suspend fun log(entry: AutomationLogEntity): Long { logged += entry; return 1L }
}

class TaskRepository {
    var tasks: List<TaskEntity> = emptyList()
    val updated = mutableListOf<TaskEntity>()
    suspend fun getAllNow(): List<TaskEntity> = tasks
    suspend fun update(task: TaskEntity) { updated += task }
    suspend fun add(task: TaskEntity): Long = 0L
}

class ConversationRepository {
    suspend fun getCommitmentsNow(): List<CommitmentEntity> = emptyList()
}
