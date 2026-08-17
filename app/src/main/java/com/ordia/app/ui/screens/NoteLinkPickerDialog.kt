package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ordia.app.R
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.ui.OrdiaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Selector de notas para enlazar internamente (concepto `[[nota]]`).
 *
 * Muestra notas recientes cuando la consulta está vacía y resultados de
 * búsqueda al escribir. Al elegir una nota, llama a [onPick] con su id y título.
 *
 * No crea un grafo como pantalla principal: es un mero selector contextual
 * dentro del editor.
 */
@Composable
internal fun NoteLinkPickerDialog(
    vm: OrdiaViewModel,
    excludeId: Long,
    onDismiss: () -> Unit,
    onPick: (noteId: Long, title: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NoteEntity>>(emptyList()) }

    LaunchedEffect(query) {
        results = withContext(Dispatchers.IO) {
            if (query.isBlank()) vm.recentNotes(excludeId = excludeId)
            else vm.searchNotes(query).filter { it.id != excludeId && !it.trashed }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.notes_editor_link_note_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.notes_editor_link_note_search)) },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(results, key = { it.id }) { note ->
                        Surface(
                            onClick = { onPick(note.id, note.title.ifBlank { note.body.take(40) }) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(note.title.ifBlank { note.body.take(60) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            }
                        }
                    }
                    if (results.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.notes_editor_link_note_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_editor_link_note_cancel)) }
            }
        }
    }
}
