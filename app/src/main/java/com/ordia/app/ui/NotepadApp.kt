package com.ordia.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ordia.app.ui.screens.NoteEditorScreen
import com.ordia.app.ui.screens.NotesListScreen
import com.ordia.app.ui.theme.NotepadTheme

@Composable
fun NotepadApp(viewModel: NotepadViewModel = viewModel()) {
    NotepadTheme {
        val navController = rememberNavController()
        val notes by viewModel.notes.collectAsState()

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                NotesListScreen(
                    notes = notes,
                    onOpenNote = { navController.navigate("note_editor?id=${it.id}") },
                    onCreateNote = { navController.navigate("note_editor") },
                    onDeleteNote = { viewModel.delete(it) },
                    onTogglePin = { viewModel.togglePinned(it) },
                )
            }
            composable("today") { Text("Today") }
            composable("capture") { Text("Capture") }
            composable("search") { Text("Search") }

            composable(
                route = "note_editor?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })
            ) { backStackEntry ->
                val idStr = backStackEntry.arguments?.getString("id")
                val editingId = idStr?.toLongOrNull()
                val current = remember(editingId, notes) {
                    editingId?.let { id -> notes.firstOrNull { it.id == id } }
                }

                NoteEditorScreen(
                    note = current,
                    onBack = { navController.popBackStack() },
                    onSave = { title, content, id ->
                        viewModel.save(title, content, id)
                    },
                )
            }
        }
    }
}
