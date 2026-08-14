package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.core.EmptyState
import com.ordia.app.ui.components.core.ScreenHeader
import com.ordia.app.ui.components.core.SectionHeader

data class ArchivedItem(val kind: String, val id: Long, val title: String, val description: String)

@Composable
fun ArchiveScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues) {
    val items = buildList {
        state.archivedTasks.forEach { add(ArchivedItem("task", it.id, it.title, "Tarea")) }
        state.archivedProjects.forEach { add(ArchivedItem("project", it.id, it.name, "Proyecto")) }
        state.archivedNotes.forEach { add(ArchivedItem("note", it.id, it.title, "Nota")) }
        state.archivedHabits.forEach { add(ArchivedItem("habit", it.id, it.title, "Hábito")) }
        state.archivedRoutines.forEach { add(ArchivedItem("routine", it.id, it.name, "Rutina")) }
    }
    var deleting by remember { mutableStateOf<ArchivedItem?>(null) }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar definitivamente") },
            text = { Text("“${item.title}” se borrará de Ordia y no podrá recuperarse.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteArchivedPermanently(item.kind, item.id)
                    deleting = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp,
            contentPadding.calculateTopPadding() + 20.dp,
            20.dp,
            contentPadding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader("RECUPERA LO QUE NECESITES", "Archivo", "Los elementos archivados permanecen locales hasta que los elimines definitivamente.") }
        if (items.isEmpty()) {
            item { EmptyState("El archivo está vacío", "Las tareas, notas, proyectos, hábitos y rutinas archivadas aparecerán aquí.") }
        } else {
            item { SectionHeader("Elementos archivados", "${items.size} en total") }
            items.forEach { archived ->
                item(key = "${archived.kind}-${archived.id}") {
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(archived.title, style = MaterialTheme.typography.titleMedium)
                                Text(archived.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.restoreArchived(archived.kind, archived.id) }) {
                                Icon(Icons.Outlined.Restore, "Restaurar ${archived.title}")
                            }
                            IconButton(onClick = { deleting = archived }) {
                                Icon(Icons.Outlined.DeleteForever, "Eliminar definitivamente ${archived.title}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
