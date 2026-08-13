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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ProjectEditorDialog
import com.ordia.app.ui.components.ScreenHeader

@Composable
fun ProjectsScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onProject: (Long) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    if (adding) ProjectEditorDialog(onDismiss = { adding = false }, onSave = { vm.saveProject(it); adding = false })
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("RESULTADOS CONCRETOS", "Proyectos", "Reúne tareas y notas alrededor de un objetivo.", "Nuevo") { adding = true } }
        if (state.projects.isEmpty()) {
            item { EmptyState("Todavía no hay proyectos", "Crea uno cuando varias tareas persigan el mismo resultado.", "Crear proyecto", onAction = { adding = true }) }
        } else {
            items(state.projects, key = { it.id }) { project ->
                val progress = state.projectProgress(project.id)
                Card(onClick = { onProject(project.id) }) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(project.name, style = MaterialTheme.typography.titleLarge)
                                Text(project.description.ifBlank { project.status.name.lowercase().replaceFirstChar { it.uppercase() } }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onProject(project.id) }) { Icon(Icons.Outlined.ArrowForward, "Abrir proyecto") }
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}% completado", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
