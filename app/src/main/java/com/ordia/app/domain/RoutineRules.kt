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

    /**
     * `true` si [task] fue generada por la rutina [routineName]. Reconoce por
     * PREFIJO del marcador canónico `Rutina: <name>` al inicio de `details`,
     * seguido del fin del marcador: el texto RESTA, vacío o una anotación que
     * el usuario añadió tras el marcador.
     *
     * Una igualdad exacta (`details == "Rutina: Gym"`) rompía en cuanto el
     * usuario ANOTABA el paso ("Rutina: Gym\nllevar toalla", "Rutina: Gym
     * (llevar toalla)"): las tareas generadas por rutina son tareas normales
     * editables. Perdido el reconocimiento, [wasRunToday] devolvía `false` y un
     * re-disparo (reabrir la app, worker, acción manual) DUPLICABA los pasos en
     * la bandeja — justo lo opuesto a "evitar olvidos" y "rutinas adaptables".
     *
     * El límite tras el nombre (fin de cadena o separador) evita que "Gym"
     * reconozca una tarea de "Gym avanzado": el marcador debe corresponder al
     * nombre EXACTO de la rutina. Y el marcador debe ir al INICIO de `details`
     * para no captar menciones a mitad del texto ("ya hice la Rutina: Gym
     * ayer") que no son tareas generadas por ella.
     */
    fun isCreatedByRoutine(task: TaskEntity, routineName: String): Boolean =
        isRoutineDetail(task.details, routineName)

    fun tasksFromRoutine(tasks: List<TaskEntity>, routineName: String): List<TaskEntity> =
        tasks.filter { isCreatedByRoutine(it, routineName) }

    /**
     * Reconocimiento de prefijo con límite: `details` arranca con el marcador
     * `Rutina: <name>` y, si hay más texto, éste comienza con un separador
     * (salto de línea, tab, espacio + paréntesis/guion) o es exactamente el
     * marcador. Un nombre que sea prefijo de otro ("Gym" vs "Gym avanzado") no
     * casa porque tras "Gym" viene " avanzado" (espacio + letra, no un
     * separador de anotación). Detalle puro sin marcador ("Otra cosa") tampoco.
     */
    private fun isRoutineDetail(details: String, routineName: String): Boolean {
        val marker = routineDetail(routineName)
        if (details == marker) return true
        if (!details.startsWith(marker)) return false
        // Tras el marcador sólo se admite una anotación del usuario: salto de
        // línea/tab, o espacio seguido de un signo de apertura/nota ("(...)" o
        // "- ..."). Así "Rutina: Gym avanzado" (espacio + letra) no casa para
        // "Gym", pero "Rutina: Gym\nllevar toalla" y "Rutina: Gym (llevar
        // toalla)" sí.
        val rest = details.substring(marker.length)
        return rest.startsWith("\n") || rest.startsWith("\t") ||
            rest.startsWith(" (") || rest.startsWith(" -")
    }

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
        return tasks
            .filter { task ->
                isRoutineDetail(task.details, oldName) &&
                    !task.archived && task.status != TaskStatus.CANCELLED &&
                    Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate() == today
            }
            .map { task -> RoutineTaskRelink(task.id, routineDetail(newName)) }
    }
}
