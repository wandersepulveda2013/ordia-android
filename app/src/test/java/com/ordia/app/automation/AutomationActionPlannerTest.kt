package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationActionPlannerTest {

    private val zone = ZoneId.of("America/Santiago")
    private val now = 1_736_812_000_000L // 2025-01-13 10:00 America/Santiago

    private fun rule(
        action: AutomationAction,
        condition: AutomationCondition = AutomationCondition.ALWAYS
    ) = AutomationRuleEntity(
        id = 1,
        name = "test",
        instruction = "",
        trigger = AutomationTrigger.MANUAL,
        condition = condition,
        action = action,
        explanation = "",
        enabled = true,
        definitionHash = "h"
    )

    private fun task(
        id: Long,
        title: String = "t$id",
        durationMinutes: Int = 25,
        status: TaskStatus = TaskStatus.INBOX,
        dueAt: Long? = null,
        reminderAt: Long? = null,
        startAt: Long? = null
    ) = TaskEntity(
        id = id,
        title = title,
        durationMinutes = durationMinutes,
        status = status,
        dueAt = dueAt,
        reminderAt = reminderAt,
        startAt = startAt,
        createdAt = now - 1000,
        updatedAt = now - 1000
    )

    @Test
    fun `plan_day no pisa reminderAt previo`() {
        val existingReminder = now + 3_600_000L
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = existingReminder)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)

        assertTrue(plan.matched)
        assertEquals(1, plan.updates.size)
        // El reminder previo del usuario se respeta.
        assertEquals(existingReminder, plan.updates.first().reminderAt)
        assertEquals(TaskStatus.PLANNED, plan.updates.first().status)
    }

    @Test
    fun `plan_day asigna reminder al inicio solo si el slot es futuro`() {
        // Tarea planificada a una hora futura (now=10:00, slot 09:00 no aplica porque
        // DayPlanner arranca a las 09:00 pero 09:00 < 10:00 → slot pasado → sin reminder).
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = null)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)

        val update = plan.updates.first()
        if (update.startAt != null && update.startAt <= now) {
            assertNull("Un slot pasado no debe generar recordatorio tardío", update.reminderAt)
        } else {
            assertNotNull("Un slot futuro sin reminder previo obtiene uno al inicio", update.reminderAt)
        }
    }

    @Test
    fun `condition HAS_OVERDUE_TASKS no se cumple sin vencidas`() {
        val t = task(1, dueAt = now + 86_400_000L) // futuro, no vencida
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(t), 0, now, zone
        )
        assertFalse(plan.matched)
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun `reschedule_overdue reprograma vencidas a partir de manana`() {
        val overdue = task(1, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = now - 1000L)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(overdue), 0, now, zone
        )
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNull(u.startAt)
        assertTrue("La nueva fecha debe ser futura", u.dueAt!! > now)
        assertEquals(TaskStatus.PLANNED, u.status)
        // El reminder se reubica a 1h antes del nuevo dueAt.
        assertEquals(u.dueAt - 3_600_000L, u.reminderAt)
    }

    @Test
    fun `batch_quick_tasks agrupa tareas rapidas y respeta reminder previo`() {
        val existingReminder = now + 7_200_000L
        val quick = listOf(
            task(1, durationMinutes = 5, reminderAt = null),
            task(2, durationMinutes = 10, reminderAt = existingReminder),
            task(3, durationMinutes = 5, reminderAt = null)
        )
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick, 0, now, zone
        )
        assertTrue(plan.matched)
        assertEquals(3, plan.updates.size)
        // La tarea 2 conserva su reminder previo; las demás no tenían y obtienen uno al inicio.
        val byId = plan.updates.associateBy { it.id }
        assertEquals(existingReminder, byId[2]!!.reminderAt)
        assertEquals(TaskStatus.PLANNED, byId[2]!!.status)
    }

    @Test
    fun `batch_quick_tasks no duplica tareas con mismo slot`() {
        val quick = (1..6).map { task(it.toLong(), durationMinutes = 5) }
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick, 0, now, zone
        )
        val starts = plan.updates.map { it.startAt }
        assertEquals("Sin slots duplicados", starts.distinct().size, starts.size)
    }

    @Test
    fun `review_commitments crea una tarea y evita duplicados`() {
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.REVIEW_COMMITMENTS, AutomationCondition.HAS_PENDING_COMMITMENTS),
            emptyList(), 3, now, zone
        )
        assertTrue(plan.matched)
        assertEquals(1, plan.creates.size)
        assertTrue(plan.creates.first().title.contains("3"))

        // Una segunda ejecución no debe crear un duplicado si ya existe la revisión.
        val existing = plan.creates.first().copy(id = 10)
        val plan2 = AutomationActionPlanner.build(
            rule(AutomationAction.REVIEW_COMMITMENTS, AutomationCondition.HAS_PENDING_COMMITMENTS),
            listOf(existing), 3, now, zone
        )
        assertFalse(plan2.matched)
        assertTrue(plan2.creates.isEmpty())
    }

    @Test
    fun `review_commitments no se dispara sin compromisos`() {
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.REVIEW_COMMITMENTS, AutomationCondition.HAS_PENDING_COMMITMENTS),
            emptyList(), 0, now, zone
        )
        assertFalse(plan.matched)
    }

    @Test
    fun `batch_quick_tasks ignora tareas largas`() {
        val mixed = listOf(
            task(1, durationMinutes = 5),
            task(2, durationMinutes = 60)
        )
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            mixed, 0, now, zone
        )
        assertTrue(plan.matched)
        assertEquals(1, plan.updates.size)
        assertEquals(1L, plan.updates.first().id)
    }
}
