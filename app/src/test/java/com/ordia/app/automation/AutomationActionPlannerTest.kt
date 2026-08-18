package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.DateRules
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationActionPlannerTest {

    private val zone = ZoneId.of("America/Santiago")
    private val now = 1_736_773_200_000L // 2025-01-13 10:00 America/Santiago

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
        startAt: Long? = null,
        recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
        priority: TaskPriority = TaskPriority.NORMAL
    ) = TaskEntity(
        id = id,
        title = title,
        durationMinutes = durationMinutes,
        status = status,
        dueAt = dueAt,
        reminderAt = reminderAt,
        startAt = startAt,
        recurrence = recurrence,
        priority = priority,
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
        // now=10:00 → el primer slot respeta `now` (c.209): arranca a las 10:00.
        // startAt == now → el slot no es estrictamente futuro → sin reminder tardío.
        // (Contrato past-safe: nunca se genera un aviso para un slot que ya empezó.)
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = null)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)

        val update = plan.updates.first()
        if (update.startAt != null && update.startAt <= now) {
            assertNull("Un slot que ya empezó no debe generar recordatorio tardío", update.reminderAt)
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
    fun `reschedule_overdue prioriza una vencida URGENT aunque otra vencida tenga dueAt mas viejo`() {
        // "Detección de vencidas importantes": la reprogramación automática no debe
        // aplazar un compromiso URGENT detrás de uno NORMAL sólo porque el NORMAL se
        // venció antes. La prioridad explícita del usuario (URGENT) es la señal más
        // fuerte de "lo más importante a recuperar"; la antigüedad del vencimiento la
        // desempata dentro de la misma prioridad. PRE-fix: orden por dueAt asc puro →
        // NORMAL (now-5d) → índice 0 → base+1; URGENT (now-1d) → índice 1 → base+1.
        // Ambas caían el mismo día aquí, pero el ORDEN dentro del día era equivocado:
        // la NORMAL ocupaba el primer lugar. Con prioridad primero, URGENT va índice 0.
        val urgent = task(1, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null, priority = TaskPriority.URGENT)
        val normal = task(2, dueAt = now - 5 * 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null, priority = TaskPriority.NORMAL)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(normal, urgent), 0, now, zone
        )
        assertEquals(2, plan.updates.size)
        // URGENT → índice 0 → base+1 (el slot más temprano).
        assertEquals(1_736_888_400_000L, plan.updates[0].dueAt) // 2025-01-14 18:00
        assertEquals(1L, plan.updates[0].id)
        // NORMAL → índice 1 → base+1 (mismo día, pero detrás de la URGENT).
        assertEquals(1_736_888_400_000L, plan.updates[1].dueAt)
        assertEquals(2L, plan.updates[1].id)
    }

    @Test
    fun `reschedule_overdue no aplaza una URGENT detras de tres NORMAL aunque estas venzan antes`() {
        // Caso de 4 vencidas: 3 NORMAL muy viejas (now-3d/4d/5d) + 1 URGENT reciente
        // (now-1d). Con el reparto de 3/día, el índice 0-2 va a base+1 y el índice 3 a
        // base+2. PRE-fix (dueAt asc puro): las 3 NORMAL (índices 0-2) → base+1 y la
        // URGENT (índice 3) → base+2: la URGENT queda aplazada un día entero detrás de
        // tareas triviales. POST-fix (prioridad primero): la URGENT → índice 0 → base+1.
        val urgent = task(10, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null, priority = TaskPriority.URGENT)
        val normals = (1..3).map { task(it.toLong(), dueAt = now - (it + 2) * 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null, priority = TaskPriority.NORMAL) }
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            normals + urgent, 0, now, zone
        )
        assertEquals(4, plan.updates.size)
        // La URGENT debe estar en base+1 (el primer día), no en base+2.
        assertEquals(1_736_888_400_000L, plan.updates[0].dueAt) // 2025-01-14 18:00 = base+1
        assertEquals(10L, plan.updates[0].id)
        // Las 3 NORMAL rellenan los índices 1-3 (índice 3 → base+2).
        assertEquals(1_736_974_800_000L, plan.updates[3].dueAt) // 2025-01-15 18:00 = base+2
    }

    @Test
    fun `reschedule_overdue desempata vencidas de igual prioridad por dueAt asc`() {
        // Contrato de no-regresión: con prioridades iguales, el orden sigue siendo el
        // de antigüedad (la más vieja primero), igual que el comportamiento pre-fix.
        val a = task(1, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null, priority = TaskPriority.HIGH)
        val b = task(2, dueAt = now - 3 * 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null, priority = TaskPriority.HIGH)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(a, b), 0, now, zone
        )
        assertEquals(2, plan.updates.size)
        // b (más vieja, now-3d) → índice 0 → base+1; a (más nueva, now-1d) → índice 1 → base+1.
        assertEquals(2L, plan.updates[0].id)
        assertEquals(1L, plan.updates[1].id)
    }

    @Test
    fun `plan_day deriva la fecha del now inyectado, no del reloj del sistema`() {
        // Determinismo: los slots del plan deben fecharse con el día de `now`
        // (2025-01-13 10:00). Antes, LocalDate.now(zone) fechaba con el reloj real y el
        // primer slot caía en una fecha distinta según cuándo se ejecutara. El inicio del
        // slot respeta `now` (c.209): a las 10:00 el primer slot es 10:00, no 09:00.
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, reminderAt = null)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)

        assertTrue(plan.matched)
        assertEquals(
            "El primer slot debe ser 2025-01-13 10:00 (día del now inyectado, sin pasar al pasado)",
            1_736_773_200_000L,
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

    @Test
    fun `plan_day no deja startAt despues de dueAt`() {
        // Tarea vencida/temprana (due 08:00) colocada en el primer slot del plan
        // (09:00, posterior al now 10:00). Antes, conservar el due 08:00 dejaba
        // startAt (09:00) > dueAt (08:00): estado que [BackupManager] rechaza al
        // restaurar ("Una tarea comienza después de su vencimiento"), así un backup
        // tomado tras planificar era IRRESTAURABLE. Ahora el due sigue al fin del slot.
        val dueBeforeSlot = 1_736_766_000_000L // 2025-01-13 08:00 America/Santiago
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, dueAt = dueBeforeSlot)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNotNull("Debe asignarse un slot", u.startAt)
        assertNotNull("Debe tener un due", u.dueAt)
        assertTrue(
            "startAt (${u.startAt}) no debe superar a dueAt (${u.dueAt})",
            u.startAt!! <= u.dueAt!!
        )
    }

    @Test
    fun `plan_day conserva dueAt nulo en tarea de inbox`() {
        // No-regresión: una tarea de bandeja (sin due) planificada NO debe ganar un
        // vencimiento espurio. El due sigue siendo nulo (la planificación mueve
        // startAt/status/reminder, no inventa un due donde no lo había).
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, dueAt = null)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, now, zone)
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNull("Una tarea sin due no debe ganar vencimiento al planificarse", u.dueAt)
    }

    @Test
    fun `has_inbox_tasks no se cumple si solo hay subtareas sin due`() {
        // c.219: la condición HAS_INBOX_TASKS inflaba con subtareas. `active` no
        // filtraba parentTaskId == null, así una subtarea con dueAt == null (sin
        // vencimiento propio, muy común en subtareas que heredan del padre) hacía
        // que `active.any { it.status == INBOX || it.dueAt == null }` fuera cierto
        // aunque NO hubiera ninguna tarea raíz de bandeja. La automatización
        // "si hay tareas en bandeja, planifica el día" se disparaba sin tareas
        // raíz reales que planificar. Misma clase de inflación por subtareas que
        // AssistantEngine (c.218): debe contar solo raíces, como WhatNowEngine,
        // GuardianEngine, SummaryEngine y DayPlanner.
        val subtaskNoDue = task(2, durationMinutes = 30, status = TaskStatus.INBOX, dueAt = null)
            .copy(parentTaskId = 1L)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.PLAN_DAY, AutomationCondition.HAS_INBOX_TASKS),
            listOf(subtaskNoDue),
            0, now, zone
        )
        assertFalse("Sin tareas raíz en bandeja la condición no debe cumplirse", plan.matched)
        assertTrue(
            "El mensaje debe indicar que la condición no se cumple, no planificar",
            plan.message.contains("no se cumple")
        )
    }

    @Test
    fun `plan_day arrancado tarde no escribe slots en el pasado`() {
        // c.209: si PLAN_DAY se dispara a las 11:00, antes arrancaba los slots a las
        // 09:00 (pasado). Una tarea de bandeja quedaba con startAt 09:00 → "inicio
        // perdido" (isMissedStart) y reminder nulo; una vencida re-planificada en un
        // slot pasado seguía vencida con su due también en el pasado. "Planificar el
        // día" creaba tareas olvidadas: justo lo opuesto a su propósito. Ahora el
        // cursor arranca en max(09:00, now): ningún slot escrito cae antes de `now`.
        val noonNow = 1_736_776_800_000L // 2025-01-13 11:00 America/Santiago
        val inbox = task(1, durationMinutes = 30, status = TaskStatus.INBOX, dueAt = null)
        val overdue = task(
            2, durationMinutes = 30, status = TaskStatus.PLANNED,
            dueAt = noonNow - 86_400_000L // vencida ayer
        )
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.PLAN_DAY), listOf(inbox, overdue), 0, noonNow, zone
        )
        assertTrue(plan.matched)
        assertTrue("Debe planificar al menos una tarea", plan.updates.isNotEmpty())
        plan.updates.forEach { u ->
            assertNotNull("Toda tarea planificada debe tener startAt", u.startAt)
            assertTrue(
                "Ningún slot debe caer estrictamente antes de now (startAt ${u.startAt} < now $noonNow)",
                u.startAt!! >= noonNow
            )
            if (u.dueAt != null) {
                assertTrue(
                    "El vencimiento re-planificado tampoco debe quedar en el pasado (dueAt ${u.dueAt} < now $noonNow)",
                    u.dueAt!! >= noonNow
                )
            }
        }
    }

    @Test
    fun `plan_day muy tarde no planifica nada`() {
        // c.209: si no queda ventana hoy (now >= 18:00), no se escriben slots pasados.
        val lateNow = 1_736_802_000_000L // 2025-01-13 18:00 America/Santiago (dayStart >= dayEnd)
        val t = task(1, durationMinutes = 30, status = TaskStatus.INBOX, dueAt = null)
        val plan = AutomationActionPlanner.build(rule(AutomationAction.PLAN_DAY), listOf(t), 0, lateNow, zone)
        assertFalse("Muy tarde no debe planificar nada pasado", plan.matched)
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun `batch_quick_tasks no deja startAt despues de dueAt`() {
        // Tarea rápida con due (09:30) anterior al slot agrupado (10:15, posterior al
        // now 10:00). Conservar el due 09:30 dejaría startAt (10:15) > dueAt (09:30),
        // mismo bug de integridad que PLAN_DAY (backup irrestaurable). El due sigue
        // al fin del slot.
        val dueBeforeSlot = now - 1_800_000L // 2025-01-13 09:30 (anterior al now 10:00)
        val quick = listOf(task(1, durationMinutes = 5, dueAt = dueBeforeSlot))
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick, 0, now, zone
        )
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNotNull("Debe asignarse un slot", u.startAt)
        assertNotNull("Debe tener un due", u.dueAt)
        assertTrue(
            "startAt (${u.startAt}) no debe superar a dueAt (${u.dueAt})",
            u.startAt!! <= u.dueAt!!
        )
    }

    @Test
    fun `batch_quick_tasks conserva dueAt nulo en tarea de inbox`() {
        // No-regresión: una tarea rápida sin due agrupada NO debe ganar vencimiento
        // espurio (el due sigue nulo).
        val quick = listOf(task(1, durationMinutes = 5, dueAt = null))
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick, 0, now, zone
        )
        assertTrue(plan.matched)
        val u = plan.updates.first()
        assertNull("Una tarea rápida sin due no debe ganar vencimiento al agruparse", u.dueAt)
    }

    @Test
    fun `reschedule_overdue no reprograma una tarea en curso`() {
        // Sacro: una tarea que el usuario está ejecutando (status IN_PROGRESS, vencida)
        // no debe ser reprogramada automáticamente a mañana. Una automatización que
        // "reprograme vencidas" no debe pisar el trabajo activo: lo resetearía a
        // PLANNED, le borraría el startAt y le empujaría el vencimiento, descarrilando
        // lo que el usuario hace ahora mismo. Simétrico con GuardianEngine, que excluye
        // lo "en curso" de señalar vencidas, y con timeRank, que lo coloca arriba.
        val inProgress = task(
            1,
            dueAt = now - 86_400_000L, // vencida
            status = TaskStatus.IN_PROGRESS,
            startAt = now - 600_000L // ventana activa
        )
        val normal = task(2, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(inProgress, normal), 0, now, zone
        )
        assertTrue(plan.matched)
        val byId = plan.updates.associateBy { it.id }
        assertNull("La tarea en curso no debe reprogramarse", byId[1L])
        assertNotNull("La vencida normal sí debe reprogramarse", byId[2L])
    }

    @Test
    fun `batch_quick_tasks no replanifica una tarea rapida en curso`() {
        // Sacro: una tarea rápida en curso no debe ser reubicada en un nuevo slot.
        // Reagruparla pisaría el startAt activo y la resetearía a PLANNED, interrumpiendo
        // lo que el usuario hace ahora. Mismo principio que reschedule_overdue.
        val inProgress = task(
            1,
            durationMinutes = 5,
            status = TaskStatus.IN_PROGRESS,
            startAt = now - 120_000L,
            dueAt = now + 3_600_000L
        )
        val inbox = task(2, durationMinutes = 5, dueAt = null)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            listOf(inProgress, inbox), 0, now, zone
        )
        assertTrue(plan.matched)
        val byId = plan.updates.associateBy { it.id }
        assertNull("La tarea rápida en curso no debe reasignarse", byId[1L])
        assertNotNull("La rápida de inbox sí debe agruparse", byId[2L])
    }

    @Test
    fun `reschedule_overdue no reprograma una tarea recurrente`() {
        // Sacro recurrencia: una tarea recurrente vencida NO debe ser reprogramada.
        // Su `dueAt` es el ANCLA de la cadencia: [RecurrenceEngine.nextOccurrence]
        // deriva de él (y de reminderAt/startAt) los offsets que reutiliza para todas
        // las ocurrencias futuras. Mover el vencimiento a "mañana 18:00" desplazaría
        // cada ciclo venidero (p. ej. una mensual "pagar el 1" vencida el 3 arrastraría
        // toda la serie). El motor de recurrencia gestiona su ciclo: completarla
        // engendra la siguiente ocurrencia. Simétrico con el sacro "en curso".
        val recurring = task(
            1,
            dueAt = now - 86_400_000L, // vencida
            status = TaskStatus.PLANNED,
            reminderAt = now - 2 * 86_400_000L,
            recurrence = RecurrenceFrequency.MONTHLY
        )
        val normal = task(2, dueAt = now - 86_400_000L, status = TaskStatus.PLANNED, reminderAt = null)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(recurring, normal), 0, now, zone
        )
        assertTrue(plan.matched)
        val byId = plan.updates.associateBy { it.id }
        assertNull("La recurrente vencida no debe reprogramarse (corrompería la cadencia)", byId[1L])
        assertNotNull("La vencida normal sí debe reprogramarse", byId[2L])
    }

    @Test
    fun `reschedule_overdue condicion no se cumple si solo hay recurrentes vencidas`() {
        // Si las ÚNICAS vencidas son recurrentes, no hay nada que automatizar sin
        // corromper datos: la condición HAS_OVERDUE_TASKS no se cumple (no dispara
        // la acción). La recurrente se recupera por completado/spawn, no por
        // reprogramación masiva. El filtro fluye de los candidatos a la condición.
        val recurring = task(
            1,
            dueAt = now - 86_400_000L,
            status = TaskStatus.PLANNED,
            recurrence = RecurrenceFrequency.WEEKLY
        )
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.RESCHEDULE_OVERDUE, AutomationCondition.HAS_OVERDUE_TASKS),
            listOf(recurring), 0, now, zone
        )
        assertFalse("Sin vencidas mutables, la condición no se cumple", plan.matched)
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun `batch_quick_tasks no replanifica una tarea rapida recurrente`() {
        // Sacro recurrencia: una tarea rápida recurrente (p. ej. "regar cada lunes",
        // 5 min) no debe reagruparse en un slot. Pisaría su startAt/dueAt y, con ello,
        // el startOffset/reminderOffset que el motor reutiliza en cada spawn. Misma
        // razón que reschedule_overdue.
        val recurring = task(
            1,
            durationMinutes = 5,
            dueAt = now + 3_600_000L,
            status = TaskStatus.PLANNED,
            recurrence = RecurrenceFrequency.WEEKLY
        )
        val inbox = task(2, durationMinutes = 5, dueAt = null)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            listOf(recurring, inbox), 0, now, zone
        )
        assertTrue(plan.matched)
        val byId = plan.updates.associateBy { it.id }
        assertNull("La rápida recurrente no debe reagruparse (corrompería la cadencia)", byId[1L])
        assertNotNull("La rápida de inbox sí debe agruparse", byId[2L])
    }

    @Test
    fun `plan_day no muta una tarea recurrente`() {
        // Sacro recurrencia: una tarea recurrente con vencimiento HOY no debe entrar
        // en el plan del día (que pisaría su startAt/reminderAt/status). Su slot es su
        // propio dueAt, gestionado por el motor de recurrencia; planificarla en un
        // bloque horizontal desplazaría su ancla y, con ella, los offsets de toda la
        // serie. Se excluye igual que las "en curso".
        val dayStart = DateRules.toEpochMillis(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            java.time.LocalTime.of(9, 0),
            zone
        )
        val recurring = task(
            1,
            durationMinutes = 30,
            dueAt = dayStart + 3 * 60 * 60_000L, // vence hoy 12:00
            status = TaskStatus.PLANNED,
            recurrence = RecurrenceFrequency.DAILY
        )
        val inbox = task(2, durationMinutes = 30, status = TaskStatus.INBOX)
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.PLAN_DAY),
            listOf(recurring, inbox), 0, now, zone
        )
        assertTrue(plan.matched)
        val byId = plan.updates.associateBy { it.id }
        assertNull("La recurrente no debe mutarse en el plan (corrompería la cadencia)", byId[1L])
        assertNotNull("La tarea de inbox sí debe planificarse", byId[2L])
    }

    // --- BATCH_QUICK_TASKS respeta compromisos FIJOS agendados (c.560) ---
    // Simétrico con el fix de DayPlanner (c.559): el cursor lineal de BATCH_QUICK_TASKS
    // no rodeaba reuniones/compromisos fijos con `startAt` hoy que NO son candidatas
    // (pura reunión sin dueAt, duración > 10 min). Colocaba tareas rápidas ENCIMA de
    // la reunión al avanzar el cursor, pisando el compromiso agendado del usuario al
    // aplicarse la automatización.

    @Test
    fun `batch_quick_tasks no pisa un compromiso fijo agendado`() {
        // now 10:00. Reunión FIJA 10:30-11:30 (startAt hoy, sin dueAt → no candidata,
        // duración 60 > 10 → no quick). 5 tareas rápidas de 5 min: el cursor lineal
        // PRE-fix colocaba las 3 últimas (10:35, 10:45, 10:55) DENTRO de la reunión.
        val meetingStart = now + 30 * 60_000L // 10:30 hoy
        val meeting = task(
            10,
            durationMinutes = 60,
            startAt = meetingStart,
            status = TaskStatus.PLANNED
        )
        val quick = (1..5).map { task(it.toLong(), durationMinutes = 5) }
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick + meeting, 0, now, zone
        )
        assertTrue(plan.matched)
        // La reunión no se muta (no es quick): no aparece en updates.
        val byId = plan.updates.associateBy { it.id }
        assertNull("La reunión fija no debe mutarse", byId[10L])
        // Ninguna tarea rápida puede caer dentro del intervalo [10:30, 11:30) en minutos
        // del día (630..690). El cursor debe RODEAR la reunión, no pisarla.
        val meetingStartMin = 10 * 60 + 30
        val meetingEndMin = 11 * 60 + 30
        plan.updates.forEach { u ->
            val startZ = Instant.ofEpochMilli(u.startAt!!).atZone(zone)
            val startMin = startZ.hour * 60 + startZ.minute
            assertTrue(
                "Tarea ${u.id} en $startMin cae DENTRO de la reunión [$meetingStartMin, $meetingEndMin)",
                startMin < meetingStartMin || startMin >= meetingEndMin
            )
        }
    }

    @Test
    fun `batch_quick_tasks coloca tareas rapidas antes y despues de la reunion`() {
        // Variante: con now 09:00 hay margen para colocar rápidas ANTES de la reunión
        // 10:30 y luego SALTAR a su fin. PRE-fix el cursor atravesaba la reunión.
        val earlyNow = 1_736_769_600_000L // 2025-01-13 09:00 America/Santiago
        val meetingStart = earlyNow + 90 * 60_000L // 10:30
        val meeting = task(
            10,
            durationMinutes = 60,
            startAt = meetingStart,
            status = TaskStatus.PLANNED
        )
        // 6 rápidas de 5 min: cabe 1 antes (09:15-09:20) y el resto tras la reunión.
        val quick = (1..6).map { task(it.toLong(), durationMinutes = 5, dueAt = null) }
        val plan = AutomationActionPlanner.build(
            rule(AutomationAction.BATCH_QUICK_TASKS, AutomationCondition.HAS_QUICK_TASKS),
            quick + meeting, 0, earlyNow, zone
        )
        assertTrue(plan.matched)
        val meetingStartMin = 10 * 60 + 30
        val meetingEndMin = 11 * 60 + 30
        plan.updates.forEach { u ->
            val startZ = Instant.ofEpochMilli(u.startAt!!).atZone(zone)
            val startMin = startZ.hour * 60 + startZ.minute
            assertTrue(
                "Tarea ${u.id} en $startMin cae DENTRO de la reunión [$meetingStartMin, $meetingEndMin)",
                startMin < meetingStartMin || startMin >= meetingEndMin
            )
        }
    }
}
