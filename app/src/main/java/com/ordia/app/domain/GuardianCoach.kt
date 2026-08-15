package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object GuardianCoach {
    enum class Tone { CALM, FOCUSED, CELEBRATING, GENTLE }
    data class Insight(val eyebrow: String, val title: String, val message: String, val taskId: Long? = null, val tone: Tone = Tone.CALM)

    fun insight(tasks: List<TaskEntity>, habits: List<HabitEntity>, habitLogs: List<HabitLogEntity>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Insight {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val roots = tasks.filter { it.parentTaskId == null && !it.archived && it.status != TaskStatus.CANCELLED }
        val pending = roots.filterNot { it.completed }
        val overdue = pending.filter { TaskRules.isOverdue(it, now) }
        // Tareas atrasadas que el usuario NO está ejecutando ahora mismo: las
        // únicas sobre las que tiene sentido un nudge de "recuperar/comenzar".
        // Una atrasada pero en curso ya está atendida; nombrarla como "comienza
        // por esta" es incoherente y distrae. Se usa el predicado canónico
        // [TaskRules.isBeingWorkedOn] (status IN_PROGRESS o hueco activo),
        // mismo "sacro en curso" que GuardianEngine.smallestOverdueAction,
        // SummaryEngine.mostDeferrableTask y AutomationActionPlanner (c.280):
        // el predicado parcial isInProgressNow solo ve la ventana de tiempo y
        // NO una atrasada marcada IN_PROGRESS a mano sin ventana activa (p. ej.
        // hueco ya pasado), así que la incluía y nudgiaba "empieza por esta"
        // sobre lo que el usuario ya hace. Si TODAS las atrasadas están en
        // curso, no se nudgea a "recuperar el control": el usuario ya lo hace,
        // así se deja caer al siguiente insight.
        val recoverable = overdue.filter { !TaskRules.isBeingWorkedOn(it, now) }
        val dueToday = pending.filter { TaskRules.isDueToday(it, now, zone) && !TaskRules.isOverdue(it, now) }
        val completedToday = TaskRules.completedTodayCount(tasks, now, zone)
        if (recoverable.isNotEmpty()) {
            val next = TaskRules.nextBestTask(recoverable, now)
            // "Olvidada": la más atrasada lleva tanto tiempo esperando que el
            // problema no es priorizarla, sino decidir qué hacer con ella. El
            // coach deja de repetir "empieza por esta" y plantea la decisión
            // real: hacerla hoy, moverla a propósito o quitarla de la lista.
            // Heurística honesta: cuenta días de CALENDARIO entre la fecha de
            // vencimiento (en la zona del usuario) y hoy, no millis/24h. Así la
            // edad y el umbral de "olvidada" son correctos aunque el momento de
            // consulta sea anterior a la hora del vencimiento (p. ej. una tarea
            // vencida hace 2 días vista a las 7 a.m. sigue contando como 2, no
            // como 1) y son robustos frente a los cambios de horario (DST).
            val mostOverdueDays = recoverable.maxOf { task -> overdueDays(task.dueAt, today, zone) }
            if (mostOverdueDays >= FORGOTTEN_DAYS_THRESHOLD) {
                val ageLabel = forgottenAgeLabel(mostOverdueDays)
                val message = if (recoverable.size == 1)
                    "Esta tarea lleva $ageLabel atrasada. Hazla hoy o muévela con intención, no la dejes pasar otra vez."
                else
                    "Tienes ${recoverable.size} tareas atrasadas y la más antigua lleva $ageLabel. Elige una: hacerla hoy, reprogramarla o quitarla."
                return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay algo pendiente", message, next?.id, Tone.FOCUSED)
            }
            return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay algo pendiente", if (recoverable.size == 1) "Esta tarea está atrasada. Empieza con un bloque corto." else "Tienes ${recoverable.size} tareas atrasadas. Comienza por esta.", next?.id, Tone.GENTLE)
        }
        val urgent = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgent.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgent, now)
            return Insight("PROTEGE TU DÍA", next?.title ?: "Prioridad de hoy", "Reserva tiempo para lo más importante antes de llenar la agenda.", next?.id, Tone.FOCUSED)
        }
        // Recuperación del "olvido silencioso" (isMissedStart, c.201): un
        // compromiso al que el usuario le dio hueco (`startAt`) y cuyo turno ya
        // pasó sin atraso de plazo. Es el tercer olvido honesto de Ordía, junto
        // a las vencidas ([TaskRules.isOverdue]) y las capturas arrinconadas
        // ([TaskRules.isStaleInbox]); antes esta superficie —la más visible de
        // recuperación— solo reencuadraba los dos primeros como "RECUPERA EL
        // CONTROL", y un compromiso olvidado caía a un "SIGUIENTE PASO"
        // genérico pese a que el asistente ("¿qué olvidé?") y el nudge del
        // guardián ([com.ordia.app.domain.GuardianEngine.missedStartAction]) sí
        // lo recuperaban. Simétrico a las vencidas: tono suave si el hueco pasó
        // hoy/mismo día, FOCUSED ("olvidada") cuando lleva
        // [FORGOTTEN_DAYS_THRESHOLD] o más días de calendario sin atenderse —el
        // compromiso se agendó a propósito, así que olvidarlo es señal tan
        // fuerte como un plazo incumplido—. Va tras la rama de prioridad de hoy
        // (una deadline de hoy URGENT/ALTA es más crítica que un compromiso cuyo
        // plazo aún no venció) y antes de la bandeja arrinconada: agendar un
        // compromiso es una decisión más fuerte que una captura sin fecha, así
        // que su recuperación prevalece (coherente con [TaskRules.nextBestTask],
        // que ya ordena isMissedStart por encima de isStaleInbox). nextBestTask
        // sobre los olvidados asegura no robar el lugar a algo más urgente dentro
        // de la propia lista. Sin nueva pantalla: reencuadra lo que ya existe.
        val missedStart = pending.filter { TaskRules.isMissedStart(it, now) }
        if (missedStart.isNotEmpty()) {
            val next = TaskRules.nextBestTask(missedStart, now)
            val mostMissedDays = missedStart.maxOf { missedStartDays(it.startAt, today, zone) }
            if (mostMissedDays >= FORGOTTEN_DAYS_THRESHOLD) {
                val ageLabel = forgottenAgeLabel(mostMissedDays)
                val message = if (missedStart.size == 1)
                    "Esta tarea tenía su hueco y se pasó hace $ageLabel. Hazla hoy o reagéndala: no la dejes pasar otra vez."
                else
                    "Tienes ${missedStart.size} compromisos cuyo hueco pasó y el más antiguo lleva $ageLabel. Elige uno: hacerlo hoy, reagendarlo o quitarlo."
                return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay un compromiso olvidado", message, next?.id, Tone.FOCUSED)
            }
            return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay un compromiso pendiente", if (missedStart.size == 1) "Esta tarea tenía su hueco y se pasó. Empieza con un bloque corto o reagéndala." else "Tienes ${missedStart.size} compromisos cuyo hueco pasó. Comienza por este.", next?.id, Tone.GENTLE)
        }
        val next = TaskRules.nextBestTask(pending, now)
        // Rescate de tareas "olvidadas" SIN fecha: la recuperación solo miraba
        // las vencidas (con dueAt), pero una idea capturada en la bandeja y
        // nunca agendada también se olvida. Si la mejor tarea candidata es
        // ella misma una captura sin fecha que lleva muchos días esperando
        // (umbral de calendario, no millis/24h), replantea la decisión real:
        // hacerla hoy, agendarla o quitarla. Delegar en nextBestTask asegura
        // que el rescate NUNCA robe el lugar a algo más time-sensitive (vence
        // hoy, urgente sin fecha…): solo se reencuadra cuando lo elegido es la
        // captura olvidada. Reusa la etiqueta de edad de las vencidas; sin
        // nueva pantalla ni botón.
        if (next != null && TaskRules.isStaleInbox(next, now, zone)) {
            val staleInbox = pending.filter { TaskRules.isStaleInbox(it, now, zone) }
            val maxAge = staleInbox.maxOf { TaskRules.inboxAgeDays(it, now, zone) }
            val ageLabel = forgottenAgeLabel(maxAge)
            val message = if (staleInbox.size == 1)
                "Esta tarea lleva $ageLabel en tu bandeja sin fecha. Hazla hoy, agéndala o quítala: no la dejes pasar otra vez."
            else
                "Tienes ${staleInbox.size} tareas sin fecha y la más antigua lleva $ageLabel. Elige una: hacerla hoy, agendarla o quitarla."
            return Insight("RECUPERA EL CONTROL", next.title, message, next.id, Tone.FOCUSED)
        }
        next?.let { return Insight("SIGUIENTE PASO", it.title, it.details.takeIf(String::isNotBlank) ?: "Ordía la priorizó por fecha, importancia y estado.", it.id, Tone.FOCUSED) }
        habits.firstOrNull { HabitRules.isScheduled(it, today) && HabitRules.countFor(habitLogs, it.id, today) < it.targetPerPeriod }?.let {
            return Insight("UN PEQUEÑO RITUAL", it.title, "Tu lista está despejada. Este hábito puede cerrar el día con intención.", tone = Tone.CALM)
        }
        if (completedToday > 0) return Insight("BIEN HECHO", "Tu día está en orden", "Completaste $completedToday ${if (completedToday == 1) "tarea" else "tareas"} hoy.", tone = Tone.CELEBRATING)
        return Insight("TODO EN CALMA", "No hay pendientes inmediatos", "Captura una idea, revisa un proyecto o conserva este espacio libre.", tone = Tone.CALM)
    }

    /**
     * Etiqueta legible de la antigüedad de una tarea olvidada. Fuente única de
     * verdad en [DateRules.ageLabel] (compartida con el asistente); aquí sólo
     * delegamos para mantener las dos superficies de recuperación sincronizadas.
     */
    private fun forgottenAgeLabel(daysOverdue: Int): String = DateRules.ageLabel(daysOverdue)

    private const val FORGOTTEN_DAYS_THRESHOLD = 2

    /**
     * Días de vencimiento transcurridos en términos de calendario (no de
     * milisegundos): cuenta los días completos entre la fecha local de
     * [dueAt] y [today], ambas en la zona del usuario. Devuelve 0 si no hay
     * fecha. Esto evita el error de 1 día que produce `(now - dueAt)/24h`
     * cuando se consulta antes de la hora del vencimiento, y es correcto en
     * zonas con horario de verano (DST), donde un "día" no siempre son 24 h.
     *
     * El umbral y la edad de las capturas de bandeja SIN fecha viven ahora en
     * [TaskRules.isStaleInbox]/[TaskRules.inboxAgeDays] (fuente única de verdad,
     * compartida con el asistente); aquí sólo queda la edad del plazo incumplido.
     */
    private fun overdueDays(dueAt: Long?, today: java.time.LocalDate, zone: ZoneId): Int =
        dueAt?.let { ChronoUnit.DAYS.between(DateRules.toLocalDate(it, zone), today).toInt() } ?: 0

    /**
     * Días de calendario que un compromiso agendado lleva con el hueco pasado:
     * cuenta los días completos entre la fecha local de [startAt] y [today],
     * ambas en la zona del usuario. Simétrico a [overdueDays] (que mide el plazo
     * incumplido): aquí se mide el hueco incumplido (el "olvido silencioso" de
     * [TaskRules.isMissedStart]). Devuelve 0 si la tarea no tiene `startAt` o el
     * hueco pasó hoy (mismo día): aún no es "olvidada", solo tarde. Cuenta
     * calendario, no millis/24h, para ser correcta sin importar la hora de
     * consulta y robusta frente a DST —mismo motivo que [overdueDays].
     */
    private fun missedStartDays(startAt: Long?, today: java.time.LocalDate, zone: ZoneId): Int =
        startAt?.let { ChronoUnit.DAYS.between(DateRules.toLocalDate(it, zone), today).toInt().coerceAtLeast(0) } ?: 0
}
