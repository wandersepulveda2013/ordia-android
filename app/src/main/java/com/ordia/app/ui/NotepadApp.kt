package com.ordia.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ordia.app.ui.screens.CaptureScreen
import com.ordia.app.ui.screens.HomeScreen
import com.ordia.app.ui.screens.NoteEditorScreen
import com.ordia.app.ui.screens.NotesListScreen
import com.ordia.app.ui.screens.SearchScreen
import com.ordia.app.ui.screens.TodayScreen
import com.ordia.app.ui.theme.NotepadTheme

@Composable
fun NotepadApp(viewModel: NotepadViewModel = viewModel()) {
    NotepadTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    onNavigateToToday = { navController.navigate("today") },
                    onNavigateToSearch = { navController.navigate("search") },
                    onNavigateToCapture = { navController.navigate("capture") },
                    onNavigateToNotes = { navController.navigate("notes") }
                )
            }

            composable("today") {
                TodayScreen()
            }

            composable("search") {
                SearchScreen()
            }

            composable("capture") {
                CaptureScreen()
            }

            composable("notes") {
                val notes by viewModel.notes.collectAsState()
                NotesListScreen(
                    notes = notes,
                    onOpenNote = { navController.navigate("note_editor/${it.id}") },
                    onCreateNote = { navController.navigate("note_editor/new") },
                    onDeleteNote = { viewModel.delete(it) },
                    onTogglePin = { viewModel.togglePinned(it) },
                )
            }

            composable("note_editor/{noteId}") { backStackEntry ->
                val noteIdStr = backStackEntry.arguments?.getString("noteId")
                val notes by viewModel.notes.collectAsState()

                val currentNote = if (noteIdStr != "new") {
                    val id = noteIdStr?.toLongOrNull()
                    notes.firstOrNull { it.id == id }
                } else null

                NoteEditorScreen(
                    note = currentNote,
                    onBack = { navController.popBackStack() },
                    onSave = { title, content, id ->
                        viewModel.save(title, content, id)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
