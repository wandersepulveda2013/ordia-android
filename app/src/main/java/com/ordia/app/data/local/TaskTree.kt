package com.ordia.app.data.local

/**
 * Recorrido del subárbol de tareas (ORD-025).
 *
 * `TaskEntity.parentTaskId` no tiene self-ForeignKey (el esquema de Room no está
 * versionado, por lo que añadir una FK exigiría migración y exportar `app/schemas/`).
 * Para no dejar subtareas huérfanas al borrar una tarea padre, el subárbol completo
 * se recoge de forma explícita y se elimina en la misma transacción.
 *
 * La recolección es BFS, tolerante a ciclos (por si hubiera datos corruptos) y no
 * depende de SQLite, por lo que es testeable en JVM puro.
 */
object TaskTree {

    /**
     * Devuelve [rootId] más todos sus descendientes transitivos (BFS), sin repetidos.
     *
     * @param childrenOf provee los hijos directos de una tarea; puede ser suspend
     *   (p.ej. un método del DAO).
     */
    suspend fun collectIds(rootId: Long, childrenOf: suspend (Long) -> List<Long>): List<Long> {
        val result = mutableListOf(rootId)
        val seen = mutableSetOf(rootId)
        var frontier = childrenOf(rootId)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<Long>()
            for (id in frontier) {
                if (seen.add(id)) {
                    result += id
                    next += childrenOf(id)
                }
            }
            frontier = next
        }
        return result
    }
}
