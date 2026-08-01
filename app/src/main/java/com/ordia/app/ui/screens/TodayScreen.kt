package com.ordia.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.domain.DaySummary
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.SummaryEngine
import com.ordia.app.domain.WhatNowEngine
import com.ordia.app.domain.WhatNowReason
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ActionCard
import com.ordia.app.ui.components.CaptureChips
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.VirtualGuardian
import com.ordia.app.ui.components.ProgressRing
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatCard
import com.ordia.app.ui.labelRes
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow
import com.ordia.app.ui.components.recurrenceChipLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenInbox: () -> Unit
) {
    var quickText by rememberSaveable { mutableStateOf("") }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    if (showTaskDialog) {
        TaskEditorDialog(
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            onDismiss = { showTaskDialog = false },
            onSave = { task, tags ->
                vm.saveTask(task, tags)
                showTaskDialog = false
            }
        )
    }

    val today = LocalDate.now()
    val completedToday = state.rootTasks.count { task ->
        task.completedAt?.let { DateRules.toLocalDate(it) == today } == true
    }
    val dayTotal = state.todayTasks.size + completedToday
    val dayProgress = if (dayTotal == 0) 0f else completedToday.toFloat() / dayTotal
    val whatNow = remember(state.tasks) { WhatNowEngine.suggest(state.tasks) }
    val summary = remember(state.tasks) { SummaryEngine.summarize(state.tasks, System.currentTimeMillis()) }
    val guardian = GuardianEngine.snapshot(
        tasks = state.tasks,
        habits = state.habits,
        habitLogs = state.habitLogs,
        focusSessions = state.focusSessions,
        notes = state.notes,
        preferences = state.preferences
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                title = greeting(),
                subtitle = when {
                    state.pendingCount == 0 -> stringResource(R.string.today_subtitle_clear)
                    state.overdueTasks.isNotEmpty() -> stringResource(R.string.today_subtitle_overdue, state.overdueTasks.size)
                    else -> stringResource(R.string.today_subtitle_active, state.pendingCount)
                },
                actionLabel = stringResource(R.string.today_new),
                onAction = { showTaskDialog = true }
            )
        }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.today_rhythm_eyebrow),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
                        )
                        Text(
                            if (dayTotal == 0) stringResource(R.string.today_design_light_day) else stringResource(R.string.today_completed_of, completedToday, dayTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            when {
                                dayTotal == 0 -> stringResource(R.string.today_rhythm_add_priority)
                                dayProgress >= 1f -> stringResource(R.string.today_rhythm_finished)
                                dayProgress >= 0.5f -> stringResource(R.string.today_rhythm_halfway)
                                else -> stringResource(R.string.today_rhythm_start_small)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                    }
                    ProgressRing(
                        progress = dayProgress,
                        centerText = stringResource(R.string.today_progress_percent, (dayProgress * 100).toInt()),
                        size = 92.dp,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ActionCard(
                        title = stringResource(R.string.today_action_capture_task),
                        description = stringResource(R.string.today_action_capture_desc),
                        icon = Icons.Outlined.Add,
                        onClick = { showTaskDialog = true },
                        modifier = Modifier.width(210.dp),
                        badge = stringResource(R.string.today_badge_quick)
                    )
                }
                item {
                    ActionCard(
                        title = stringResource(R.string.today_action_focus),
                        description = stringResource(R.string.today_action_focus_desc),
                        icon = Icons.Outlined.Timer,
                        onClick = onOpenFocus,
                        modifier = Modifier.width(210.dp),
                        badge = stringResource(R.string.today_badge_minutes, state.focusMinutesThisWeek),
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    ActionCard(
                        title = stringResource(R.string.today_action_clear_inbox),
                        description = stringResource(R.string.today_action_clear_inbox_desc),
                        icon = Icons.Outlined.Inbox,
                        onClick = onOpenInbox,
                        modifier = Modifier.width(210.dp),
                        badge = state.inboxTasks.size.toString(),
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    StatCard(
                        stringResource(R.string.today_stat_today),
                        state.todayTasks.size.toString(),
                        stringResource(R.string.today_stat_today_support),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.CheckCircle
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.today_stat_overdue),
                        state.overdueTasks.size.toString(),
                        stringResource(R.string.today_stat_overdue_support),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.WarningAmber,
                        accent = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.today_stat_focus),
                        stringResource(R.string.today_badge_minutes, state.focusMinutesThisWeek),
                        stringResource(R.string.today_stat_focus_support),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.Timer,
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    StatCard(
                        stringResource(R.string.today_stat_completed),
                        stringResource(R.string.today_stat_percent, state.completionRate),
                        stringResource(R.string.today_stat_completed_support),
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.CheckCircle,
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item { DaySummaryCard(summary) }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.today_quick_capture_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.today_quick_capture_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = quickText,
                            onValueChange = { quickText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.today_quick_capture_placeholder)) },
                            singleLine = true
                        )
                        FilledTonalIconButton(
                            onClick = {
                                vm.addSmartTask(quickText)
                                quickText = ""
                            },
                            enabled = quickText.isNotBlank(),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Outlined.ArrowForward, stringResource(R.string.today_quick_capture_save))
                        }
                    }
                    val preview = remember(quickText) {
                        if (quickText.isBlank()) null else NaturalTaskParser.parse(quickText)
                    }
                    preview?.let { p ->
                        val previewChips = buildList {
                            p.dueAt?.let { add("${DateRules.formatDate(it)} ${DateRules.formatTime(it)}".trim()) }
                            if (p.priority != TaskPriority.NORMAL) {
                                add(stringResource(if (p.priority == TaskPriority.URGENT) R.string.dialog_priority_urgent else R.string.dialog_priority_high))
                            }
                            p.reminderOffsetMinutes?.let { add(stringResource(R.string.capture_chip_reminder, it)) }
                            p.durationMinutes?.let { add(stringResource(R.string.capture_chip_duration, it)) }
                            recurrenceChipLabel(p.recurrence, p.recurrenceInterval, p.recurrenceDays)?.let { add(it) }
                        }
                        CaptureChips(
                            chips = previewChips,
                            hint = if (p.confidence < 0.5f) stringResource(R.string.capture_hint_inbox) else null
                        )
                    }
                }
            }
        }

        item {
            val insight = state.guardianInsight
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    VirtualGuardian(
                        snapshot = guardian,
                        size = 78.dp,
                        animationsEnabled = state.preferences.guardianAnimations && !state.preferences.reduceMotion
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.today_guardian_identity, guardian.name.uppercase(), stringResource(guardian.stage.labelRes()).uppercase()), style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.today_guardian_mood, stringResource(guardian.mood.labelRes())), style = MaterialTheme.typography.titleLarge)
                        Text(
                            guardian.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                    insight.taskId?.let { taskId ->
                        IconButton(onClick = { onTask(taskId) }) {
                            Icon(Icons.Outlined.ArrowForward, stringResource(R.string.today_open_recommendation))
                        }
                    }
                }
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.today_require_decision), stringResource(R.string.today_require_decision_support)) }
            items(state.overdueTasks.take(4), key = { "overdue-${it.id}" }) { task ->
                TaskItem(state, vm, task, onTask)
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.today_plan_title),
                supporting = if (state.todayTasks.isEmpty()) stringResource(R.string.today_plan_empty) else stringResource(R.string.today_plan_count, state.todayTasks.size),
                action = if (state.inboxTasks.isNotEmpty()) stringResource(R.string.today_inbox_badge, state.inboxTasks.size) else null,
                onAction = if (state.inboxTasks.isNotEmpty()) onOpenInbox else null
            )
        }
        if (state.todayTasks.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.today_empty_title),
                    stringResource(R.string.today_empty_desc),
                    stringResource(R.string.today_plan_task_button),
                    onAction = { showTaskDialog = true }
                )
            }
        } else {
            items(state.todayTasks, key = { "today-${it.id}" }) { task ->
                TaskItem(state, vm, task, onTask)
            }
        }

        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE && state.habits.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.today_habits_title), stringResource(R.string.today_habits_support)) }
        item {
            if (whatNow != null) {
                Card(onClick = { onTask(whatNow.task.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.what_now_eyebrow), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                            Text(stringResource(R.string.what_now_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(whatNow.task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(whatNowReasonLabel(whatNow.reason), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Outlined.ArrowForward, stringResource(R.string.what_now_open), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.what_now_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.habits, key = { it.id }) { habit ->
                        val count = state.habitCount(habit.id)
                        val progress = (count.toFloat() / habit.targetPerPeriod.coerceAtLeast(1)).coerceIn(0f, 1f)
                        Card(
                            onClick = { vm.toggleHabit(habit) },
                            modifier = Modifier.width(230.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(habit.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.today_progress_percent, (progress * 100).toInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(stringResource(R.string.today_habit_progress, count, habit.targetPerPeriod), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.today_habit_streak, state.habitStreak(habit)), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE && state.projects.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.today_projects_title), stringResource(R.string.today_projects_support)) }
            items(state.projects.take(3), key = { "project-${it.id}" }) { project ->
                val progress = state.projectProgress(project.id)
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.today_progress_percent, (progress * 100).toInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Surface(
                onClick = onOpenFocus,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiary) {
                        Icon(Icons.Outlined.Timer, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onTertiary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.today_focus_block_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.today_focus_block_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun TaskItem(state: OrdiaUiState, vm: OrdiaViewModel, task: TaskEntity, onTask: (Long) -> Unit) {
    val subtasks = state.subtasks(task.id)
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

@Composable
private fun whatNowReasonLabel(reason: WhatNowReason): String = when (reason) {
    WhatNowReason.IN_PROGRESS_NOW -> stringResource(R.string.what_now_reason_in_progress)
    WhatNowReason.OVERDUE -> stringResource(R.string.what_now_reason_overdue)
    WhatNowReason.DUE_TODAY -> stringResource(R.string.what_now_reason_due_today)
    WhatNowReason.URGENT -> stringResource(R.string.what_now_reason_urgent)
    WhatNowReason.HIGH_PRIORITY -> stringResource(R.string.what_now_reason_high)
    WhatNowReason.NEXT_INBOX -> stringResource(R.string.what_now_reason_inbox)
    WhatNowReason.SCHEDULED_LATER -> stringResource(R.string.what_now_reason_scheduled_later)
}

/**
 * Resumen del día: lo completado hoy, lo pendiente con su estimación,
 * las atrasadas, la bandeja por revisar y el ritmo semanal.
 */
@Composable
private fun DaySummaryCard(summary: DaySummary) {
    val narrative = when {
        summary.overdue > 0 -> stringResource(
            R.string.summary_line_overdue,
            summary.overdue,
            summary.remainingToday,
            summary.remainingMinutesToday
        )
        summary.remainingToday > 0 -> stringResource(
            R.string.summary_line_remaining,
            summary.remainingToday,
            summary.remainingMinutesToday
        )
        else -> stringResource(R.string.summary_no_remaining)
    }
    val weeklyAverage = String.format(composeLocale(), "%.1f", summary.weekDailyAverage)
    val trend = when {
        summary.completedThisWeek == 0 -> null
        summary.completedToday > summary.weekDailyAverage * 1.2f -> stringResource(R.string.summary_week_ahead)
        summary.completedToday * 1.2f < summary.weekDailyAverage -> stringResource(R.string.summary_week_behind)
        else -> null
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.summary_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.summary_completed_pending, summary.completedToday, summary.remainingToday),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                narrative,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary.inboxPending > 0) {
                Text(
                    stringResource(R.string.summary_inbox, summary.inboxPending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.summary_week, summary.completedThisWeek, weeklyAverage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            trend?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.today_greeting_morning)
    in 12..18 -> stringResource(R.string.today_greeting_afternoon)
    else -> stringResource(R.string.today_greeting_evening)
}

@Suppress("NonObservableLocale")
@Composable
private fun composeLocale(): java.util.Locale {
    val locales = androidx.compose.ui.platform.LocalConfiguration.current.locales
    return if (locales.isEmpty()) java.util.Locale.getDefault() else locales.get(0)
}
