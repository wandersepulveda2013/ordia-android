package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskRulesTest {
    private fun task(
        id: Long,
        parentId: Long? = null,
        completed: Boolean = false,
        status: TaskStatus = TaskStatus.INBOX
    ) = TaskEntity(id = id, title = "T$id", parentTaskId = parentId, completed = completed, status = status)

    private fun cancelled(id: Long, parentId: Long? = null) =
        task(id, parentId, completed = false, status = TaskStatus.CANCELLED)

    @Test
    fun progressCountsCompletedOverTotal() {
        val subs = listOf(task(2, 1, completed = true), task(3, 1), task(4, 1, completed = true))

        assertEquals(2 to 3, SubtaskRules.progress(subs))
    }

    @Test
    fun allCompletedRequiresNonEmptyAndAllDone() {
        assertFalse(SubtaskRules.allCompleted(emptyList()))
        assertFalse(SubtaskRules.allCompleted(listOf(task(2, 1, completed = true), task(3, 1))))
        assertTrue(SubtaskRules.allCompleted(listOf(task(2, 1, completed = true), task(3, 1, completed = true))))
    }

    @Test
    fun shouldAutoCompleteParentWhenLastSubtaskClosed() {
        val parent = task(1)
        assertFalse(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1), task(3, 1))))
        assertTrue(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1, completed = true), task(3, 1, completed = true))))
        // padre ya completo: no hace falta autocompletarlo
        assertFalse(SubtaskRules.shouldAutoCompleteParent(task(1, completed = true), listOf(task(2, 1, completed = true))))
    }

    @Test
    fun shouldAutoReopenParentWhenSubtaskReopened() {
        val parent = task(1, completed = true)
        assertFalse(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true))))
        assertTrue(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true), task(3, 1))))
        // padre pendiente: no hay que reabrirlo
        assertFalse(SubtaskRules.shouldAutoReopenParent(task(1), listOf(task(3, 1))))
    }

    // --- CANCELLED se trata como "resuelto" (descartado), no como pendiente ---
    // Continúa el hilo de consistencia de CANCELLED (c.169-c.173): una subtarea
    // cancelada (status=CANCELLED, completed=false) ya no cuenta como trabajo
    // pendiente del padre. Sin esto, una subtarea cancelada (alcanzable hoy vía
    // restore de respaldo) BLOQUEA el autocompletado del padre y FUERZA su
    // reapertura, contradiciendo que el usuario la descartó.

    @Test
    fun cancelledSubtaskCountsAsResolvedForAutoComplete() {
        val parent = task(1)
        // Todas las subtareas canceladas → todas resueltas → el padre se autocompleta
        assertTrue(SubtaskRules.shouldAutoCompleteParent(parent, listOf(cancelled(2, 1), cancelled(3, 1))))
        // Mezcla: una completada + una cancelada → todas resueltas → autocompleta
        assertTrue(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1, completed = true), cancelled(3, 1))))
        // Una pendiente real (ni completada ni cancelada) → NO autocompleta
        assertFalse(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1, completed = true), task(3, 1))))
    }

    @Test
    fun cancelledSubtaskDoesNotForceParentReopen() {
        val parent = task(1, completed = true)
        // Solo hay una cancelada (resuelta) → nada pendiente → no reabrir
        assertFalse(SubtaskRules.shouldAutoReopenParent(parent, listOf(cancelled(2, 1))))
        // Completada + cancelada → ninguna pendiente → no reabrir
        assertFalse(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true), cancelled(3, 1))))
        // Una subtarea realmente pendiente SÍ reabre
        assertTrue(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true), task(3, 1))))
    }

    @Test
    fun allCompletedTreatsCancelledAsResolved() {
        assertTrue(SubtaskRules.allCompleted(listOf(cancelled(2), cancelled(3))))
        assertTrue(SubtaskRules.allCompleted(listOf(task(2, completed = true), cancelled(3))))
        assertFalse(SubtaskRules.allCompleted(listOf(task(2, completed = true), task(3))))
    }

    @Test
    fun depthWalksAncestors() {
        val byId = mapOf(
            1L to task(1),
            2L to task(2, 1),
            3L to task(3, 2),
            4L to task(4, 3)
        )
        assertEquals(0, SubtaskRules.depth(byId.getValue(1L), byId))
        assertEquals(1, SubtaskRules.depth(byId.getValue(2L), byId))
        assertEquals(2, SubtaskRules.depth(byId.getValue(3L), byId))
        assertEquals(3, SubtaskRules.depth(byId.getValue(4L), byId))
    }

    @Test
    fun depthToleratesCycles() {
        // A → B → A (ciclo); no debe colgarse
        val byId = mapOf(
            1L to task(1, 2L),
            2L to task(2, 1L)
        )
        val d = SubtaskRules.depth(byId.getValue(1L), byId)
        assertTrue(d >= 1)
    }

    @Test
    fun depthStopsAtMissingParent() {
        val byId = mapOf(1L to task(1), 2L to task(2, 999L))
        assertEquals(0, SubtaskRules.depth(byId.getValue(2L), byId))
    }

    @Test
    fun canAddSubtaskRespectsMaxDepth() {
        val byId = mapOf(
            1L to task(1),
            2L to task(2, 1),
            3L to task(3, 2),
            4L to task(4, 3)
        )
        assertTrue(SubtaskRules.canAddSubtask(byId.getValue(3L), byId))
        assertFalse(SubtaskRules.canAddSubtask(byId.getValue(4L), byId))
    }

    // --- Clonación de subtareas al crear la próxima ocurrencia de un padre
    // recurrente (c.221, "datos sagrados"/"evitar olvidos"): el desglose en
    // subtareas de una tarea recurrente (p. ej. "Preparar reunión semanal" →
    // "Agenda", "Materiales", "Minutas") se perdía en cada ciclo: la próxima
    // ocurrencia nacía como padre huérfano sin su checklist. El usuario debía
    // recrear las subtareas cada semana o —peor— olvidaba pasos de la rutina.
    // cloneForNextOccurrence devuelve copias frescas (id=0, abiertas, sin
    // planificación heredada del ciclo viejo) enlazadas al nuevo padre. ---

    private fun subtaskTemplate(
        id: Long,
        parentId: Long,
        title: String,
        completed: Boolean = false,
        dueAt: Long? = null,
        recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
        sortOrder: Int = 0,
        durationMinutes: Int = 25,
        priority: TaskPriority = TaskPriority.NORMAL,
        flagged: Boolean = false,
        projectId: Long? = null,
        details: String = "",
    ) = TaskEntity(
        id = id,
        title = title,
        details = details,
        projectId = projectId,
        parentTaskId = parentId,
        dueAt = dueAt,
        durationMinutes = durationMinutes,
        priority = priority,
        status = if (completed) TaskStatus.COMPLETED else TaskStatus.INBOX,
        completed = completed,
        recurrence = recurrence,
        sortOrder = sortOrder,
        flagged = flagged,
    )

    @Test
    fun cloneForNextOccurrence_linksToNewParentAndResetsIdentity() {
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Agenda", sortOrder = 0),
            subtaskTemplate(3, 1, "Materiales", sortOrder = 1, details = "Proyector + cables")
        )
        val clones = SubtaskRules.cloneForNextOccurrence(subs, newParentId = 900, now = now)

        assertEquals(2, clones.size)
        clones.forEach {
            assertEquals(0L, it.id)
            assertEquals(900L, it.parentTaskId)
            assertFalse(it.completed)
            assertEquals(null, it.completedAt)
            assertEquals(now, it.createdAt)
            assertEquals(now, it.updatedAt)
        }
        assertEquals(listOf("Agenda", "Materiales"), clones.map { it.title })
        assertEquals(listOf(0, 1), clones.map { it.sortOrder })
        assertEquals("Proyector + cables", clones[1].details)
    }

    @Test
    fun cloneForNextOccurrence_resetsCompletionState() {
        // Una subtarea COMPLETADA del ciclo viejo renace abierta en el nuevo ciclo.
        val now = 1_700_000_000_000L
        val subs = listOf(subtaskTemplate(2, 1, "Agenda", completed = true))
        val clones = SubtaskRules.cloneForNextOccurrence(subs, newParentId = 900, now = now)

        assertEquals(1, clones.size)
        assertFalse(clones[0].completed)
        assertEquals(null, clones[0].completedAt)
        assertEquals(TaskStatus.INBOX, clones[0].status)
    }

    @Test
    fun cloneForNextOccurrence_clearsStaleScheduling() {
        // La planificación (dueAt/reminderAt/startAt) del ciclo viejo es obsoleta
        // para el nuevo ciclo: se resetea a null para que la subtarea herede el
        // contexto del nuevo padre (igual que una subtarea recién creada).
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Agenda", dueAt = 1_699_000_000_000L)
                .copy(reminderAt = 1_698_000_000_000L, startAt = 1_697_000_000_000L)
        )
        val clones = SubtaskRules.cloneForNextOccurrence(subs, newParentId = 900, now = now)

        assertEquals(null, clones[0].dueAt)
        assertEquals(null, clones[0].reminderAt)
        assertEquals(null, clones[0].startAt)
    }

    @Test
    fun cloneForNextOccurrence_resetsNestedRecurrenceToNone() {
        // Una subtarea con recurrencia propia (anidada) renace SIN recurrencia:
        // clonarla tal cual generaría ocurrencias recursivas anidadas bajo cada
        // ciclo del padre (explosión de tareas). Se resetea a NONE.
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Revisar", recurrence = RecurrenceFrequency.DAILY, dueAt = 1_699_000_000_000L)
        )
        val clones = SubtaskRules.cloneForNextOccurrence(subs, newParentId = 900, now = now)

        assertEquals(RecurrenceFrequency.NONE, clones[0].recurrence)
        assertEquals(1, clones[0].recurrenceInterval)
        assertEquals("", clones[0].recurrenceDays)
    }

    @Test
    fun cloneForNextOccurrence_preservesStructuralFields() {
        // La ESTRUCTURA del checklist sobrevive: duración, prioridad, proyecto,
        // marcado, orden. Es justo lo que el usuario quiere recuperar ciclo a ciclo.
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Materiales", durationMinutes = 45, priority = TaskPriority.HIGH,
                flagged = true, projectId = 7, sortOrder = 3)
        )
        val clones = SubtaskRules.cloneForNextOccurrence(subs, newParentId = 900, now = now)

        assertEquals(45, clones[0].durationMinutes)
        assertEquals(TaskPriority.HIGH, clones[0].priority)
        assertTrue(clones[0].flagged)
        assertEquals(7L, clones[0].projectId)
        assertEquals(3, clones[0].sortOrder)
        assertFalse(clones[0].archived)
    }

    @Test
    fun cloneForNextOccurrence_emptyListReturnsEmpty() {
        assertEquals(emptyList<TaskEntity>(), SubtaskRules.cloneForNextOccurrence(emptyList(), 900, 1_700_000_000_000L))
    }

    // cloneForDuplicate: copia literal del desglose al duplicar una tarea.
    // A diferencia de cloneForNextOccurrence, PRESERVA planificación y recurrencia
    // (igual que el duplicado del padre), sólo reinicia identidad + estado de cierre.

    @Test
    fun cloneForDuplicate_linksToNewParentAndResetsIdentity() {
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Agenda", sortOrder = 0),
            subtaskTemplate(3, 1, "Materiales", sortOrder = 1, details = "Proyector + cables")
        )
        val clones = SubtaskRules.cloneForDuplicate(subs, newParentId = 900, now = now)

        assertEquals(2, clones.size)
        clones.forEach {
            assertEquals(0L, it.id)
            assertEquals(900L, it.parentTaskId)
            assertFalse(it.completed)
            assertEquals(null, it.completedAt)
            assertEquals(now, it.createdAt)
            assertEquals(now, it.updatedAt)
        }
        assertEquals(listOf("Agenda", "Materiales"), clones.map { it.title })
        assertEquals(listOf(0, 1), clones.map { it.sortOrder })
        assertEquals("Proyector + cables", clones[1].details)
    }

    @Test
    fun cloneForDuplicate_resetsCompletionState() {
        // Una subtarea completada del original renace abierta en la copia.
        val now = 1_700_000_000_000L
        val subs = listOf(subtaskTemplate(2, 1, "Agenda", completed = true))
        val clones = SubtaskRules.cloneForDuplicate(subs, newParentId = 900, now = now)

        assertEquals(1, clones.size)
        assertFalse(clones[0].completed)
        assertEquals(null, clones[0].completedAt)
        assertEquals(TaskStatus.INBOX, clones[0].status)
    }

    @Test
    fun cloneForDuplicate_preservesScheduling() {
        // Duplicar es una copia literal: la planificación del original sobrevive
        // (igual que el duplicado del padre conserva dueAt/reminderAt).
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Agenda", dueAt = 1_699_000_000_000L)
                .copy(reminderAt = 1_698_000_000_000L, startAt = 1_697_000_000_000L)
        )
        val clones = SubtaskRules.cloneForDuplicate(subs, newParentId = 900, now = now)

        assertEquals(1_699_000_000_000L, clones[0].dueAt)
        assertEquals(1_698_000_000_000L, clones[0].reminderAt)
        assertEquals(1_697_000_000_000L, clones[0].startAt)
    }

    @Test
    fun cloneForDuplicate_preservesRecurrence() {
        // La recurrencia propia de una subtarea se conserva al duplicar (no hay
        // anidamiento bajo un padre recurrente, así que no hay explosión).
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Revisar", recurrence = RecurrenceFrequency.WEEKLY, dueAt = 1_699_000_000_000L)
                .copy(recurrenceInterval = 2, recurrenceDays = "MO,WE")
        )
        val clones = SubtaskRules.cloneForDuplicate(subs, newParentId = 900, now = now)

        assertEquals(RecurrenceFrequency.WEEKLY, clones[0].recurrence)
        assertEquals(2, clones[0].recurrenceInterval)
        assertEquals("MO,WE", clones[0].recurrenceDays)
    }

    @Test
    fun cloneForDuplicate_preservesStructuralFields() {
        val now = 1_700_000_000_000L
        val subs = listOf(
            subtaskTemplate(2, 1, "Materiales", durationMinutes = 45, priority = TaskPriority.HIGH,
                flagged = true, projectId = 7, sortOrder = 3)
        )
        val clones = SubtaskRules.cloneForDuplicate(subs, newParentId = 900, now = now)

        assertEquals(45, clones[0].durationMinutes)
        assertEquals(TaskPriority.HIGH, clones[0].priority)
        assertTrue(clones[0].flagged)
        assertEquals(7L, clones[0].projectId)
        assertEquals(3, clones[0].sortOrder)
        assertFalse(clones[0].archived)
    }

    @Test
    fun cloneForDuplicate_emptyListReturnsEmpty() {
        assertEquals(emptyList<TaskEntity>(), SubtaskRules.cloneForDuplicate(emptyList(), 900, 1_700_000_000_000L))
    }
}
