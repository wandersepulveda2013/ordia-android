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
        val dueToday = pending.filter { TaskRules.isDueToday(it, now, zone) && !TaskRules.isOverdue(it, now) }
        val completedToday = roots.count { it.completed && it.completedAt?.let { time -> java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == today } == true }
        if (overdue.isNotEmpty()) {
            val next = TaskRules.nextBestTask(overdue, now)
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
            val mostOverdueDays = overdue.maxOf { task -> overdueDays(task.dueAt, today, zone) }
            if (mostOverdueDays >= FORGOTTEN_DAYS_THRESHOLD) {
                val ageLabel = forgottenAgeLabel(mostOverdueDays)
                val message = if (overdue.size == 1)
                    "Esta tarea lleva $ageLabel atrasada. Hazla hoy o muévela con intención, no la dejes pasar otra vez."
                else
                    "Tienes ${overdue.size} tareas atrasadas y la más antigua lleva $ageLabel. Elige una: hacerla hoy, reprogramarla o quitarla."
                return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay algo pendiente", message, next?.id, Tone.FOCUSED)
            }
            return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay algo pendiente", if (overdue.size == 1) "Esta tarea está atrasada. Empieza con un bloque corto." else "Tienes ${overdue.size} tareas atrasadas. Comienza por esta.", next?.id, Tone.GENTLE)
        }
        val urgent = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgent.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgent, now)
            return Insight("PROTEGE TU DÍA", next?.title ?: "Prioridad de hoy", "Reserva tiempo para lo más importante antes de llenar la agenda.", next?.id, Tone.FOCUSED)
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
        if (next != null && next.dueAt == null && next.startAt == null &&
            inboxAgeDays(next.createdAt, today, zone) >= STALE_INBOX_DAYS_THRESHOLD) {
            val staleInbox = pending.filter { it.dueAt == null && it.startAt == null && inboxAgeDays(it.createdAt, today, zone) >= STALE_INBOX_DAYS_THRESHOLD }
            val maxAge = staleInbox.maxOf { inboxAgeDays(it.createdAt, today, zone) }
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
     * Etiqueta legible de la antigüedad de una tarea olvidada: "1 día", "2 días",
     * "1 semana" (7 días), "2 semanas" (14)… Acota a días/semanas para evitar
     * "30 días" cuando "4 semanas" comunica mejor cuánto se pospuso.
     */
    private fun forgottenAgeLabel(daysOverdue: Int): String {
        val days = daysOverdue.coerceAtLeast(1)
        if (days < 7) return "$days ${if (days == 1) "día" else "días"}"
        val weeks = days / 7
        if (weeks < 5) return "$weeks ${if (weeks == 1) "semana" else "semanas"}"
        val months = days / 30
        return "$months ${if (months == 1) "mes" else "meses"}"
    }

    private const val FORGOTTEN_DAYS_THRESHOLD = 2

    /**
     * Umbral de "olvidada" para una tarea de la bandeja SIN fecha: como no
     * incumple ningún vencimiento, le damos más margen que a una vencida
     * ([FORGOTTEN_DAYS_THRESHOLD]). Una semana esperando sin agendar es la
     * señal honesta de que la captura quedó arrinconada.
     */
    private const val STALE_INBOX_DAYS_THRESHOLD = 7

    /**
     * Días de calendario que una tarea de la bandeja lleva esperando desde su
     * creación (en la zona del usuario), para decidir si está "olvidada".
     * Cuenta días completos, no millis/24h, igual que [overdueDays]: así es
     * correcta aunque se consulte a primera hora y es robusta frente al DST.
     */
    private fun inboxAgeDays(createdAt: Long, today: java.time.LocalDate, zone: ZoneId): Int =
        ChronoUnit.DAYS.between(DateRules.toLocalDate(createdAt, zone), today).toInt()

    /**
     * Días de vencimiento transcurridos en términos de calendario (no de
     * milisegundos): cuenta los días completos entre la fecha local de
     * [dueAt] y [today], ambas en la zona del usuario. Devuelve 0 si no hay
     * fecha. Esto evita el error de 1 día que produce `(now - dueAt)/24h`
     * cuando se consulta antes de la hora del vencimiento, y es correcto en
     * zonas con horario de verano (DST), donde un "día" no siempre son 24 h.
     */
    private fun overdueDays(dueAt: Long?, today: java.time.LocalDate, zone: ZoneId): Int =
        dueAt?.let { ChronoUnit.DAYS.between(DateRules.toLocalDate(it, zone), today).toInt() } ?: 0
}
