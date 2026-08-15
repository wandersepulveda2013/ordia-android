package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Resumen del estado del día y de la semana, calculado de forma
 * determinista a partir de las tareas y un instante inyectado.
 *
 * Es un resumen "asistente": combina lo completado, lo pendiente de hoy,
 * las atrasadas, la bandeja por revisar y el ritmo semanal en una sola
 * estructura que la interfaz puede mostrar en una tarjeta.
 */
data class DaySummary(
    val completedToday: Int,
    val remainingToday: Int,
    val remainingMinutesToday: Int,
    val overdue: Int,
    val inboxPending: Int,
    val completedThisWeek: Int,
    val weekDailyAverage: Float,
    /** Veredicto honesto sobre si el trabajo restante de hoy cabe en el día. */
    val dayLoad: DayLoad = DayLoad.LIGHT,
    /**
     * Cuando [dayLoad] es OVERLOADED, sugiere la tarea de hoy más posponible
     * (la de menor prioridad que NO esté vencida y que más capacidad libere
     * al moverla a mañana). Null en el resto de casos. Heurística honesta:
     * no mueve nada, solo nombra qué tarea es candidata a reprogramar.
     */
    val deferralSuggestion: DeferralSuggestion? = null
)

/**
 * Tarea candidata a mover a mañana cuando el día está saturado. Solo se
 * calcula para OVERLOADED; la interfaz la muestra como una sugerencia, no
 * como una acción automática (el usuario decide moverla).
 */
data class DeferralSuggestion(val taskId: Long, val title: String, val canDefer: Boolean)

/**
 * Veredicto del día: convierte varios conteos (minutos restantes vs minutos
 * que quedan de jornada) en UNA decisión accionable, en vez de obligar al
 * usuario a hacer la aritmética mental. Heurística honesta, sin random.
 *
 * - LIGHT: nada pendiente o el día está despejado.
 * - ON_TRACK: el trabajo restante cabe con holgura (≤ media jornada libre).
 * - FULL: cabe pero justo (≤ toda la jornada libre restante).
 * - OVERLOADED: no cabe en el tiempo que queda → hay que soltar/replanear.
 */
enum class DayLoad { LIGHT, ON_TRACK, FULL, OVERLOADED }

object SummaryEngine {

    /** Inicio de jornada por defecto (9:00), igual que DayPlanner. */
    private const val DEFAULT_DAY_START = 9 * 60
    /** Fin de jornada por defecto (18:00), igual que DayPlanner. */
    private const val DEFAULT_DAY_END = 18 * 60

    /**
     * Variante que toma directamente un [LearningProfile] aprendido (o null para
     * los valores por defecto). Facilita pasar el perfil del usuario sin
     * desempaquetarlo a mano en cada caller de la UI.
     */
    fun summarize(
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId,
        profile: LearningProfile?
    ): DaySummary = summarize(
        tasks,
        now,
        zone,
        dayStartMinute = profile?.dayStartMinute ?: DEFAULT_DAY_START,
        dayEndMinute = profile?.dayEndMinute ?: DEFAULT_DAY_END
    )

    /**
     * Calcula el resumen para el día de `now` y los últimos 7 días.
     *
     * Todos los conteos consideran solo tareas raíz (`parentTaskId == null`), igual
     * que What Now, el planificador y el guardián: las subtareas son anidadas y
     * contarlas además del padre infla los números de la tarjeta de resumen
     * (un padre con 3 subtareas vencidas mostraba overdue=4 en vez de 1).
     *
     * - completedToday: tareas raíz completadas cuyo `completedAt` cae hoy.
     * - remainingToday: tareas raíz activas con `dueAt` u `startAt` hoy.
     * - remainingMinutesToday: minutos de trabajo que faltan de las anteriores
     *   (descuenta lo ya hecho en una tarea en curso; ver [TaskRules.remainingPlanMinutes]).
     * - overdue: tareas raíz activas vencidas según `TaskRules.isOverdue`.
     * - inboxPending: tareas en estado INBOX sin archivar (por revisar).
     * - completedThisWeek: completadas entre hoy-6 y hoy (inclusive).
     * - weekDailyAverage: completedThisWeek / 7.
     *
     * [dayStartMinute]/[dayEndMinute] definen la ventana de jornada usada por el
     * veredicto [DayLoad] (minutos libres vs. minutos restantes). Por defecto
     * 9:00–18:00; el llamador puede pasar el perfil aprendido
     * ([LearningProfile]) para que el veredicto refleje los horarios REALES del
     * usuario en lugar de una jornada fija —si un usuario trabaja 6–23, el día
     * no debe decir "OVERLOADED" a las 17:00 cuando aún le quedan 6 h de
     * capacidad, ni uno de 10–14 debe aparecer siempre "ON_TRACK". Coincide con
     * la ventana que `DayPlanner` usa para agendar, evitando que plan y veredicto
     * discrepen. Fuente única de verdad: los mismos límites que el planificador.
     */
    fun summarize(
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        dayStartMinute: Int = DEFAULT_DAY_START,
        dayEndMinute: Int = DEFAULT_DAY_END
    ): DaySummary {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstOfWeek = today.minusDays(6)

        val active = { task: TaskEntity -> TaskRules.isActive(task) }
        val completedToday = TaskRules.completedTodayCount(tasks, now, zone)
        val remainingTodayTasks = tasks.filter { task ->
            task.parentTaskId == null &&
                active(task) && (onDate(task.dueAt, today, zone) || onDate(task.startAt, today, zone))
        }
        val remainingToday = remainingTodayTasks.size
        val remainingMinutesToday = remainingTodayTasks.sumOf { TaskRules.remainingPlanMinutes(it, now) }
        val overdueTasks = tasks.filter { task ->
            task.parentTaskId == null && active(task) && TaskRules.isOverdue(task, now)
        }
        val overdue = overdueTasks.size
        val inboxPending = tasks.count { task ->
            task.parentTaskId == null && task.status == TaskStatus.INBOX && !task.archived
        }
        val completedThisWeek = tasks.count { task ->
            task.parentTaskId == null &&
                task.completed && task.completedAt?.let {
                    val date = DateRules.toLocalDate(it, zone)
                    !date.isBefore(firstOfWeek) && !date.isAfter(today)
                } == true
        }
        val weekDailyAverage = completedThisWeek / 7f

        // El veredicto de carga compara el trabajo REAL que compite por el tiempo
        // que queda de jornada: el de hoy MÁS las vencidas MÁS los olvidos
        // silenciosos (missed-start). Una tarea vencida sigue sin hacerse y
        // consume la misma jornada finita; un compromiso olvidado cuyo hueco
        // (startAt) ya pasó pero que aún no vence (dueAt futuro) es lo mismo: el
        // planificador (DayPlanner, c.246) ya lo recupera en el plan de hoy, así
        // que si el veredicto lo ignorara diría "ON_TRACK" mientras el plan
        // agendaba el trabajo olvidado — dos verdades divergentes. Se toma la
        // unión de hoy + vencidas + olvidos (por id) para no contar dos veces:
        // un olvidado tiene dueAt futuro → no está en remainingToday (que pide
        // dueAt/startAt hoy) ni en overdue (que pide isOverdue), y un vencido no
        // es olvidado (isMissedStart excluye isOverdue), así que los tres
        // conjuntos son disjuntos. Cada tarea aporta solo lo que falta
        // ([TaskRules.remainingPlanMinutes]): una en curso ya no suma su duración
        // completa, solo el tiempo restante, igual que `freeMinutes` solo cuenta
        // desde ahora (no doble-contar lo ya gastado). El olvido NO suma en
        // `remainingToday`/`remainingMinutesToday`: esas métricas son "lo de hoy"
        // (la badge "te quedan N min hoy" refleja solo hoy), igual que las
        // vencidas no inflan dicha badge. Solo el veredicto de carga considera el
        // trabajo olvidado, porque es trabajo que el plan ya ubicó hoy.
        val missedStartTasks = tasks.filter { task ->
            task.parentTaskId == null && active(task) && TaskRules.isMissedStart(task, now)
        }
        val loadMinutes = (remainingTodayTasks + overdueTasks + missedStartTasks)
            .distinctBy { it.id }
            .sumOf { TaskRules.remainingPlanMinutes(it, now) }
        val dayLoad = assessDayLoad(loadMinutes, now, zone, dayStartMinute, dayEndMinute)
        val deferralSuggestion = if (dayLoad == DayLoad.OVERLOADED) {
            // Cuando el olvido satura el día, la sugerencia nombra una tarea de
            // hoy posponible (no vencida, no olvidada): mostDeferrableTask ya
            // excluye missed-start ("posponer un olvido lo agrava"), igual que
            // excluye vencidas. El culpable (el olvido) no se aplaza; se hace.
            mostDeferrableTask(remainingTodayTasks, now)
        } else null

        return DaySummary(
            completedToday = completedToday,
            remainingToday = remainingToday,
            remainingMinutesToday = remainingMinutesToday,
            overdue = overdue,
            inboxPending = inboxPending,
            completedThisWeek = completedThisWeek,
            weekDailyAverage = weekDailyAverage,
            dayLoad = dayLoad,
            deferralSuggestion = deferralSuggestion
        )
    }

    /**
     * Veredicto honesto: ¿cabe el trabajo restante de hoy en el tiempo que
     * queda de la jornada? Usa la misma ventana de jornada que `DayPlanner`
     * (9:00–18:00 por defecto, o el perfil aprendido si el llamador lo pasa),
     * de forma que "minutos restantes" y "minutos libres" hablan el mismo
     * idioma. Si ya pasó el fin de jornada no hay capacidad libre (0 min):
     * cualquier trabajo restante queda OVERLOADED.
     *
     * Sin trabajo que cargar → LIGHT. Si no, compara los minutos planificados
     * a cargar con los minutos libres hasta el fin de jornada:
     *   ≤ mitad de la jornada libre → ON_TRACK (margen holgado);
     *   ≤ jornada libre entera      → FULL (cabe pero justo);
     *   >  → OVERLOADED (no cabe; hay que soltar/replanear).
     *
     * [loadMinutes] suma el trabajo de hoy MÁS las vencidas (unión por id en
     * [summarize]): el tiempo libre de la jornada es finito y compartido, así
     * que el trabajo vencido sin hacer compite con el de hoy por esos minutos.
     * Antes solo se comparaba "minutos de hoy", lo que ocultaba la saturación
     * real cuando había vencidas pendientes.
     */
    private fun assessDayLoad(
        loadMinutes: Int,
        now: Long,
        zone: ZoneId,
        dayStartMinute: Int,
        dayEndMinute: Int
    ): DayLoad {
        if (loadMinutes <= 0) return DayLoad.LIGHT

        val zonedNow = Instant.ofEpochMilli(now).atZone(zone)
        val nowMinute = zonedNow.hour * 60 + zonedNow.minute
        val freeMinutes = (dayEndMinute - maxOf(nowMinute, dayStartMinute)).coerceAtLeast(0)
        if (freeMinutes <= 0) return DayLoad.OVERLOADED

        return when {
            loadMinutes <= freeMinutes / 2 -> DayLoad.ON_TRACK
            loadMinutes <= freeMinutes -> DayLoad.FULL
            else -> DayLoad.OVERLOADED
        }
    }

    /**
     * Tarea candidata a mover a mañana cuando el día está saturado. Heurística
     * honesta y conservadora: nunca sugiere (1) una tarea vencida —ya llegaron
     * tarde y posponerlas empeora el retraso— ni (2) una tarea ocurriendo ahora
     * mismo o a punto de empezar (compromiso inminente) —posponer una reunión
     * que arranca en 5 min es un consejo dañino, no ayuda— ni (3) una tarea sin
     * `dueAt`: la acción "mover a mañana" ([TaskRules.deferToNextDay]) no puede
     * ejecutarla (devuelve null sin vencimiento), así que nombrarla entregaría
     * un consejo no accionable (texto pasivo, sin tap) cuando sí existe otra de
     * hoy posponible— ni (4) un compromiso agendado cuyo hueco ya pasó
     * ([TaskRules.isMissedStart]): el usuario le dio hora y se le olvidó;
     * posponerlo a mañana es RE-OLVIDAR el compromiso que el guardián (c.201),
     * What Now (c.203) y el asistente "¿qué olvidé?" (c.206) se esfuerzan en
     * RECUPERAR —mismo principio que la exclusión de vencidas: posponer un
     * olvido no lo resuelve, lo agrava. Entre las de hoy posponibles, ordena
     * por: menor prioridad (LOW antes que NORMAL antes que HIGH antes que
     * URGENT) → la que MÁS capacidad libera (`plannedDuration` mayor) → a igual
     * capacidad, la que vence más tarde hoy (más margen → más segura de aplazar
     * sin riesgo inminente). El criterio de capacidad es central: el propósito
     * de posponer bajo OVERLOADED es que el día quepa, y posponer una tarea de
     * 10 min cuando hay una de 120 min de la misma prioridad deja el día
     * saturado (consejo inútil); posponer la grande recupera el tiempo que de
     * verdad resuelve la saturación. No muta nada.
     */
    private fun mostDeferrableTask(
        remainingTodayTasks: List<TaskEntity>,
        now: Long
    ): DeferralSuggestion? {
        // Exige `dueAt`: [TaskRules.deferToNextDay] no puede mover una tarea sin
        // vencimiento, así que nombrarla daría un consejo no accionable.
        val deferrable = remainingTodayTasks.filter { task ->
            task.dueAt != null &&
                !TaskRules.isOverdue(task, now) &&
                !TaskRules.isBeingWorkedOn(task, now) &&
                !TaskRules.isImminentStart(task, now) &&
                !TaskRules.isMissedStart(task, now)
        }
        if (deferrable.isEmpty()) return null
        val chosen = deferrable.maxWithOrNull(
            compareBy<TaskEntity> { priorityDeferralWeight(it.priority) }
                .thenBy { TaskRules.plannedDuration(it) }
                .thenBy { it.dueAt ?: it.startAt ?: 0L }
        ) ?: return null
        return DeferralSuggestion(taskId = chosen.id, title = chosen.title, canDefer = chosen.dueAt != null)
    }

    /** Mayor peso = más posponible (menos urgente). LOW=NORMAL/HIGH/URGENT inverso. */
    private fun priorityDeferralWeight(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 3
        TaskPriority.NORMAL -> 2
        TaskPriority.HIGH -> 1
        TaskPriority.URGENT -> 0
    }


    private fun onDate(epochMillis: Long?, date: LocalDate, zone: ZoneId): Boolean =
        epochMillis?.let { DateRules.toLocalDate(it, zone) == date } ?: false
}
