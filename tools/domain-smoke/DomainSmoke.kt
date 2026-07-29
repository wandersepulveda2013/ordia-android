package ordia.smoke

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.FocusClock
import com.ordia.app.domain.GuardianCoach
import com.ordia.app.domain.HabitRules
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.QuietHours
import com.ordia.app.domain.RecurrenceEngine
import com.ordia.app.domain.SearchEngine
import com.ordia.app.domain.SearchKind
import com.ordia.app.domain.TaskRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private var assertions = 0
private fun checkThat(condition: Boolean, message: String) {
    assertions++
    check(condition) { message }
}

fun main() {
    val zone = ZoneId.of("America/Santo_Domingo")
    val today = LocalDate.of(2026, 7, 29)
    val now = DateRules.toEpochMillis(today, LocalTime.NOON, zone)

    val parsed = NaturalTaskParser.parse("Llamar a Ana mañana a las 9:30 !alta", now, zone)
    checkThat(parsed.title == "Llamar a Ana", "Natural parser did not clean command tokens")
    checkThat(parsed.priority == TaskPriority.HIGH, "Natural parser priority failed")
    checkThat(DateRules.toLocalDate(requireNotNull(parsed.dueAt), zone) == today.plusDays(1), "Natural parser date failed")
    checkThat(DateRules.toLocalTime(parsed.dueAt, zone) == LocalTime.of(9, 30), "Natural parser time failed")
    val weekdayParsed = NaturalTaskParser.parse("Entregar reporte el viernes a las 15:00", now, zone)
    checkThat(weekdayParsed.title == "Entregar reporte", "Weekday parser title cleanup failed")
    checkThat(DateRules.toLocalDate(requireNotNull(weekdayParsed.dueAt), zone) == LocalDate.of(2026, 7, 31), "Weekday parser date failed")
    val relativeParsed = NaturalTaskParser.parse("Revisar el horno en 45 minutos", now, zone)
    checkThat(relativeParsed.dueAt == now + 45 * 60_000L, "Relative duration parser failed")

    val due = DateRules.toEpochMillis(today, LocalTime.of(9, 30), zone)
    val next = requireNotNull(
        RecurrenceEngine.nextOccurrence(
            TaskEntity(title = "Diaria", dueAt = due, reminderAt = due - 30 * 60_000L, recurrence = RecurrenceFrequency.DAILY),
            completedAt = due,
            zone = zone
        )
    )
    checkThat(DateRules.toLocalDate(requireNotNull(next.dueAt), zone) == today.plusDays(1), "Daily recurrence failed")
    val nextDue = requireNotNull(next.dueAt)
    checkThat(nextDue - requireNotNull(next.reminderAt) == 30 * 60_000L, "Reminder offset was not preserved")

    val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
    val normal = TaskEntity(id = 1, title = "Normal", priority = TaskPriority.NORMAL)
    checkThat(TaskRules.nextBestTask(listOf(normal, high), now) == high, "Task prioritization failed")
    checkThat(FocusClock.format(1500) == "25:00", "Focus clock format failed")

    val habit = HabitEntity(id = 7, title = "Caminar", frequency = HabitFrequency.WEEKLY, activeDays = "1,3,5")
    val logs = listOf(
        HabitLogEntity(7, today.toEpochDay()),
        HabitLogEntity(7, today.minusDays(2).toEpochDay()),
        HabitLogEntity(7, today.minusDays(5).toEpochDay())
    )
    checkThat(HabitRules.isScheduled(habit, today), "Habit schedule failed")
    checkThat(HabitRules.currentStreak(habit, logs, today) == 3, "Habit streak failed")

    val search = SearchEngine.search(
        "toolisto",
        listOf(TaskEntity(id = 1, title = "Revisar Toolisto")),
        listOf(ProjectEntity(id = 2, name = "Toolisto")),
        listOf(NoteEntity(id = 3, title = "Ideas", body = "Cambios para Toolisto")),
        listOf(HabitEntity(id = 4, title = "Revisión", details = "Abrir Toolisto"))
    )
    checkThat(search.map { it.kind }.toSet() == SearchKind.entries.toSet(), "Universal search failed")
    checkThat(search.first().kind == SearchKind.PROJECT, "Search ranking failed")
    val accentSearch = SearchEngine.search(
        "habito", emptyList(), emptyList(), emptyList(),
        listOf(HabitEntity(id = 8, title = "Hábito de lectura"))
    )
    checkThat(accentSearch.singleOrNull()?.id == 8L, "Accent-insensitive search failed")

    checkThat(QuietHours.contains(23 * 60, 22 * 60, 7 * 60), "Overnight quiet hours failed")
    checkThat(!QuietHours.contains(12 * 60, 22 * 60, 7 * 60), "Quiet hours false positive")
    checkThat(DateRules.minutesToClock(425) == "07:05", "Clock formatting failed")
    val afterMidnight = DateRules.toEpochMillis(today, 24 * 60 + 30, zone)
    checkThat(DateRules.toLocalDate(afterMidnight, zone) == today.plusDays(1), "Absolute minute day rollover failed")
    checkThat(DateRules.toLocalTime(afterMidnight, zone) == LocalTime.of(0, 30), "Absolute minute time rollover failed")

    val plan = DayPlanner.build(
        listOf(
            TaskEntity(id = 10, title = "Normal", durationMinutes = 30, priority = TaskPriority.NORMAL),
            TaskEntity(id = 11, title = "Urgente", durationMinutes = 30, priority = TaskPriority.URGENT)
        ),
        today,
        dayStartMinute = 9 * 60,
        dayEndMinute = 11 * 60,
        now = now,
        zone = zone
    )
    checkThat(plan.blocks.firstOrNull()?.taskId == 11L, "Day planner priority failed")
    checkThat(plan.blocks.size == 2, "Day planner capacity failed")

    val guardian = GuardianCoach.insight(
        tasks = listOf(TaskEntity(id = 20, title = "Vencida", dueAt = now - 60_000L)),
        habits = emptyList(),
        habitLogs = emptyList(),
        now = now,
        zone = zone
    )
    checkThat(guardian.taskId == 20L, "Guardian did not surface overdue task")
    checkThat(guardian.tone == GuardianCoach.Tone.GENTLE, "Guardian tone failed")

    println("Ordia domain smoke passed: $assertions assertions")
}
