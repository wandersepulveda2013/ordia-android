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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader

data class ArchivedItem(val kind: String, val id: Long, val title: String, val description: String)

@Composable
fun ArchiveScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues) {
    val kindTask = stringResource(R.string.suggestion_type_task)
    val kindProject = stringResource(R.string.archive_kind_project)
    val kindNote = stringResource(R.string.archive_kind_note)
    val kindHabit = stringResource(R.string.archive_kind_habit)
    val kindRoutine = stringResource(R.string.archive_kind_routine)
    val items = buildList {
        state.archivedTasks.forEach { add(ArchivedItem("task", it.id, it.title, kindTask)) }
        state.archivedProjects.forEach { add(ArchivedItem("project", it.id, it.name, kindProject)) }
        state.archivedNotes.forEach { add(ArchivedItem("note", it.id, it.title, kindNote)) }
        state.archivedHabits.forEach { add(ArchivedItem("habit", it.id, it.title, kindHabit)) }
        state.archivedRoutines.forEach { add(ArchivedItem("routine", it.id, it.name, kindRoutine)) }
    }
    var deleting by remember { mutableStateOf<ArchivedItem?>(null) }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.archive_delete_permanent)) },
            text = { Text(stringResource(R.string.archive_delete_confirm, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteArchivedPermanently(item.kind, item.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }
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
        item { ScreenHeader(stringResource(R.string.archive_eyebrow), stringResource(R.string.archive_title), stringResource(R.string.archive_subtitle)) }
        if (items.isEmpty()) {
            item { EmptyState(stringResource(R.string.archive_empty_title), stringResource(R.string.archive_empty_desc)) }
        } else {
            item { SectionHeader(stringResource(R.string.archive_items_header), stringResource(R.string.archive_count, items.size)) }
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
                                Icon(Icons.Outlined.Restore, stringResource(R.string.archive_restore_icon, archived.title))
                            }
                            IconButton(onClick = { deleting = archived }) {
                                Icon(Icons.Outlined.DeleteForever, stringResource(R.string.archive_delete_icon, archived.title), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
