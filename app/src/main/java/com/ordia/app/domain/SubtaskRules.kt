package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity

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

    /** Progreso (completadas, total) de las subtareas directas. */
    fun progress(subtasks: List<TaskEntity>): Pair<Int, Int> =
        subtasks.count { it.completed } to subtasks.size

    fun allCompleted(subtasks: List<TaskEntity>): Boolean =
        subtasks.isNotEmpty() && subtasks.all { it.completed }

    /**
     * El padre debe completarse automáticamente cuando no está completo y
     * todas sus subtareas directas quedaron completadas.
     */
    fun shouldAutoCompleteParent(parent: TaskEntity, subtasks: List<TaskEntity>): Boolean =
        !parent.completed && allCompleted(subtasks)

    /**
     * El padre debe volver a abrirse cuando está completo pero alguna de sus
     * subtareas directas quedó pendiente (al desmarcar una subtarea).
     */
    fun shouldAutoReopenParent(parent: TaskEntity, subtasks: List<TaskEntity>): Boolean =
        parent.completed && subtasks.any { !it.completed }

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
}
