package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.TaskRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.InfoBanner
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.StatCard
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

enum class TaskFilter(val label: String) {
    PENDING("Pendientes"),
    TODAY("Hoy"),
    OVERDUE("Atrasadas"),
    UPCOMING("Próximas"),
    FLAGGED("Importantes"),
    COMPLETED("Completadas"),
    ALL("Todas")
}

enum class TaskSort(val label: String) {
    SMART("Inteligente"),
    DATE("Fecha"),
    PRIORITY("Prioridad")
}

@Composable
fun TasksScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit
) {
    var filter by remember { mutableStateOf(TaskFilter.PENDING) }
    var sort by remember { mutableStateOf(TaskSort.SMART) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }

    if (adding) {
        TaskEditorDialog(
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            onDismiss = { adding = false },
            onSave = { task, tags ->
                vm.saveTask(task, tags)
                adding = false
            }
        )
    }

    val now = System.currentTimeMillis()
    val filtered = state.rootTasks.filter { task ->
        !task.archived &&
            (query.isBlank() || task.title.contains(query, ignoreCase = true) || task.details.contains(query, ignoreCase = true)) &&
            when (filter) {
                TaskFilter.PENDING -> !task.completed
                TaskFilter.TODAY -> !task.completed && TaskRules.isDueToday(task)
                TaskFilter.OVERDUE -> !task.completed && TaskRules.isOverdue(task)
                TaskFilter.UPCOMING -> !task.completed && task.dueAt?.let { it > now && !TaskRules.isDueToday(task) } == true
                TaskFilter.FLAGGED -> !task.completed && task.flagged
                TaskFilter.COMPLETED -> task.completed
                TaskFilter.ALL -> true
            }
    }
    val shown = when (sort) {
        TaskSort.SMART -> filtered.sortedWith(
            compareBy<TaskEntity> { it.completed }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenByDescending { it.priority }
                .thenBy { it.title.lowercase() }
        )
        TaskSort.DATE -> filtered.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        TaskSort.PRIORITY -> filtered.sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            contentPadding.calculateTopPadding() + 20.dp,
            20.dp,
            contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                "CONTROL SIN RUIDO",
                "Tareas",
                "Busca, filtra y decide qué merece atención ahora.",
                "Nueva",
                onAction = { adding = true }
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    StatCard(
                        "Pendientes",
                        state.pendingCount.toString(),
                        "activas",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.Schedule
                    )
                }
                item {
                    StatCard(
                        "Atrasadas",
                        state.overdueTasks.size.toString(),
                        "por decidir",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.WarningAmber,
                        accent = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
                item {
                    StatCard(
                        "Completadas",
                        state.completedCount.toString(),
                        "históricas",
                        Modifier.width(170.dp),
                        icon = Icons.Outlined.CheckCircle,
                        accent = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item {
                InfoBanner(
                    "Hay ${state.overdueTasks.size} tareas atrasadas",
                    "No todas deben completarse: reprograma, archiva o elimina la obligación conscientemente."
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar tareas") },
                placeholder = { Text("Título o detalles") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, "Limpiar búsqueda")
                        }
                    }
                },
                singleLine = true
            )
        }

        item {
            Text("Vista", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LazyRow(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TaskFilter.entries) { value ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(value.label) }
                    )
                }
            }
        }

        item {
            Text("Orden", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LazyRow(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TaskSort.entries) { value ->
                    FilterChip(
                        selected = sort == value,
                        onClick = { sort = value },
                        label = { Text(value.label) }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = filter.label,
                supporting = if (query.isBlank()) "${shown.size} resultados" else "${shown.size} coincidencias para “$query”"
            )
        }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = when {
                        query.isNotBlank() -> "No encontramos coincidencias"
                        filter == TaskFilter.COMPLETED -> "Todavía no hay tareas completadas"
                        filter == TaskFilter.OVERDUE -> "Nada atrasado"
                        else -> "Nada por aquí"
                    },
                    description = when {
                        query.isNotBlank() -> "Prueba otra palabra o limpia la búsqueda para ver todas las tareas."
                        filter == TaskFilter.OVERDUE -> "Tu planificación está al día."
                        else -> "Cambia el filtro o crea una tarea nueva con un resultado concreto."
                    },
                    actionLabel = if (query.isBlank()) "Crear tarea" else null,
                    onAction = if (query.isBlank()) ({ adding = true }) else null
                )
            }
        } else {
            items(shown, key = { it.id }) { task ->
                TaskListItem(state, vm, task, onTask)
            }
        }
    }
}

@Composable
private fun TaskListItem(state: OrdiaUiState, vm: OrdiaViewModel, task: TaskEntity, onTask: (Long) -> Unit) {
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
