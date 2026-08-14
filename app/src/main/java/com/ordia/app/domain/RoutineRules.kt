package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reglas de adaptación de rutinas: permite saber si una rutina ya se añadió
 * hoy a la bandeja (para no duplicarla al volver a ejecutarla) y agrupa las
 * tareas que Ordia creó a partir de ella.
 *
 * Las tareas generadas por una rutina se reconocen por su campo `details`
 * con el prefijo "Rutina: <nombre>".
 */
object RoutineRules {

    fun routineDetail(routineName: String): String = "Rutina: $routineName"

    fun isCreatedByRoutine(task: TaskEntity, routineName: String): Boolean =
        task.details == routineDetail(routineName)

    fun tasksFromRoutine(tasks: List<TaskEntity>, routineName: String): List<TaskEntity> =
        tasks.filter { isCreatedByRoutine(it, routineName) }

    /**
     * Devuelve true si la rutina ya se ejecutó hoy: existe al menos una tarea
     * generada por ella (mismo `details` "Rutina: <nombre>") creada hoy y que no
     * haya sido archivada ni cancelada.
     *
     * Una tarea completada también cuenta: significa que la rutina se ejecutó
     * hoy y el usuario ya avanzó esa tanda, por lo que un nuevo disparo
     * (reabrir la app, worker o acción manual) NO debe volver a añadirla, ya
     * que duplicaría tareas en la bandeja. Antes se exigía que la tarea
     * estuviera pendiente, lo que provocaba duplicados justo cuando el usuario
     * había sido productivo y completado la rutina del día.
     */
    fun wasRunToday(
        tasks: List<TaskEntity>,
        routineName: String,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = tasksFromRoutine(tasks, routineName).any { task ->
        !task.archived && task.status != TaskStatus.CANCELLED &&
            Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate() == today
    }

    /**
     * Tarea de hoy generada por una rutina que, tras renombrarla, hay que
     * reetiquetar con el nuevo nombre para que [wasRunToday] siga reconociéndola.
     */
    data class RoutineTaskRelink(val taskId: Long, val newDetails: String)

    /**
     * Tras renombrar una rutina, las tareas que generó hoy todavía llevan el
     * nombre anterior en `details` ("Rutina: <viejo>"), así que [wasRunToday] no
     * las reconocería y la rutina se dispararía de nuevo al reabrir la app o
     * volver a ejecutarla: **tareas duplicadas en la bandeja** justo cuando el
     * usuario ya avanzó esa tanda.
     *
     * Devuelve las tareas de hoy (activas, no archivadas ni canceladas) que
     * llevan `details == "Rutina: <oldName>"` para reetiquetarlas con
     * `newName`. No muta: el llamador aplica los cambios. El ámbito es el mismo
     * de [wasRunToday] (solo hoy, y solo las tareas que esta considera): reescribir
     * histórico no aporta nada al dedup y puede sorprender al usuario. Si el
     * nombre no cambió ([oldName] == [newName]) no hay nada que reetiquetar.
     */
    fun relinkAfterRename(
        tasks: List<TaskEntity>,
        oldName: String,
        newName: String,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<RoutineTaskRelink> {
        if (oldName == newName) return emptyList()
        val oldDetail = routineDetail(oldName)
        return tasks
            .filter { task ->
                task.details == oldDetail &&
                    !task.archived && task.status != TaskStatus.CANCELLED &&
                    Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate() == today
            }
            .map { task -> RoutineTaskRelink(task.id, routineDetail(newName)) }
    }
}
