package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.TaskRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

enum class TaskFilter(val label: String) { PENDING("Pendientes"), TODAY("Hoy"), UPCOMING("Próximas"), FLAGGED("Importantes"), COMPLETED("Completadas"), ALL("Todas") }

@Composable
fun TasksScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit
) {
    var filter by remember { mutableStateOf(TaskFilter.PENDING) }
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    if (adding) TaskEditorDialog(
        projects = state.projects,
        tags = state.tags,
            onAddTag = vm::addTag,
        onDismiss = { adding = false },
        onSave = { task, tags -> vm.saveTask(task, tags); adding = false }
    )
    val now = System.currentTimeMillis()
    val shown = state.rootTasks.filter { task ->
        !task.archived && task.title.contains(query, ignoreCase = true) && when (filter) {
            TaskFilter.PENDING -> !task.completed
            TaskFilter.TODAY -> TaskRules.isDueToday(task)
            TaskFilter.UPCOMING -> !task.completed && task.dueAt?.let { it > now && !TaskRules.isDueToday(task) } == true
            TaskFilter.FLAGGED -> !task.completed && task.flagged
            TaskFilter.COMPLETED -> task.completed
            TaskFilter.ALL -> true
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("TODO EN UN LUGAR", "Tareas", "Organiza lo necesario sin llenar la pantalla de campos.", "Nueva") { adding = true } }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar tareas") }, singleLine = true) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskFilter.entries) { value -> FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value.label) }) }
            }
        }
        if (shown.isEmpty()) {
            item { EmptyState("Nada por aquí", "Cambia el filtro o crea una tarea nueva.", "Crear tarea") { adding = true } }
        } else {
            items(shown, key = { it.id }) { task -> TaskListItem(state, vm, task, onTask) }
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
