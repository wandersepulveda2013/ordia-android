package com.ordia.app.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ordia.app.OrdiaApplication
import com.ordia.app.data.local.AutomationRuleResult
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.repository.AutomationRuleRepository
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object AutomationSchedulePolicy {
    fun triggerForHour(hour: Int): AutomationTrigger? = when (hour) {
        in 5..11 -> AutomationTrigger.DAILY_MORNING
        in 17..23 -> AutomationTrigger.DAILY_EVENING
        else -> null
    }
}

class AutomationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? OrdiaApplication ?: return Result.failure()
        val trigger = AutomationSchedulePolicy.triggerForHour(ZonedDateTime.now().hour) ?: return Result.success()
        val outcomes = app.container.automationEngine.runTrigger(trigger)
        val failed = outcomes.any { it.result == AutomationRuleResult.FAILED }
        return when {
            !failed -> Result.success()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.failure()
        }
    }
}

object AutomationScheduler {
    private const val WORK_NAME = "ordia-automation-engine"

    suspend fun sync(context: Context, repository: AutomationRuleRepository) {
        val needed = repository.allNow().any {
            it.enabled && (it.trigger == AutomationTrigger.DAILY_MORNING || it.trigger == AutomationTrigger.DAILY_EVENING)
        }
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!needed) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutomationWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
