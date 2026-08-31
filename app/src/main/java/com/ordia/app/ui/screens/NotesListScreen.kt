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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.util.relativeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    notes: List<NoteEntity>,
    onOpenNote: (NoteEntity) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onRestoreNote: (NoteEntity) -> Unit,
    onTogglePin: (Long) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUndo by remember { mutableStateOf<NoteEntity?>(null) }
    // Search mode is independent of the query text: opening it from the toolbar
    // must show the empty search field. Seeded from a lingering query so a
    // recreated screen (rotation/process death) keeps filtering consistently.
    var isSearching by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }

    // Clearing the field exits the search mode (back to the full list).
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) isSearching = false
    }

    LaunchedEffect(pendingUndo) {
        val note = pendingUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Nota eliminada",
            actionLabel = "Deshacer",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onRestoreNote(note)
        pendingUndo = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ordía", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(
                        onClick = {
                            isSearching = !isSearching
                            onSearchQueryChange("")
                        },
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = "Buscar notas")
                    }
                },
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
        // The header and the list share the Scaffold Box; wrapping them in a Column
        // (with the inset padding applied once) keeps the header stacked above the
        // list instead of being drawn over the first row.
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isSearching) {
                SearchHeader(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val showEmpty = if (isSearching) {
                searchQuery.isNotBlank() && notes.isEmpty()
            } else {
                notes.isEmpty()
            }
            when {
                showEmpty && isSearching -> NoSearchResults(Modifier.weight(1f))
                showEmpty -> EmptyState(Modifier.weight(1f))
                else -> NoteList(
                    notes = notes,
                    searchQuery = searchQuery,
                    modifier = Modifier.weight(1f),
                    onOpenNote = onOpenNote,
                    onTogglePin = onTogglePin,
                    onDeleteNote = { deleted ->
                        onDeleteNote(deleted)
                        pendingUndo = deleted
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Buscar notas") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
    )
}

@Composable
private fun NoteList(
    notes: List<NoteEntity>,
    searchQuery: String,
    modifier: Modifier = Modifier,
    onOpenNote: (NoteEntity) -> Unit,
    onTogglePin: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (searchQuery.isNotBlank()) {
            item(key = "result-count") {
                Text(
                    "Notas: ${notes.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
        items(notes, key = { it.id }) { note ->
            NoteRow(note, onOpenNote, onTogglePin) { deleted ->
                onDeleteNote(deleted)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun NoSearchResults(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                "Sin resultados",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Ninguna nota coincide con la búsqueda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
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
    onTogglePin: (Long) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val date = remember(note.updatedAt) {
        relativeLabel(note.updatedAt)
    }
    val preview = remember(note.content) { note.content.take(120) }

    val rowLabel = if (note.title.isBlank()) "Abrir nota sin título" else "Abrir nota: ${note.title}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = rowLabel) { onOpenNote(note) }
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
            val pinLabel = if (note.title.isBlank()) "Fijada, sin título" else "Fijada: ${note.title}"
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = pinLabel,
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
                    onClick = { menuOpen = false; onTogglePin(note.id) },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    onClick = { menuOpen = false; onDeleteNote(note) },
                )
            }
        }
    }
}
