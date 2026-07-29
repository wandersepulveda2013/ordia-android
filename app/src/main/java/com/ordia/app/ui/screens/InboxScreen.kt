package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow

@Composable
fun InboxScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    if (adding) TaskEditorDialog(
        projects = state.projects,
        tags = state.tags,
            onAddTag = vm::addTag,
        onDismiss = { adding = false },
        onSave = { task, tags -> vm.saveTask(task, tags); adding = false }
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("CAPTURA SIN FRICCIÓN", "Bandeja", "Todo lo que todavía no tiene fecha o proyecto.", "Añadir") { adding = true } }
        if (state.inboxTasks.isEmpty()) {
            item { EmptyState("Bandeja vacía", "Las ideas rápidas y tareas sin fecha aparecerán aquí.", "Capturar algo", onAction = { adding = true }) }
        } else {
            items(state.inboxTasks, key = { it.id }) { task ->
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
    }
}
