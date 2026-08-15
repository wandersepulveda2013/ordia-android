package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus

/**
 * Reglas de subtareas: progreso, autocompletado del padre cuando se cierra
 * la última subtarea, reapertura del padre al reactivar una subtarea y
 * límite de profundidad para mantener el árbol manejable.
 *
 * Las reglas son puras y deterministas: reciben solo entidades y devuelven
 * decisiones, sin tocar la base de datos.
 */
object SubtaskRules {

    /** Profundidad máxima de anidamiento: raíz(0) → sub(1) → sub(2) → sub(3). */
    const val MAX_DEPTH = 3

    /**
     * Una subtarea está "resuelta" si se completó o se canceló (descartó).
     * Cancelar una subtarea la saca del trabajo pendiente del padre igual que
     * completarla: coherente con que `TaskRules.isActive` excluye CANCELLED en
     * todas las demás superficies (hilo c.169-c.173). Sin esto, una subtarea
     * cancelada (alcanzable vía restore de respaldo) bloqueaba el
     * autocompletado del padre y forzaba su reapertura.
     */
    private fun isResolved(task: TaskEntity): Boolean =
        task.completed || task.status == TaskStatus.CANCELLED

    /** Progreso (completadas, total) de las subtareas directas. */
    fun progress(subtasks: List<TaskEntity>): Pair<Int, Int> =
        subtasks.count { it.completed } to subtasks.size

    fun allCompleted(subtasks: List<TaskEntity>): Boolean =
        subtasks.isNotEmpty() && subtasks.all { isResolved(it) }

    /**
     * El padre debe completarse automáticamente cuando no está completo y
     * todas sus subtareas directas quedaron resueltas (completadas o
     * canceladas).
     */
    fun shouldAutoCompleteParent(parent: TaskEntity, subtasks: List<TaskEntity>): Boolean =
        !parent.completed && allCompleted(subtasks)

    /**
     * El padre debe volver a abrirse cuando está completo pero alguna de sus
     * subtareas directas quedó pendiente (al desmarcar una subtarea). Una
     * subtarea cancelada NO cuenta como pendiente: descartarla no reabre el
     * padre.
     */
    fun shouldAutoReopenParent(parent: TaskEntity, subtasks: List<TaskEntity>): Boolean =
        parent.completed && subtasks.any { !isResolved(it) }

    /**
     * Número de ancestros de `task` (0 si es raíz). Tolera ciclos cortando
     * el recorrido para no entrar en bucle infinito.
     */
    fun depth(task: TaskEntity, tasksById: Map<Long, TaskEntity>): Int {
        var depth = 0
        var current: TaskEntity? = task
        val visited = mutableSetOf<Long>()
        while (current?.parentTaskId != null) {
            if (!visited.add(current.parentTaskId!!)) break
            current = tasksById[current.parentTaskId]
            if (current == null) break
            depth++
        }
        return depth
    }

    /** Permite añadir una subtarea solo si el padre no está en la profundidad máxima. */
    fun canAddSubtask(parent: TaskEntity, tasksById: Map<Long, TaskEntity>): Boolean =
        depth(parent, tasksById) < MAX_DEPTH

    /**
     * Copias frescas de [subtasks] para la próxima ocurrencia de un padre
     * recurrente, enlazadas a [newParentId].
     *
     * Sin esto, completar una tarea recurrente con un desglose (p. ej.
     * "Preparar reunión semanal" → "Agenda", "Materiales", "Minutas") pierde
     * todo el checklist en cada ciclo: la próxima ocurrencia nacía como padre
     * huérfano y el usuario debía recrear las subtareas o —peor— olvidaba
     * pasos de la rutina ("evitar olvidos", "datos sagrados").
     *
     * Campos preservados (la ESTRUCTURA del checklist): `title`, `details`,
     * `durationMinutes`, `priority`, `projectId`, `sortOrder`, `flagged`,
     * `archived`. Campos reiniciados para el ciclo nuevo:
     * - `id` = 0, `parentTaskId` = [newParentId], `createdAt`/`updatedAt` = [now];
     * - `completed` = false, `completedAt` = null, `status` = INBOX (abierta);
     * - `dueAt`/`reminderAt`/`startAt` = null: la planificación del ciclo viejo
     *   es obsoleta; la subtarea hereda el contexto del nuevo padre, igual que
     *   una subtarea recién creada (que nace sin fechas);
     * - `recurrence` = NONE: una subtarea recurrente propia generaría
     *   ocurrencias anidadas bajo cada ciclo del padre (explosión de tareas),
     *   así que se aplana.
     *
     * El cancelado de un ciclo (status CANCELLED) NO se propaga: ese paso
     * renace abierto porque el descarte fue de la ocurrencia anterior, no un
     * borrado permanente del checklist.
     *
     * Regla pura y determinista; el llamador persiste las copias con el
     * repositorio. Ver `OrdiaViewModel` (toggleTask / autocompletado de padre).
     */
    fun cloneForNextOccurrence(
        subtasks: List<TaskEntity>,
        newParentId: Long,
        now: Long,
    ): List<TaskEntity> = subtasks.map { sub ->
        sub.copy(
            id = 0L,
            parentTaskId = newParentId,
            status = TaskStatus.INBOX,
            completed = false,
            completedAt = null,
            startAt = null,
            dueAt = null,
            reminderAt = null,
            recurrence = RecurrenceFrequency.NONE,
            recurrenceInterval = 1,
            recurrenceDays = "",
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Copias frescas de [subtasks] para una tarea duplicada, enlazadas a
     * [newParentId]. A diferencia de [cloneForNextOccurrence] —que descarta la
     * planificación porque la del ciclo viejo es obsoleta— aquí se PRESERVA la
     * planificación y la recurrencia, igual que el duplicado del padre
     * (`OrdiaViewModel.duplicateTask`): duplicar es una copia literal, no una
     * nueva ocurrencia.
     *
     * Sin esto, "Duplicar" en una tarea con desglose creaba un padre "(copia)"
     * huérfano y silenciaba el checklist —inconsistencia con la recurrencia
     * (c.223) y pérdida de la estructura construida.
     *
     * Campos preservados (estructura + planificación): `title`, `details`,
     * `durationMinutes`, `priority`, `projectId`, `sortOrder`, `flagged`,
     * `archived`, `startAt`, `dueAt`, `reminderAt`, `recurrence`,
     * `recurrenceInterval`, `recurrenceDays`. Campos reiniciados:
     * - `id` = 0, `parentTaskId` = [newParentId], `createdAt`/`updatedAt` = [now];
     * - `completed` = false, `completedAt` = null, `status` = INBOX: el duplicado
     *   nace abierto; un paso cancelado en el original renace abierto (el
     *   descarte fue del original, no permanente).
     *
     * Regla pura y determinista; el llamador persiste las copias y agenda los
     * recordatorios. Ver `OrdiaViewModel.duplicateTask`.
     */
    fun cloneForDuplicate(
        subtasks: List<TaskEntity>,
        newParentId: Long,
        now: Long,
    ): List<TaskEntity> = subtasks.map { sub ->
        sub.copy(
            id = 0L,
            parentTaskId = newParentId,
            status = TaskStatus.INBOX,
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
