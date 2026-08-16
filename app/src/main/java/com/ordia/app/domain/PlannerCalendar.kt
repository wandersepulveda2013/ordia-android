package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class PlannerMonthDay(
    val date: LocalDate,
    val inDisplayedMonth: Boolean
)

data class PlannerAgendaGroup(
    val date: LocalDate,
    val tasks: List<TaskEntity>
)

/**
 * Proyección local de tareas sobre el calendario.
 *
 * Una tarea pertenece a una fecha cuando su hora prevista (`startAt`) o su
 * fecha límite (`dueAt`) cae en ella. No representa eventos externos ni crea
 * datos nuevos.
 */
object PlannerCalendar {
    fun tasksOnDate(
        tasks: List<TaskEntity>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TaskEntity> = tasks
        .asSequence()
        .filter(::isVisible)
        .filter { task -> date in datesFor(task, zone) }
        .sortedWith(taskComparator(date, zone))
        .toList()

    fun weekDates(selectedDate: LocalDate, firstDayOfWeek: DayOfWeek): List<LocalDate> {
        val start = selectedDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return (0L..6L).map(start::plusDays)
    }

    /** Siempre devuelve seis semanas para que la grilla mensual no cambie de altura. */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<PlannerMonthDay> {
        val firstOfMonth = month.atDay(1)
        val offset = Math.floorMod(
            firstOfMonth.dayOfWeek.value - firstDayOfWeek.value,
            DayOfWeek.entries.size
        )
        val gridStart = firstOfMonth.minusDays(offset.toLong())
        return (0L until 42L).map { offsetDays ->
            val date = gridStart.plusDays(offsetDays)
            PlannerMonthDay(date, YearMonth.from(date) == month)
        }
    }

    fun agenda(
        tasks: List<TaskEntity>,
        fromDate: LocalDate,
        horizonDays: Int = 90,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PlannerAgendaGroup> {
        require(horizonDays > 0)
        val endExclusive = fromDate.plusDays(horizonDays.toLong())
        val grouped = sortedMapOf<LocalDate, MutableList<TaskEntity>>()
        tasks.asSequence()
            .filter(::isVisible)
            .forEach { task ->
                datesFor(task, zone)
                    .filter { date -> !date.isBefore(fromDate) && date.isBefore(endExclusive) }
                    .forEach { date -> grouped.getOrPut(date) { mutableListOf() }.add(task) }
            }
        return grouped.map { (date, datedTasks) ->
            PlannerAgendaGroup(
                date = date,
                tasks = datedTasks.distinctBy(TaskEntity::id).sortedWith(taskComparator(date, zone))
            )
        }
    }

    fun shiftMonthPreservingDay(date: LocalDate, months: Long): LocalDate {
        val targetMonth = YearMonth.from(date).plusMonths(months)
        return targetMonth.atDay(date.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth()))
    }

    private fun datesFor(task: TaskEntity, zone: ZoneId): Set<LocalDate> = buildSet {
        task.startAt?.let { add(DateRules.toLocalDate(it, zone)) }
        task.dueAt?.let { add(DateRules.toLocalDate(it, zone)) }
    }

    private fun isVisible(task: TaskEntity): Boolean =
        !task.completed &&
            !task.archived &&
            task.status != TaskStatus.CANCELLED &&
            task.parentTaskId == null

    private fun taskComparator(date: LocalDate, zone: ZoneId): Comparator<TaskEntity> =
        compareBy<TaskEntity> { timestampOnDate(it.startAt, date, zone) ?: Long.MAX_VALUE }
            .thenBy { timestampOnDate(it.dueAt, date, zone) ?: Long.MAX_VALUE }
            .thenByDescending { it.priority }
            .thenBy { it.sortOrder }
            .thenBy { it.createdAt }

    private fun timestampOnDate(epochMillis: Long?, date: LocalDate, zone: ZoneId): Long? =
        epochMillis?.takeIf { DateRules.toLocalDate(it, zone) == date }
}
