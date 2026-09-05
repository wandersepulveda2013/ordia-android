package com.ordia.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.components.OrdiaCard
import com.ordia.app.ui.components.OrdiaFloatingActionButton
import com.ordia.app.ui.components.OrdiaSurface
import com.ordia.app.ui.components.OrdiaTopAppBar
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    notes: List<NoteEntity>,
    onOpenNote: (NoteEntity) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
) {
    OrdiaSurface {
        Scaffold(
            topBar = {
                OrdiaTopAppBar(title = "Ordía")
            },
            floatingActionButton = {
                OrdiaFloatingActionButton(
                    onClick = onCreateNote,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = "Nueva nota") }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (notes.isEmpty()) {
                EmptyState(padding)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onOpenNote = onOpenNote,
                            onTogglePin = onTogglePin,
                            onDeleteNote = onDeleteNote
                        )
                        Box(modifier = Modifier.size(8.dp)) // Spacer instead of divider
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Un espacio en blanco.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Toca + para capturar una idea, tarea o nota.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
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

    OrdiaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenNote(note) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (note.title.isNotBlank()) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (note.title.isNotBlank()) 4.dp else 0.dp),
                    )
                }
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (note.pinned) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = "Fijada",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp),
                )
            }
            Box(modifier = Modifier.padding(start = 4.dp)) {
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
                        text = { Text("Eliminar") },
                        onClick = { menuOpen = false; onDeleteNote(note) },
                    )
                }
            }
        }
    }
}
