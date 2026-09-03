package com.ordia.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ordia.app.R
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
        val snackbarHostState = remember { SnackbarHostState() }
        val persistenceFailedMessage = stringResource(R.string.error_persistence)

        // Non-fatal feedback on any persistence write failure, regardless of the
        // active screen (an autosave may fail while the user is in the editor). The ViewModel
        // emits the event and the app surfaces it here so it is never silently dropped.

        LaunchedEffect(Unit) {
            viewModel.persistenceError.collect {
                snackbarHostState.showSnackbar(
                    message = persistenceFailedMessage,
                    duration = SnackbarDuration.Short,
                )
            }
        }

        val current = remember(editingId, notes) {
            editingId?.let { id -> notes.firstOrNull { it.id == id } }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
