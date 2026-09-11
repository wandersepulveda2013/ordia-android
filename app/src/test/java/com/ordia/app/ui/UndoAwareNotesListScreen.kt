package com.ordia.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ordia.app.R
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.screens.NotesListScreen
import kotlinx.coroutines.channels.Channel

@Composable
fun UndoAwareNotesListScreen(
    notes: List<NoteEntity>,
    onOpenNote: (NoteEntity) -> Unit,
    onCreateNote: () -> Unit,
    onDeleteNote: (NoteEntity) -> Unit,
    onRestoreNote: (NoteEntity) -> Unit,
    onTogglePin: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoQueue = remember { Channel<NoteEntity>(Channel.UNLIMITED) }
    val noteDeletedMessage = stringResource(R.string.note_deleted)
    val undoAction = stringResource(R.string.undo)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NotesListScreen(
                notes = notes,
                onOpenNote = onOpenNote,
                onCreateNote = onCreateNote,
                onDeleteNote = { note ->
                    onDeleteNote(note)
                    undoQueue.trySend(note)
                },
                onRestoreNote = onRestoreNote,
                onTogglePin = onTogglePin,
            )
            LaunchedEffect(Unit) {
                for (note in undoQueue) {
                    val result = snackbarHostState.showSnackbar(
                        message = noteDeletedMessage,
                        actionLabel = undoAction,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) onRestoreNote(note)
                }
            }
        }
    }
}
