package com.ordia.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ordia.app.ui.screens.NoteEditorScreen
import com.ordia.app.ui.screens.NotesListScreen
import com.ordia.app.ui.theme.NotepadTheme

@Composable
fun NotepadApp(viewModel: NotepadViewModel = viewModel()) {
    NotepadTheme {
        val notes by viewModel.notes.collectAsState()
        val searchQuery by viewModel.searchQuery.collectAsState()
        val searchResults by viewModel.searchResults.collectAsState()
        var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
        var creating by rememberSaveable { mutableStateOf(false) }

        val current = remember(editingId, notes) {
            editingId?.let { id -> notes.firstOrNull { it.id == id } }
        }

        when {
            creating || (editingId != null && current != null) -> {
                LaunchedEffect(editingId, current?.id, creating) {
                    viewModel.beginDraft(if (creating) null else editingId)
                }
                NoteEditorScreen(
                    note = if (creating) null else current,
                    onBack = {
                        creating = false
                        editingId = null
                    },
                    onAutosave = { title, content ->
                        viewModel.autosave(title, content)
                    },
                    onCommit = { title, content ->
                        viewModel.commitDraft(title, content)
                    },
                )
            }
            else -> {
                NotesListScreen(
                    notes = if (searchQuery.isBlank()) notes else searchResults,
                    onOpenNote = { editingId = it.id },
                    onCreateNote = { creating = true },
                    onDeleteNote = { viewModel.delete(it) },
                    onRestoreNote = { viewModel.restore(it) },
                    onTogglePin = { viewModel.togglePinned(it) },
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                )
            }
        }
    }
}
