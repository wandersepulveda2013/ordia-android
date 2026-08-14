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
    fun `reschedule_overdue conserva el offset de reminder del usuario`() {
        // Vencida con reminder 2 h antes del dueAt original: el offset debe conservarse
        // (no forzar a 1 h), o se corrompería la cadencia de ocurrencias recurrentes.
        val oldDue = now - 86_400_000L
        val overdue = task(1, dueAt = oldDue, status = TaskStatus.PLANNED, reminderAt = oldDue - 2 * 3_600_000L)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(overdue), 0, now, zone
        )
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNull(u.startAt)
        val newDue = u.dueAt!!
        assertTrue("La nueva fecha debe ser futura", newDue > now)
        assertEquals(TaskStatus.PLANNED, u.status)
        // Offset 2 h conservado (no 1 h): reminder = nuevoDue - 2 h.
        assertEquals(newDue - 2 * 3_600_000L, u.reminderAt)
    }

    @Test
    fun `reschedule_overdue anade reminder cuando no existia`() {
        // Una vencida SIN reminder no debe quedar al olvido: se añade 1 h antes del
        // nuevo vencimiento (siempre futuro). Antes el recordatorio se descartaba.
        val overdue = task(1, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(overdue), 0, now, zone
        )
        val u = plan.updates.first()
        assertNotNull("Una vencida reprogramada sin reminder previo obtiene uno", u.reminderAt)
        val due = u.dueAt!!
        assertEquals(due - 3_600_000L, u.reminderAt)
        assertTrue("El recordatorio debe ser futuro", u.reminderAt!! > now)
    }

    @Test
    fun `reschedule_overdue no deja el reminder en el pasado cuando el offset es grande`() {
        // Offset grande (alcanzable vía parser: "recuérdame 2 días antes" → 2*24*60 min).
        // Vencida 1 día con reminder 2 días antes del dueAt original: trasladar ese offset
        // al nuevo vencimiento (mañana 18:00) deja el reminder ~27 h en el PASADO. Un
        // reminder pasado lo descarta ReminderSync (trigger <= now → null), así la tarea
        // reprogramada volvía a quedar SIN aviso → el usuario la olvidaba otra vez, justo
        // lo que RESCHEDULE_OVERDUE debe evitar. Debe caer a un default futuro (1 h antes).
        val offsetMs = 2L * 86_400_000L // 2 días
        val oldDue = now - 86_400_000L // vencida hace 1 día
        val overdue = task(1, dueAt = oldDue, status = TaskStatus.PLANNED, reminderAt = oldDue - offsetMs)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(overdue), 0, now, zone
        )
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNotNull("La vencida reprogramada debe conservar un reminder", u.reminderAt)
        assertTrue(
            "El reminder no debe caer en el pasado (offset grande trasladado): got ${u.reminderAt}",
            u.reminderAt!! > now
        )
        // Y no debe ser descartado por la re-sincronización (evita olvidos).
        assertTrue(
            "El reminder futuro debe ser re-encolado por ReminderSync",
            com.ordia.app.domain.ReminderSync.triggers(listOf(u), now).isNotEmpty()
        )
    }

    @Test
    fun `reschedule_overdue deriva la fecha del now inyectado, no del reloj del sistema`() {
        // Determinismo: la fecha base de reprogramación debe calcularse desde el `now`
        // inyectado. Antes se usaba LocalDate.now(zone) (reloj real), de modo que la
        // nueva fecha dependía del instante de ejecución y ningún test podía fijarla.
        // Con now=2025-01-13, la primera vencida (índice 0) va a 2025-01-14 18:00.
        val overdue = task(1, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(overdue), 0, now, zone
        )
        assertTrue(plan.matched)
        assertEquals(
            "La fecha debe ser mañana a las 18:00 del now inyectado (2025-01-14)",
            1_736_888_400_000L,
            plan.updates.first().dueAt
        )
    }

    @Test
    fun `reschedule_overdue reparte las vencidas en bloques de tres dias`() {
        // Índice 0-2 → base+1; índice 3-5 → base+2. Las vencidas se ordenan por dueAt
        // asc (la más vieja primero), de modo que la deuda más antigua se reprograma
        // antes. La más vieja (t4, now-4d) → índice 0 → base+1; la más nueva (t1,
        // now-1d) → índice 3 → base+2. Esto además fija el día base desde `now`.
        val overdue = (1..4).map { task(it.toLong(), dueAt = now - it * 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null) }
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            overdue, 0, now, zone
        )
        assertEquals(4, plan.updates.size)
        val byId = plan.updates.associateBy { it.id }
        assertEquals(1_736_888_400_000L, byId[4L]!!.dueAt) // más vieja → índice 0 → base+1 = 2025-01-14 18:00
        assertEquals(1_736_974_800_000L, byId[1L]!!.dueAt) // más nueva → índice 3 → base+2 = 2025-01-15 18:00
    }

    @Test
    fun `plan_day deriva la fecha del now inyectado, no del reloj del sistema`() {
        // Determinismo: los slots del plan deben fecharse con el día de `now`
        // (2025-01-13 09:00). Antes, LocalDate.now(zone) fechaba con el reloj real y el
        // primer slot caía en una fecha distinta según cuándo se ejecutara.
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = null)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)

        assertTrue(plan.matched)
        assertEquals(
            "El primer slot debe ser 2025-01-13 09:00 (día del now inyectado)",
            1_736_769_600_000L,
            plan.updates.first().startAt
        )
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

    @Test
    fun `batch_quick_tasks reemplaza reminder previo pasado por uno futuro en el nuevo slot`() {
        // Una tarea rápida vencida cuyo recordatorio ya disparó (reminderAt en el PASADO).
        // Al agruparla en un slot futuro, conservar el reminder pasado literalmente lo
        // dejaría SIN aviso (ReminderSync descarta trigger <= now), justo lo que
        // BATCH_QUICK_TASKS debe evitar. Simétrico con RESCHEDULE_OVERDUE (c.187): un
        // reminder pasado recae a un default futuro (el inicio del slot, si es futuro).
        val pastReminder = now - 86_400_000L // ayer: ya disparó
        val quick = listOf(task(1, durationMinutes = 5, reminderAt = pastReminder))
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick, 0, now, zone
        )
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNotNull("La tarea agrupada debe conservar un reminder", u.reminderAt)
        assertTrue(
            "El reminder no debe quedar en el pasado (slot futuro sin aviso): got ${u.reminderAt}",
            u.reminderAt!! > now
        )
        assertTrue(
            "El reminder futuro debe ser re-encolado por ReminderSync",
            com.ordia.app.domain.ReminderSync.triggers(listOf(u), now).isNotEmpty()
        )
    }

    @Test
    fun `batch_quick_tasks conserva reminder previo futuro`() {
        // No-regresión: un reminder previo aún FUTURO se respeta (no se sobrescribe).
        val futureReminder = now + 7_200_000L
        val quick = listOf(task(1, durationMinutes = 5, reminderAt = futureReminder))
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick, 0, now, zone
        )
        val u = plan.updates.first()
        assertEquals(futureReminder, u.reminderAt)
    }

    @Test
    fun `plan_day reemplaza reminder previo pasado por uno futuro cuando el slot es futuro`() {
        // Una tarea vencida con reminder pasado entra al plan (DayPlanner prioriza vencidas)
        // y se le asigna un slot futuro. Conservar el reminder pasado literalmente lo
        // dejaría SIN aviso para el nuevo slot. Simétrico con RESCHEDULE_OVERDUE (c.187):
        // un reminder pasado recae al default (inicio del slot, si es futuro).
        // now a las 08:00 (el primer slot del plan, 09:00, es futuro → expone el bug).
        val earlyNow = 1_736_766_000_000L // 2025-01-13 08:00 America/Santiago
        val pastReminder = earlyNow - 86_400_000L
        val overdue = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = pastReminder).copy(
            createdAt = earlyNow - 1000,
            updatedAt = earlyNow - 1000
        )
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(overdue), 0, earlyNow, zone)
        assertTrue(plan.matched)
        val u = plan.updates.first()
        // El slot asignado (09:00) debe ser futuro respecto al now (08:00).
        assertNotNull("Debe asignarse un slot", u.startAt)
        assertTrue("El slot debe ser futuro: got ${u.startAt}", u.startAt!! > earlyNow)
        assertNotNull("Un slot futuro con reminder previo pasado debe conservar un aviso", u.reminderAt)
        assertTrue(
            "El reminder no debe quedar en el pasado (slot futuro sin aviso): got ${u.reminderAt}",
            u.reminderAt!! > earlyNow
        )
        assertTrue(
            "El reminder futuro debe ser re-encolado por ReminderSync",
            com.ordia.app.domain.ReminderSync.triggers(listOf(u), earlyNow).isNotEmpty()
        )
    }

    @Test
    fun `plan_day conserva reminder previo futuro`() {
        // No-regresión: un reminder previo aún FUTURO se respeta (no se sobrescribe).
        val futureReminder = now + 3_600_000L
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = futureReminder)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)
        val u = plan.updates.first()
        assertEquals(futureReminder, u.reminderAt)
    }
}
