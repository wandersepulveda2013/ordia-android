package com.ordia.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.TaskRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var adding by remember { mutableStateOf(false) }
    var showSuggestedPlan by remember { mutableStateOf(false) }
    val currentLocale = composeLocale()
    if (adding) TaskEditorDialog(
        projects = state.projects,
        tags = state.tags,
            onAddTag = vm::addTag,
        defaultDueDate = selectedDate,
        onDismiss = { adding = false },
        onSave = { task, tags -> vm.saveTask(task, tags); adding = false }
    )
    val weekStartDay = if (state.preferences.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(weekStartDay))
    val week = (0..6).map { weekStart.plusDays(it.toLong()) }
    val tasksOnDate = state.pendingTasks.filter { TaskRules.isDueOn(it, selectedDate) }
    val suggestedPlan = remember(state.tasks, selectedDate) {
        DayPlanner.build(state.tasks, selectedDate)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ScreenHeader("MIRA EL TIEMPO", "Planificador", "Coloca cada tarea donde realmente cabe.", "Planificar") { adding = true } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1); selectedDate = month.atDay(1) }) { Icon(Icons.Outlined.ChevronLeft, "Mes anterior") }
                Text(month.month.getDisplayName(TextStyle.FULL, currentLocale).replaceFirstChar { it.uppercase() } + " ${month.year}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1); selectedDate = month.atDay(1) }) { Icon(Icons.Outlined.ChevronRight, "Mes siguiente") }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(week, key = { it.toEpochDay() }) { date ->
                    val selected = date == selectedDate
                    Surface(
                        modifier = Modifier.clickable { selectedDate = date; month = YearMonth.from(date) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, currentLocale).uppercase(), style = MaterialTheme.typography.labelSmall)
                            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            val count = state.pendingTasks.count { TaskRules.isDueOn(it, date) }
                            Text(if (count == 0) "—" else count.toString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Plan automático local", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (suggestedPlan.blocks.isEmpty()) "Añade tareas para que Ordia proponga un orden realista."
                                else "${suggestedPlan.blocks.size} bloques · ${suggestedPlan.scheduledMinutes} min ocupados · ${suggestedPlan.remainingMinutes} min libres",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showSuggestedPlan = !showSuggestedPlan },
                            enabled = suggestedPlan.blocks.isNotEmpty()
                        ) { Text(if (showSuggestedPlan) "Ocultar" else "Ver plan") }
                    }
                    if (showSuggestedPlan) {
                        suggestedPlan.blocks.forEach { block ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${DateRules.minutesToClock(block.startMinute)}–${DateRules.minutesToClock(block.endMinute)}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(block.title, style = MaterialTheme.typography.bodyLarge)
                                    if (block.overdue) Text("Atrasada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (suggestedPlan.unscheduledTaskIds.isNotEmpty()) {
                            Text(
                                "${suggestedPlan.unscheduledTaskIds.size} ${if (suggestedPlan.unscheduledTaskIds.size == 1) "tarea no cabe" else "tareas no caben"} en este horario.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { vm.applyDayPlan(suggestedPlan); showSuggestedPlan = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Aplicar este plan")
                        }
                    }
                }
            }
        }
        item { SectionHeader(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", currentLocale)).replaceFirstChar { it.uppercase() }, "${tasksOnDate.size} tareas") }
        if (tasksOnDate.isEmpty()) {
            item { EmptyState("Día disponible", "No hay tareas planificadas para esta fecha.", "Añadir tarea", onAction = { adding = true }) }
        } else {
            items(tasksOnDate, key = { it.id }) { task ->
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
        }
        if (state.inboxTasks.isNotEmpty()) {
            item { SectionHeader("Sin fecha", "Arrastra mentalmente menos: decide solo una fecha") }
            items(state.inboxTasks.take(5), key = { "backlog-${it.id}" }) { task ->
                Card(onClick = { onTask(task.id) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(task.title, modifier = Modifier.weight(1f))
                        Text("Planificar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
