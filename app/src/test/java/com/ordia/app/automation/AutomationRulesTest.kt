package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.DateRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRulesTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 1), LocalTime.of(9, 0), zone)

    @Test fun naturalLanguageMapsOnlyToSupportedExplainableRules() {
        val parsed = AutomationRuleCatalog.parse("Cada mañana prepara mi día") as AutomationParseResult.Supported
        assertEquals(AutomationTrigger.DAILY_MORNING, parsed.template.trigger)
        assertEquals(AutomationAction.PLAN_DAY, parsed.template.action)
        assertTrue(AutomationRuleCatalog.parse("Borra todo cuando quieras") is AutomationParseResult.Unsupported)
    }

    @Test fun guardBlocksDisabledFrequencyDailyLimitAndLoops() {
        val base = AutomationRuleCatalog.templates.first().toEntity(now).copy(enabled = false)
        assertEquals(AutomationBlockReason.DISABLED, AutomationExecutionGuard.evaluate(base, now, 0, 0).reason)
        val enabled = base.copy(enabled = true, lastRunAt = now - 1_000)
        assertEquals(AutomationBlockReason.FREQUENCY_LIMIT, AutomationExecutionGuard.evaluate(enabled, now, 0, 0).reason)
        assertEquals(AutomationBlockReason.DAILY_LIMIT, AutomationExecutionGuard.evaluate(enabled.copy(lastRunAt = null), now, 9, 0).reason)
        assertEquals(AutomationBlockReason.LOOP_GUARD, AutomationExecutionGuard.evaluate(enabled, now, 0, 2, test = true).reason)
    }

    @Test fun plannerReschedulesOnlyBoundedOverdueTasksAndKeepsUndoInputs() {
        val overdueAt = DateRules.toEpochMillis(LocalDate.of(2026, 7, 31), LocalTime.NOON, zone)
        val tasks = (1L..12L).map { TaskEntity(id = it, title = "Vencida $it", dueAt = overdueAt) }
        val rule = AutomationRuleCatalog.templates[1].toEntity(now)
        val plan = AutomationActionPlanner.build(rule, tasks, 0, now, zone)
        assertEquals(8, plan.updates.size)
        assertTrue(plan.updates.all { it.dueAt!! > now && it.status == TaskStatus.PLANNED })
    }

    @Test fun commitmentReviewAvoidsDuplicateTask() {
        val rule = AutomationRuleCatalog.templates[3].toEntity(now)
        val first = AutomationActionPlanner.build(rule, emptyList(), 4, now, zone)
        assertEquals(1, first.creates.size)
        val second = AutomationActionPlanner.build(rule, first.creates, 4, now, zone)
        assertFalse(second.matched)
        assertTrue(second.creates.isEmpty())
    }

    @Test fun schedulePolicyUsesSeparatedMorningAndEveningWindows() {
        assertEquals(AutomationTrigger.DAILY_MORNING, AutomationSchedulePolicy.triggerForHour(8))
        assertEquals(AutomationTrigger.DAILY_EVENING, AutomationSchedulePolicy.triggerForHour(20))
        assertEquals(null, AutomationSchedulePolicy.triggerForHour(14))
    }
}
