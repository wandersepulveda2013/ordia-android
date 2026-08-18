package com.ordia.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import com.ordia.app.ui.components.OrdiaButton
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.ordia.app.ui.components.OrdiaOutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.LearningEngine
import com.ordia.app.domain.PlanReason
import com.ordia.app.domain.PlannerCalendar
import com.ordia.app.domain.PlannerMonthDay
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private const val AGENDA_HORIZON_DAYS = 90

private enum class PlannerView { DAY, WEEK, MONTH, AGENDA }

@Suppress("NonObservableLocale")
@Composable
private fun composeLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty()) Locale.getDefault() else locales.get(0)
}

@Composable
fun PlannerScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit
) {
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var selectedViewName by rememberSaveable { mutableStateOf(PlannerView.DAY.name) }
    var adding by remember { mutableStateOf(false) }
    var showSuggestedPlan by remember { mutableStateOf(false) }
    var selectedBlockIds by remember { mutableStateOf(setOf<Long>()) }

    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val selectedView = PlannerView.entries.firstOrNull { it.name == selectedViewName } ?: PlannerView.DAY
    val displayedMonth = YearMonth.from(selectedDate)
    val currentLocale = composeLocale()
    val zone = ZoneId.systemDefault()
    val calendarTasks = state.pendingTasks
    val weekStartDay = if (state.preferences.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val week = remember(selectedDate, weekStartDay) {
        PlannerCalendar.weekDates(selectedDate, weekStartDay)
    }
    val tasksOnDate = remember(calendarTasks, selectedDate, zone) {
        PlannerCalendar.tasksOnDate(calendarTasks, selectedDate, zone)
    }
    val weekTasks = remember(calendarTasks, week, zone) {
        week.associateWith { date -> PlannerCalendar.tasksOnDate(calendarTasks, date, zone) }
    }
    val monthDays = remember(displayedMonth, weekStartDay) {
        PlannerCalendar.monthGrid(displayedMonth, weekStartDay)
    }
    val monthTaskCounts = remember(calendarTasks, monthDays, zone) {
        monthDays.associate { day ->
            day.date to PlannerCalendar.tasksOnDate(calendarTasks, day.date, zone).size
        }
    }
    val agendaGroups = remember(calendarTasks, selectedDate, zone) {
        PlannerCalendar.agenda(calendarTasks, selectedDate, AGENDA_HORIZON_DAYS, zone)
    }
    val suggestedPlan = remember(state.tasks, selectedDate, state.preferences.learningEnabled, zone) {
        val profile = if (state.preferences.learningEnabled) {
            LearningEngine.learn(state.tasks, System.currentTimeMillis())
        } else {
            null
        }
        DayPlanner.build(
            tasks = state.tasks,
            date = selectedDate,
            dayStartMinute = profile?.dayStartMinute ?: 9 * 60,
            dayEndMinute = profile?.dayEndMinute ?: 18 * 60,
            includeScheduledOnDate = true,
            zone = zone
        )
    }

    fun selectDate(date: LocalDate) {
        selectedEpochDay = date.toEpochDay()
    }

    LaunchedEffect(selectedDate) {
        showSuggestedPlan = false
        selectedBlockIds = emptySet()
    }

    if (adding) {
        TaskEditorDialog(
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            defaultDueDate = selectedDate,
            onDismiss = { adding = false },
            onSave = { task, tags ->
                vm.saveTask(task, tags)
                adding = false
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.planner_eyebrow),
                stringResource(R.string.planner_title),
                stringResource(R.string.planner_subtitle),
                stringResource(R.string.planner_action)
            ) { adding = true }
        }

        item {
            PlannerViewSelector(selectedView) { view -> selectedViewName = view.name }
        }

        item {
            CalendarPeriodHeader(
                title = periodTitle(selectedView, selectedDate, week, currentLocale),
                previousDescription = previousPeriodDescription(selectedView),
                nextDescription = nextPeriodDescription(selectedView),
                onPrevious = {
                    selectDate(
                        when (selectedView) {
                            PlannerView.DAY -> selectedDate.minusDays(1)
                            PlannerView.WEEK -> selectedDate.minusWeeks(1)
                            PlannerView.MONTH, PlannerView.AGENDA ->
                                PlannerCalendar.shiftMonthPreservingDay(selectedDate, -1)
                        }
                    )
                },
                onNext = {
                    selectDate(
                        when (selectedView) {
                            PlannerView.DAY -> selectedDate.plusDays(1)
                            PlannerView.WEEK -> selectedDate.plusWeeks(1)
                            PlannerView.MONTH, PlannerView.AGENDA ->
                                PlannerCalendar.shiftMonthPreservingDay(selectedDate, 1)
                        }
                    )
                },
                onToday = { selectDate(LocalDate.now()) }
            )
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Text(
                        stringResource(R.string.planner_tasks_only_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (selectedView == PlannerView.DAY || selectedView == PlannerView.WEEK) {
            item {
                WeekSelector(
                    dates = week,
                    selectedDate = selectedDate,
                    tasksByDate = weekTasks,
                    locale = currentLocale,
                    onSelect = ::selectDate
                )
            }
            item {
                AutoPlanCard(
                    plan = suggestedPlan,
                    tasks = state.tasks,
                    selectedBlockIds = selectedBlockIds,
                    showDetails = showSuggestedPlan,
                    onToggleDetails = { showSuggestedPlan = !showSuggestedPlan },
                    onToggleBlock = { taskId ->
                        selectedBlockIds = if (taskId in selectedBlockIds) {
                            selectedBlockIds - taskId
                        } else {
                            selectedBlockIds + taskId
                        }
                    },
                    onReplan = { vm.replanDay(selectedDate) },
                    onApply = {
                        vm.applyDayPlan(suggestedPlan, selectedBlockIds.takeIf { it.isNotEmpty() })
                        showSuggestedPlan = false
                        selectedBlockIds = emptySet()
                    }
                )
            }
        }

        when (selectedView) {
            PlannerView.DAY -> {
                item {
                    SectionHeader(
                        fullDate(selectedDate, currentLocale),
                        stringResource(R.string.planner_day_tasks, tasksOnDate.size)
                    )
                }
                if (tasksOnDate.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.planner_empty_day),
                            stringResource(R.string.planner_empty_day_desc),
                            stringResource(R.string.planner_add_task),
                            onAction = { adding = true }
                        )
                    }
                } else {
                    items(tasksOnDate, key = { "day-${selectedDate.toEpochDay()}-${it.id}" }) { task ->
                        PlannerTaskRow(task, selectedDate, state, vm, currentLocale, onTask)
                    }
                }
            }

            PlannerView.WEEK -> {
                week.forEach { date ->
                    val datedTasks = weekTasks[date].orEmpty()
                    item(key = "week-header-${date.toEpochDay()}") {
                        SectionHeader(
                            fullDate(date, currentLocale),
                            stringResource(R.string.planner_day_tasks, datedTasks.size)
                        )
                    }
                    if (datedTasks.isEmpty()) {
                        item(key = "week-empty-${date.toEpochDay()}") {
                            CompactEmptyPeriod(stringResource(R.string.planner_week_empty_day))
                        }
                    } else {
                        items(datedTasks, key = { "week-${date.toEpochDay()}-${it.id}" }) { task ->
                            PlannerTaskRow(task, date, state, vm, currentLocale, onTask)
                        }
                    }
                }
            }

            PlannerView.MONTH -> {
                item {
                    MonthGrid(
                        days = monthDays,
                        taskCounts = monthTaskCounts,
                        selectedDate = selectedDate,
                        today = LocalDate.now(),
                        locale = currentLocale,
                        onSelect = ::selectDate
                    )
                }
                item {
                    SectionHeader(
                        fullDate(selectedDate, currentLocale),
                        stringResource(R.string.planner_day_tasks, tasksOnDate.size)
                    )
                }
                if (tasksOnDate.isEmpty()) {
                    item { CompactEmptyPeriod(stringResource(R.string.planner_empty_day_desc)) }
                } else {
                    items(tasksOnDate, key = { "month-${selectedDate.toEpochDay()}-${it.id}" }) { task ->
                        PlannerTaskRow(task, selectedDate, state, vm, currentLocale, onTask)
                    }
                }
            }

            PlannerView.AGENDA -> {
                item {
                    SectionHeader(
                        stringResource(R.string.planner_agenda_title),
                        stringResource(R.string.planner_agenda_supporting, AGENDA_HORIZON_DAYS)
                    )
                }
                if (agendaGroups.isEmpty()) {
                    item {
                        EmptyState(
                            stringResource(R.string.planner_agenda_empty),
                            stringResource(R.string.planner_agenda_empty_desc),
                            stringResource(R.string.planner_add_task),
                            onAction = { adding = true }
                        )
                    }
                } else {
                    agendaGroups.forEach { group ->
                        item(key = "agenda-header-${group.date.toEpochDay()}") {
                            SectionHeader(
                                fullDate(group.date, currentLocale),
                                stringResource(R.string.planner_day_tasks, group.tasks.size)
                            )
                        }
                        items(group.tasks, key = { "agenda-${group.date.toEpochDay()}-${it.id}" }) { task ->
                            PlannerTaskRow(task, group.date, state, vm, currentLocale, onTask)
                        }
                    }
                }
            }
        }

        if (
            (selectedView == PlannerView.DAY || selectedView == PlannerView.WEEK) &&
            state.inboxTasks.isNotEmpty()
        ) {
            item {
                SectionHeader(
                    stringResource(R.string.suggestion_no_date),
                    stringResource(R.string.planner_no_date_desc)
                )
            }
            items(state.inboxTasks.take(5), key = { "backlog-${it.id}" }) { task ->
                Card(onClick = { onTask(task.id) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(task.title, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.planner_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerViewSelector(selected: PlannerView, onSelect: (PlannerView) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PlannerView.entries, key = { it.name }) { view ->
            FilterChip(
                selected = selected == view,
                onClick = { onSelect(view) },
                label = { Text(plannerViewLabel(view)) }
            )
        }
    }
}

@Composable
private fun plannerViewLabel(view: PlannerView): String = stringResource(
    when (view) {
        PlannerView.DAY -> R.string.planner_view_day
        PlannerView.WEEK -> R.string.planner_view_week
        PlannerView.MONTH -> R.string.planner_view_month
        PlannerView.AGENDA -> R.string.planner_view_agenda
    }
)

@Composable
private fun previousPeriodDescription(view: PlannerView): String = stringResource(
    when (view) {
        PlannerView.DAY -> R.string.planner_prev_day
        PlannerView.WEEK -> R.string.planner_prev_week
        PlannerView.MONTH, PlannerView.AGENDA -> R.string.planner_prev_month
    }
)

@Composable
private fun nextPeriodDescription(view: PlannerView): String = stringResource(
    when (view) {
        PlannerView.DAY -> R.string.planner_next_day
        PlannerView.WEEK -> R.string.planner_next_week
        PlannerView.MONTH, PlannerView.AGENDA -> R.string.planner_next_month
    }
)

@Composable
private fun CalendarPeriodHeader(
    title: String,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, previousDescription)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onToday) { Text(stringResource(R.string.planner_today)) }
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, nextDescription)
        }
    }
}

@Composable
private fun WeekSelector(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    tasksByDate: Map<LocalDate, List<TaskEntity>>,
    locale: Locale,
    onSelect: (LocalDate) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dates, key = { it.toEpochDay() }) { date ->
            val selected = date == selectedDate
            Surface(
                modifier = Modifier
                    .clickable { onSelect(date) }
                    .semantics { role = Role.Button; this.selected = selected },
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tasksByDate[date].orEmpty().size.takeIf { it > 0 }?.toString() ?: "—",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<PlannerMonthDay>,
    taskCounts: Map<LocalDate, Int>,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            days.take(7).forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    MonthDayCell(
                        day = day,
                        taskCount = taskCounts[day.date] ?: 0,
                        selected = day.date == selectedDate,
                        today = day.date == today,
                        onClick = { onSelect(day.date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: PlannerMonthDay,
    taskCount: Int,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        day.inDisplayedMonth -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        day.inDisplayedMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val outline = when {
        selected -> MaterialTheme.colorScheme.primary
        today -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    Surface(
        modifier = modifier
            .padding(1.dp)
            .aspectRatio(0.8f)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                if (!day.inDisplayedMonth) disabled()
            },
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, outline)
    ) {
        Column(
            Modifier.fillMaxSize().padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal
            )
            if (taskCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                ) {
                    Text(
                        taskCount.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannerTaskRow(
    task: TaskEntity,
    date: LocalDate,
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    locale: Locale,
    onTask: (Long) -> Unit
) {
    val timing = taskTiming(task, date, locale)
    val subtasks = state.subtasks(task.id)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (timing.isNotEmpty()) {
            Text(
                timing,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        TaskRow(
            task = task,
            project = state.project(task.projectId),
            subtaskProgress = subtasks.count { it.completed } to subtasks.size,
            onToggle = { vm.toggleTask(task) },
            onEdit = { onTask(task.id) },
            onDuplicate = { vm.duplicateTask(task) },
            onDelete = { vm.deleteTask(task) }
        )
    }
}

@Composable
private fun taskTiming(task: TaskEntity, date: LocalDate, locale: Locale): String = buildList {
    task.startAt?.takeIf { DateRules.toLocalDate(it) == date }?.let { start ->
        add(stringResource(R.string.planner_starts_at, DateRules.formatTime(start, locale)))
    }
    task.dueAt?.takeIf { DateRules.toLocalDate(it) == date }?.let { due ->
        add(stringResource(R.string.planner_due_at, DateRules.formatTime(due, locale)))
    }
}.joinToString(" · ")

@Composable
private fun CompactEmptyPeriod(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AutoPlanCard(
    plan: DayPlanner.Plan,
    tasks: List<TaskEntity>,
    selectedBlockIds: Set<Long>,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onToggleBlock: (Long) -> Unit,
    onReplan: () -> Unit,
    onApply: () -> Unit
) {
    Card {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.planner_auto_plan), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (plan.blocks.isEmpty()) {
                            stringResource(R.string.planner_auto_plan_empty)
                        } else {
                            stringResource(
                                R.string.planner_auto_plan_summary,
                                plan.blocks.size,
                                plan.scheduledMinutes,
                                plan.remainingMinutes
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OrdiaOutlinedButton(onClick = onToggleDetails, enabled = plan.blocks.isNotEmpty()) {
                    Text(
                        if (showDetails) stringResource(R.string.planner_hide)
                        else stringResource(R.string.planner_view)
                    )
                }
            }
            OrdiaOutlinedButton(onClick = onReplan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.planner_replan))
            }
            if (showDetails) {
                plan.blocks.forEach { block ->
                    val conflict = plan.conflicts.firstOrNull { it.taskId == block.taskId }
                    val originalStart = if (conflict != null) {
                        tasks.firstOrNull { it.id == block.taskId }?.startAt?.let { start ->
                            val zoned = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
                            DateRules.minutesToClock(zoned.hour * 60 + zoned.minute)
                        }
                    } else {
                        null
                    }
                    BlockRow(
                        block = block,
                        selected = block.taskId in selectedBlockIds,
                        onToggle = { onToggleBlock(block.taskId) },
                        reasonLabel = plannerReasonLabel(block.reason),
                        conflictLabel = if (conflict != null) {
                            stringResource(
                                R.string.planner_conflict_moved,
                                originalStart ?: "—",
                                DateRules.minutesToClock(block.startMinute)
                            )
                        } else {
                            null
                        }
                    )
                }
                if (plan.unscheduledTaskIds.isNotEmpty()) {
                    Text(
                        if (plan.unscheduledTaskIds.size == 1) {
                            stringResource(R.string.planner_unscheduled_single, plan.unscheduledTaskIds.size)
                        } else {
                            stringResource(R.string.planner_unscheduled_plural, plan.unscheduledTaskIds.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OrdiaButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (selectedBlockIds.isEmpty()) stringResource(R.string.planner_apply)
                        else stringResource(R.string.planner_apply_selection, selectedBlockIds.size)
                    )
                }
                if (selectedBlockIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.planner_select_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun periodTitle(
    view: PlannerView,
    selectedDate: LocalDate,
    week: List<LocalDate>,
    locale: Locale
): String = when (view) {
    PlannerView.DAY -> fullDate(selectedDate, locale)
    PlannerView.WEEK -> {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        "${week.first().format(formatter)} – ${week.last().format(formatter)}"
    }
    PlannerView.MONTH -> monthTitle(YearMonth.from(selectedDate), locale)
    PlannerView.AGENDA -> selectedDate.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    )
}

private fun monthTitle(month: YearMonth, locale: Locale): String =
    month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } +
        " ${month.year}"

private fun fullDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

@Composable
private fun plannerReasonLabel(reason: PlanReason): String = when (reason) {
    PlanReason.OVERDUE -> stringResource(R.string.planner_reason_overdue)
    PlanReason.URGENT -> stringResource(R.string.planner_reason_urgent)
    PlanReason.HIGH_PRIORITY -> stringResource(R.string.planner_reason_high)
    PlanReason.DUE_TODAY -> stringResource(R.string.planner_reason_due_today)
    PlanReason.SCHEDULED_TIME -> stringResource(R.string.planner_reason_scheduled)
    PlanReason.INBOX -> stringResource(R.string.planner_reason_inbox)
}

@Composable
private fun BlockRow(
    block: DayPlanner.Block,
    selected: Boolean,
    onToggle: () -> Unit,
    reasonLabel: String,
    conflictLabel: String?
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
            Text(
                "${DateRules.minutesToClock(block.startMinute)}–${DateRules.minutesToClock(block.endMinute)}",
                style = MaterialTheme.typography.labelLarge
            )
            Column(Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    reasonLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (block.overdue) {
                    Text(
                        stringResource(R.string.planner_overdue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (conflictLabel != null) {
            Text(
                conflictLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 52.dp, bottom = 4.dp)
            )
        }
    }
}
