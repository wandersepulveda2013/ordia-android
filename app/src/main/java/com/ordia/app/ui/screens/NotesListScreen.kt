package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.components.OrdiaFloatingButton
import com.ordia.app.ui.components.OrdiaNoteCard
import com.ordia.app.ui.components.OrdiaTopBar
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesListScreen(
    notes: List<NoteEntity>,
    onOpenNote: (NoteEntity) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
) {
    Scaffold(
        topBar = {
            OrdiaTopBar(title = "Ordía")
        },
        floatingActionButton = {
            OrdiaFloatingButton(onClick = onCreateNote) {
                Icon(Icons.Outlined.Add, contentDescription = "Nueva nota")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(note, onOpenNote, onTogglePin, onDeleteNote)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Un espacio en blanco.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                "Toca + para capturar lo que importa.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    onOpenNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val date = remember(note.updatedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.updatedAt))
    }
    val preview = remember(note.content) { note.content.take(120) }

    Box(modifier = Modifier.padding(bottom = 12.dp)) {
        OrdiaNoteCard(
            title = note.title,
            preview = preview,
            date = date,
            onClick = { onOpenNote(note) },
            isPinned = note.pinned,
            trailingIcon = {
                Column(horizontalAlignment = Alignment.End) {
                    if (note.pinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Fijada",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(bottom = 8.dp),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuOpen = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "Más",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (note.pinned) "Desfijar" else "Fijar") },
                                onClick = { menuOpen = false; onTogglePin(note) },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onDeleteNote(note) },
                            )
                        }
                    }
                }
            }
        )
    }
}
