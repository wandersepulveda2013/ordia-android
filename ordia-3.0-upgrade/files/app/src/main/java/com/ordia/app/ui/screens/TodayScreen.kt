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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ActionCard
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.VirtualGuardian
import com.ordia.app.ui.components.ProgressRing
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
                    state.pendingCount == 0 -> "Todo despejado. Usa el día con intención."
                    state.overdueTasks.isNotEmpty() -> "Hay ${state.overdueTasks.size} pendientes que necesitan una decisión."
                    else -> "Tienes ${state.pendingCount} tareas activas y un plan listo para avanzar."
                },
                actionLabel = "Nueva",
                onAction = { showTaskDialog = true }
            )
        }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "TU RITMO DE HOY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
                        )
                        Text(
                            if (dayTotal == 0) "Diseña un día ligero" else "$completedToday de $dayTotal completadas",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            when {
                                dayTotal == 0 -> "Añade una prioridad real y deja espacio para lo inesperado."
                                dayProgress >= 1f -> "Terminaste el plan. Lo siguiente es opcional."
                                dayProgress >= 0.5f -> "Ya cruzaste la mitad. Mantén el foco en lo importante."
                                else -> "Empieza por una tarea pequeña para crear impulso."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                    }
                    ProgressRing(
                        progress = dayProgress,
                        centerText = "${(dayProgress * 100).toInt()}%",
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
                        title = "Capturar tarea",
                        description = "Añade algo sin interrumpir tu ritmo.",
                        icon = Icons.Outlined.Add,
                        onClick = { showTaskDialog = true },
                        modifier = Modifier.width(210.dp),
                        badge = "Rápido"
                    )
                }
                item {
                    ActionCard(
                        title = "Entrar en enfoque",
                        description = "Inicia una sesión y mide tiempo real.",
                        icon = Icons.Outlined.Timer,
                        onClick = onOpenFocus,
                        modifier = Modifier.width(210.dp),
                        badge = "${state.focusMinutesThisWeek}m",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    ActionCard(
                        title = "Vaciar bandeja",
                        description = "Decide qué hacer con tus capturas.",
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
                        "Hoy",
                        state.todayTasks.size.toString(),
                        "todavía pendientes",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.CheckCircle
                    )
                }
                item {
                    StatCard(
                        "Atrasadas",
                        state.overdueTasks.size.toString(),
                        "requieren atención",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.WarningAmber,
                        accent = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    StatCard(
                        "Enfoque",
                        "${state.focusMinutesThisWeek}m",
                        "últimos 7 días",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.Timer,
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                }
                item {
                    StatCard(
                        "Completado",
                        "${state.completionRate}%",
                        "histórico de tareas",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.CheckCircle,
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Captura inteligente", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Escribe de forma natural: “Pagar internet mañana a las 9 !alta”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = quickText,
                            onValueChange = { quickText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("¿Qué no quieres olvidar?") },
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
                            Icon(Icons.Outlined.ArrowForward, "Guardar captura")
                        }
                    }
                }
            }
        }

        item {
            val insight = state.guardianInsight
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    VirtualGuardian(
                        snapshot = guardian,
                        size = 78.dp,
                        animationsEnabled = state.preferences.guardianAnimations && !state.preferences.reduceMotion
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${guardian.name.uppercase()} · ${guardian.stage.label.uppercase()}", style = MaterialTheme.typography.labelSmall)
                        Text("Tu guardián está ${guardian.mood.label}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            guardian.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                    insight.taskId?.let { taskId ->
                        IconButton(onClick = { onTask(taskId) }) {
                            Icon(Icons.Outlined.ArrowForward, "Abrir recomendación")
                        }
                    }
                }
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item { SectionHeader("Requiere una decisión", "Completa, reprograma o archiva; no dejes que se acumule.") }
            items(state.overdueTasks.take(4), key = { "overdue-${it.id}" }) { task ->
                TaskItem(state, vm, task, onTask)
            }
        }

        item {
            SectionHeader(
                "Plan de hoy",
                supporting = if (state.todayTasks.isEmpty()) "Todavía no has definido tareas con fecha para hoy." else "${state.todayTasks.size} tareas por resolver",
                action = if (state.inboxTasks.isNotEmpty()) "Bandeja (${state.inboxTasks.size})" else null,
                onAction = if (state.inboxTasks.isNotEmpty()) onOpenInbox else null
            )
        }
        if (state.todayTasks.isEmpty()) {
            item {
                EmptyState(
                    "Tu día tiene espacio",
                    "Añade una tarea con fecha para verla aquí y convertir intención en un plan concreto.",
                    "Planificar tarea",
                    onAction = { showTaskDialog = true }
                )
            }
        } else {
            items(state.todayTasks, key = { "today-${it.id}" }) { task ->
                TaskItem(state, vm, task, onTask)
            }
        }

        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE && state.habits.isNotEmpty()) {
            item { SectionHeader("Hábitos de hoy", "Toca una tarjeta para registrar el avance.") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.habits, key = { it.id }) { habit ->
                        val count = state.habitCount(habit.id)
                        val progress = (count.toFloat() / habit.targetPerPeriod.coerceAtLeast(1)).coerceIn(0f, 1f)
                        Card(
                            onClick = { vm.toggleHabit(habit) },
                            modifier = Modifier.width(230.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(habit.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("$count de ${habit.targetPerPeriod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text("Racha actual: ${state.habitStreak(habit)} días", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        if (state.preferences.interfaceMode != InterfaceMode.SIMPLE && state.projects.isNotEmpty()) {
            item { SectionHeader("Proyectos activos", "Una vista breve de los objetivos en marcha.") }
            items(state.projects.take(3), key = { "project-${it.id}" }) { project ->
                val progress = state.projectProgress(project.id)
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Surface(
                onClick = onOpenFocus,
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiary) {
                        Icon(Icons.Outlined.Timer, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onTertiary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Protege un bloque de enfoque", style = MaterialTheme.typography.titleMedium)
                        Text("Elige una tarea y trabaja sin cambiar de contexto.", style = MaterialTheme.typography.bodySmall)
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

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}
