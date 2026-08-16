package com.ordia.app.automation

/**
 * Reglas puras para reconstruir el alcance de un deshacer.
 *
 * Los registros históricos guardan en [affectedTaskIds] tanto las tareas que
 * fueron modificadas como las que fueron creadas. Una tarea es realmente nueva
 * solo cuando no existe una instantánea previa en [snapshotTaskIds].
 */
object AutomationUndoRules {
    fun createdTaskIds(
        affectedTaskIds: Collection<Long>,
        snapshotTaskIds: Collection<Long>
    ): Set<Long> = affectedTaskIds.toSet() - snapshotTaskIds.toSet()
}
