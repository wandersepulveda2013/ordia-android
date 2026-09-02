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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ordia.app.data.NoteEntity
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
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ordía", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNote,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) { Icon(Icons.Outlined.Add, contentDescription = "Nueva nota") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onOpenNote = onOpenNote,
                        onTogglePin = onTogglePin,
                        onRequestDelete = { pendingDelete = it },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }

    pendingDelete?.let { target ->
        DeleteNoteDialog(
            note = target,
            onConfirm = {
                pendingDelete = null
                onDeleteNote(target)
            },
            onDismiss = { pendingDelete = null },
        )
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
                "Una página en blanco\nes donde empieza todo.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Toca + para escribir.",
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
    onRequestDelete: (NoteEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val date = remember(note.updatedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.updatedAt))
    }
    val preview = remember(note.content) { note.content.take(120) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenNote(note) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
                modifier = Modifier.padding(top = 6.dp),
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
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Más")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (note.pinned) "Desfijar" else "Fijar") },
                    onClick = { menuOpen = false; onTogglePin(note) },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    onClick = { menuOpen = false; onRequestDelete(note) },
                )
            }
        }
    }
}

@Composable
private fun DeleteNoteDialog(
    note: NoteEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar nota") },
        text = {
            Text(
                if (note.title.isNotBlank()) {
                    "Se eliminará «${note.title.take(60)}». Esta acción no se puede deshacer."
                } else {
                    "Esta nota se eliminará definitivamente. Esta acción no se puede deshacer."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
