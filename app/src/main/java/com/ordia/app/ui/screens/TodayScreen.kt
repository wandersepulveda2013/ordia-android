package com.ordia.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.domain.GuardianCoach
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.GuardianAvatar
import com.ordia.app.ui.components.GuardianMood
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatCard
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow
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
    var quickText by remember { mutableStateOf("") }
    var showTaskDialog by remember { mutableStateOf(false) }
    if (showTaskDialog) {
        TaskEditorDialog(
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            onDismiss = { showTaskDialog = false },
            onSave = { task, tags -> vm.saveTask(task, tags); showTaskDialog = false }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                title = greeting(),
                subtitle = if (state.pendingCount == 0) "No tienes pendientes. Puedes respirar." else "Tienes ${state.pendingCount} tareas pendientes."
            )
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val insight = state.guardianInsight
                    GuardianAvatar(62.dp, insight.tone.toMood())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(insight.eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondaryContainer)
                        Text(insight.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            insight.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                    insight.taskId?.let { taskId ->
                        IconButton(onClick = { onTask(taskId) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Abrir recomendación") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val doneToday = state.todayTasks.count { it.completed }
                val totalToday = state.todayTasks.size
                DayProgressRing(progress = if (totalToday == 0) 0f else doneToday.toFloat() / totalToday, modifier = Modifier.size(92.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Atrasadas", state.overdueTasks.size.toString(), "requieren atención", Modifier.fillMaxWidth())
                    StatCard("Enfoque", "${state.focusMinutesThisWeek}m", "esta semana", Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            quickText,
                            { quickText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ej.: Llamar mañana a las 9 !alta") },
                            singleLine = true
                        )
                        IconButton(
                            onClick = { vm.addSmartTask(quickText); quickText = "" },
                            enabled = quickText.isNotBlank()
                        ) { Icon(Icons.Outlined.Add, "Añadir a la bandeja") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickDateChip("Hoy") { vm.addSmartTask("$quickText hoy"); quickText = "" }
                        QuickDateChip("Mañana") { vm.addSmartTask("$quickText mañana"); quickText = "" }
                        QuickDateChip("Semana") { vm.addSmartTask("$quickText este finde"); quickText = "" }
                        QuickDateChip("Bandeja") { vm.addSmartTask(quickText); quickText = "" }
                    }
                }
            }
        }
        if (state.overdueTasks.isNotEmpty()) {
            item { SectionHeader("Atrasado", "Empieza por una sola cosa") }
            items(state.overdueTasks.take(4), key = { "overdue-${it.id}" }) { task -> TaskItem(state, vm, task, onTask) }
        }
        item { SectionHeader("Para hoy", action = if (state.inboxTasks.isNotEmpty()) "Ver bandeja (${state.inboxTasks.size})" else null, onAction = if (state.inboxTasks.isNotEmpty()) onOpenInbox else null) }
        if (state.todayTasks.isEmpty()) {
            item { EmptyState("Tu día tiene espacio", "Añade una tarea con fecha para verla aquí.", "Planificar tarea", onAction = { showTaskDialog = true }) }
        } else {
            items(state.todayTasks, key = { "today-${it.id}" }) { task -> TaskItem(state, vm, task, onTask) }
        }
        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE && state.habits.isNotEmpty()) {
            item { SectionHeader("Hábitos de hoy", "Un toque registra el avance") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.habits, key = { it.id }) { habit ->
                        val count = state.habitCount(habit.id)
                        Card(onClick = { vm.toggleHabit(habit) }) {
                            Column(Modifier.padding(16.dp).width(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(habit.title, style = MaterialTheme.typography.titleMedium)
                                Text("$count de ${habit.targetPerPeriod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(progress = { (count.toFloat() / habit.targetPerPeriod).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("Racha: ${state.habitStreak(habit)} días", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE && state.projects.isNotEmpty()) {
            item { SectionHeader("Proyectos activos") }
            items(state.projects.take(3), key = { "project-${it.id}" }) { project ->
                val progress = state.projectProgress(project.id)
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            Button(onClick = onOpenFocus, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Timer, null)
                Text("Iniciar una sesión de enfoque", Modifier.padding(start = 8.dp))
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

private fun GuardianCoach.Tone.toMood(): GuardianMood = when (this) {
    GuardianCoach.Tone.CALM -> GuardianMood.CALM
    GuardianCoach.Tone.FOCUSED -> GuardianMood.FOCUSED
    GuardianCoach.Tone.CELEBRATING -> GuardianMood.HAPPY
    GuardianCoach.Tone.GENTLE -> GuardianMood.CALM
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

@Composable
private fun DayProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.secondary
    val label = "${(progress * 100).toInt()}%"
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.12f
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(
                progressColor,
                -90f,
                360f * progress.coerceIn(0f, 1f),
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke)
            )
        }
        Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun QuickDateChip(label: String, onClick: () -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp)) }
    )
}
