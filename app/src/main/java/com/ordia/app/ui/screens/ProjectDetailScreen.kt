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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ProjectEditorDialog
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

@Composable
fun ProjectDetailScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    projectId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTask: (Long) -> Unit,
    onNote: (Long) -> Unit
) {
    val project = state.project(projectId)
    if (project == null) {
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(20.dp)) { EmptyState("Proyecto no disponible", "Puede estar archivado.", "Volver", onBack) }
        return
    }
    var editing by remember { mutableStateOf(false) }
    var addingTask by remember { mutableStateOf(false) }
    var addingNote by remember { mutableStateOf(false) }
    if (editing) ProjectEditorDialog(project, { editing = false }, { vm.saveProject(it); editing = false })
    if (addingTask) TaskEditorDialog(
        projects = state.projects,
        tags = state.tags,
            onAddTag = vm::addTag,
        existing = TaskEntity(title = "", projectId = project.id),
        onDismiss = { addingTask = false },
        onSave = { task, tags -> vm.saveTask(task.copy(id = 0, projectId = project.id), tags); addingTask = false }
    )
    if (addingNote) QuickNoteDialog(
        onDismiss = { addingNote = false },
        onSave = { title, body -> vm.saveNote(NoteEntity(title = title, body = body, projectId = project.id)); addingNote = false }
    )
    val tasks = state.rootTasks.filter { it.projectId == project.id && !it.archived }
    val notes = state.notes.filter { it.projectId == project.id && !it.archived }
    val progress = state.projectProgress(project.id)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 12.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                Text(project.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Editar proyecto") }
                IconButton(onClick = { vm.deleteProject(project); onBack() }) { Icon(Icons.Outlined.DeleteOutline, "Archivar proyecto") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (project.description.isNotBlank()) Text(project.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${tasks.count { it.completed }} de ${tasks.size} tareas completadas", style = MaterialTheme.typography.labelMedium)
            }
        }
        item { SectionHeader("Tareas", action = "Añadir", onAction = { addingTask = true }) }
        if (tasks.isEmpty()) item { Text("Este proyecto todavía no tiene tareas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(tasks, key = { it.id }) { task ->
            val subtasks = state.subtasks(task.id)
            TaskRow(task, project, subtasks.count { it.completed } to subtasks.size, { vm.toggleTask(task) }, { onTask(task.id) }, { vm.duplicateTask(task) }, { vm.deleteTask(task) })
        }
        item { SectionHeader("Notas", action = "Añadir", onAction = { addingNote = true }) }
        if (notes.isEmpty()) item { Text("Guarda decisiones, enlaces o contexto del proyecto.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(notes, key = { it.id }) { note ->
            OutlinedButton(onClick = { onNote(note.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    Text(note.body.take(100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickNoteDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva nota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Título") })
                OutlinedTextField(body, { body = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Contenido") }, minLines = 5)
            }
        },
        confirmButton = { Button(onClick = { onSave(title.ifBlank { "Nota sin título" }, body) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
