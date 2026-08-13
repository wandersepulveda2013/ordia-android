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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockType
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader

@Composable
fun NotesScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onNote: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var templateMenu by remember { mutableStateOf(false) }
    val notes = state.notes.filter { !it.archived && (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true)) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                ScreenHeader("PENSAR SIN PERDERSE", "Notas", "Páginas por bloques conectadas con tus proyectos.", "Nueva") { templateMenu = true }
                DropdownMenu(templateMenu, { templateMenu = false }) {
                    DropdownMenuItem(text = { Text("Nota en blanco") }, onClick = { templateMenu = false; onNote(0) })
                    DropdownMenuItem(text = { Text("Reunión") }, onClick = {
                        templateMenu = false
                        createTemplate(vm, "Notas de reunión", listOf(
                            NoteBlock(type = NoteBlockType.HEADING, text = "Objetivo"),
                            NoteBlock(text = ""),
                            NoteBlock(type = NoteBlockType.HEADING, text = "Decisiones"),
                            NoteBlock(type = NoteBlockType.BULLET, text = ""),
                            NoteBlock(type = NoteBlockType.HEADING, text = "Próximos pasos"),
                            NoteBlock(type = NoteBlockType.CHECKLIST, text = "")
                        ), onNote)
                    })
                    DropdownMenuItem(text = { Text("Plan semanal") }, onClick = {
                        templateMenu = false
                        createTemplate(vm, "Plan semanal", listOf(
                            NoteBlock(type = NoteBlockType.HEADING, text = "Prioridades"),
                            NoteBlock(type = NoteBlockType.CHECKLIST, text = ""),
                            NoteBlock(type = NoteBlockType.HEADING, text = "Puede esperar"),
                            NoteBlock(type = NoteBlockType.BULLET, text = ""),
                            NoteBlock(type = NoteBlockType.HEADING, text = "Revisión"),
                            NoteBlock(text = "")
                        ), onNote)
                    })
                    DropdownMenuItem(text = { Text("Diario breve") }, onClick = {
                        templateMenu = false
                        createTemplate(vm, "Diario", listOf(
                            NoteBlock(type = NoteBlockType.HEADING, text = "Qué pasó"),
                            NoteBlock(text = ""),
                            NoteBlock(type = NoteBlockType.HEADING, text = "Cómo me sentí"),
                            NoteBlock(text = ""),
                            NoteBlock(type = NoteBlockType.HEADING, text = "Qué necesito mañana"),
                            NoteBlock(type = NoteBlockType.CHECKLIST, text = "")
                        ), onNote)
                    })
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar en notas") }, singleLine = true) }
        if (notes.isEmpty()) {
            item { EmptyState("No hay notas", "Crea una página para guardar ideas, decisiones o información.", "Crear nota", onAction = { onNote(0) }) }
        } else {
            items(notes, key = { it.id }) { note ->
                Card(onClick = { onNote(note.id) }) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(note.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.togglePin(note) }) {
                                Icon(Icons.Outlined.PushPin, if (note.pinned) "Desfijar" else "Fijar", tint = if (note.pinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(note.body.ifBlank { "Nota vacía" }.take(180), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            state.project(note.projectId)?.let { Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                            Text(relativeTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun createTemplate(vm: OrdiaViewModel, title: String, blocks: List<NoteBlock>, onNote: (Long) -> Unit) {
    vm.saveNote(NoteEntity(title = title), blocks, onSaved = onNote)
}

private fun relativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "ahora"
        minutes < 60 -> "hace $minutes min"
        minutes < 1440 -> "hace ${minutes / 60} h"
        minutes < 10080 -> "hace ${minutes / 1440} d"
        else -> "hace ${minutes / 10080} sem"
    }
}
