package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.DateRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.PriorityPill
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

@Composable
fun TaskDetailScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    taskId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val task = state.task(taskId)
    var editing by remember(taskId) { mutableStateOf(false) }
    var subtaskText by remember { mutableStateOf("") }
    if (task == null) {
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(20.dp)) {
            EmptyState("Tarea no disponible", "Puede haber sido archivada o eliminada.", "Volver", onBack)
        }
        return
    }
    if (editing) {
        TaskEditorDialog(
            existing = task,
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            selectedTagIds = state.tagsForTask(task.id).map { it.id }.toSet(),
            onDismiss = { editing = false },
            onSave = { updated, tags -> vm.saveTask(updated, tags); editing = false }
        )
    }
    val subtasks = state.subtasks(task.id)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 12.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Volver") }
                Text("Detalle de tarea", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Editar") }
                IconButton(onClick = { vm.deleteTask(task); onBack() }) { Icon(Icons.Outlined.DeleteOutline, "Archivar") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.title, style = MaterialTheme.typography.headlineLarge)
                if (task.details.isNotBlank()) Text(task.details, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriorityPill(task.priority.name.lowercase().replaceFirstChar { it.uppercase() })
                    Text(state.project(task.projectId)?.name ?: "Sin proyecto", style = MaterialTheme.typography.bodyMedium)
                    Text(DateRules.formatDate(task.dueAt), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { vm.toggleTask(task) }) { Text(if (task.completed) "Marcar pendiente" else "Completar tarea") }
            }
        }
        item { Text("Pasos", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(subtaskText, { subtaskText = it }, modifier = Modifier.weight(1f), label = { Text("Añadir subtarea") }, singleLine = true)
                IconButton(onClick = { vm.addTask(subtaskText, parentTaskId = task.id); subtaskText = "" }, enabled = subtaskText.isNotBlank()) { Icon(Icons.Outlined.Add, "Añadir subtarea") }
            }
        }
        if (subtasks.isEmpty()) item { Text("Divide la tarea en pasos pequeños cuando lo necesites.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(subtasks, key = { it.id }) { subtask ->
            TaskRow(
                task = subtask,
                onToggle = { vm.toggleTask(subtask) },
                onEdit = { },
                onDelete = { vm.deleteTask(subtask) }
            )
        }
        if (state.tagsForTask(task.id).isNotEmpty()) {
            item { Text("Etiquetas: ${state.tagsForTask(task.id).joinToString { it.name }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
