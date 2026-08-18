package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Aprendizaje local (opt-in): perfila los horarios en los que el usuario
 * realmente completa tareas, para que el planificador use esos horarios en
 * lugar de los fijos.
 *
 * Todo se calcula en el dispositivo a partir de las propias tareas completadas;
 * no se envía ningún dato fuera. Sin aprendizaje, los valores coinciden con
 * los predeterminados del planificador.
 */
data class LearningProfile(
    val dayStartMinute: Int = 9 * 60,
    val dayEndMinute: Int = 18 * 60
)

object LearningEngine {

    /** Ventana de observación: últimas 4 semanas de tareas completadas. */
    const val WINDOW_DAYS = 28L
    private const val DEFAULT_START = 9 * 60
    private const val DEFAULT_END = 18 * 60

    /**
     * Mínimo de muestras para apartarse del default. Con menos observaciones los
     * percentiles son ruido estadístico: una sola noche atípica (p. ej. entregar
     * un trabajo a las 23:00) bastaba para empujar el p90 a la madrugada y
     * estirar la ventana del planificador, relajando el veredicto de carga de
     * SummaryEngine hasta altas horas. El default honesto es más fiable que un
     * perfil aprendido sobre 1-2 muestras.
     */
    const val MIN_SAMPLES = 5

    /**
     * Calcula el perfil a partir de las tareas completadas en los últimos
     * [WINDOW_DAYS] días:
     * - dayStartMinute: percentil 10 de la hora de finalización (cuándo empieza
     *   a funcionar de verdad), recortado a [6h, 12h] y redondeado a 15 min.
     * - dayEndMinute: percentil 90 de la hora de finalización, recortado a
     *   [16h, 23h] y redondeado a 15 min.
     * Sin datos suficientes devuelve los valores predeterminados.
     *
     * Solo se consideran tareas raíz (`parentTaskId == null`), igual que
     * SummaryEngine, GuardianEngine, What Now y el planificador al medir
     * productividad del usuario. Las subtareas se completan a menudo en ráfagas
     * (desmarcar/marcar varias seguidas) y su `completedAt` duplica el del padre
     * cuando este se autocompleta ([SubtaskRules.shouldAutoCompleteParent]):
     * contarlas infla y sesga los percentiles. Una ráfaga nocturna de 10
     * subtareas empujaba el p90 a las 23:00 aunque la jornada real terminó
     * antes, lo que a su vez estiraba la ventana de [DayPlanner] y relajaba el
     * veredicto de carga de [SummaryEngine] hasta la madrugada. Filtrar a raíces
     * mide los horarios REALES de cierre de trabajo.
     */
    fun learn(
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): LearningProfile {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstOfWindow = today.minusDays(WINDOW_DAYS)
        val minutes = tasks.asSequence()
            .filter { it.completed && it.completedAt != null && it.parentTaskId == null }
            .mapNotNull { task ->
                val completedAt = task.completedAt ?: return@mapNotNull null
                val date = DateRules.toLocalDate(completedAt, zone)
                if (date.isBefore(firstOfWindow) || date.isAfter(today)) null
                else {
                    val time = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalTime()
                    time.hour * 60 + time.minute
                }
            }
            .sorted()
            .toList()

        if (minutes.size < MIN_SAMPLES) return LearningProfile()

        val start = percentile(minutes, 0.10f).coerceIn(6 * 60, 12 * 60)
        // El final se limita a un mínimo de una hora de jornada y nunca más
        // allá de las 23:00; es honesto con el comportamiento real del usuario.
        val end = percentile(minutes, 0.90f).coerceIn(start + 60, 23 * 60)

        return LearningProfile(
            dayStartMinute = roundToQuarter(start),
            dayEndMinute = roundToQuarter(end)
        )
    }

    private fun percentile(sorted: List<Int>, pct: Float): Int {
        val index = ((sorted.size - 1) * pct).roundToInt()
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }

    private fun roundToQuarter(minute: Int): Int = (minute / 15) * 15
}
