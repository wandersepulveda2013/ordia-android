package com.ordia.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.ordia.app.OrdiaApplication
import com.ordia.app.ui.screens.NoteEditorScreen
import com.ordia.app.ui.screens.NotesListScreen
import com.ordia.app.ui.screens.TasksListScreen
import com.ordia.app.ui.theme.NotepadTheme

enum class BottomTab { TASKS, NOTES }

@Composable
fun NotepadApp() {
    val context = LocalContext.current
    val app = context.applicationContext as OrdiaApplication

    val notesViewModel: NotepadViewModel = viewModel(
        factory = NotepadViewModelFactory(app.repository)
    )
    val tasksViewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(app.taskRepository)
    )

    NotepadTheme {
        var currentTab by rememberSaveable { mutableStateOf(BottomTab.TASKS) }

        val notes by notesViewModel.notes.collectAsState()
        val tasks by tasksViewModel.tasks.collectAsState()

        var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
        var creatingNote by rememberSaveable { mutableStateOf(false) }

        val currentNote = remember(editingNoteId, notes) {
            editingNoteId?.let { id -> notes.firstOrNull { it.id == id } }
        }

        when {
            creatingNote || (editingNoteId != null && currentNote != null) -> {
                NoteEditorScreen(
                    note = if (creatingNote) null else currentNote,
                    onBack = {
                        creatingNote = false
                        editingNoteId = null
                    },
                    onSave = { title, content, id ->
                        notesViewModel.save(title, content, id)
                    },
                )
            }
            else -> {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            NavigationBarItem(
                                selected = currentTab == BottomTab.TASKS,
                                onClick = { currentTab = BottomTab.TASKS },
                                icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = "Tareas") },
                                label = { Text("Tareas") },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == BottomTab.NOTES,
                                onClick = { currentTab = BottomTab.NOTES },
                                icon = { Icon(Icons.Outlined.Note, contentDescription = "Notas") },
                                label = { Text("Notas") },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            BottomTab.TASKS -> {
                                TasksListScreen(
                                    tasks = tasks,
                                    onCreateTask = { tasksViewModel.save("Nueva tarea", "") }, // Temporary until full editor is built
                                    onToggleStatus = { tasksViewModel.toggleStatus(it) }
                                )
                            }
                            BottomTab.NOTES -> {
                                NotesListScreen(
                                    notes = notes,
                                    onOpenNote = { editingNoteId = it.id },
                                    onCreateNote = { creatingNote = true },
                                    onDeleteNote = { notesViewModel.delete(it) },
                                    onTogglePin = { notesViewModel.togglePinned(it) },
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
