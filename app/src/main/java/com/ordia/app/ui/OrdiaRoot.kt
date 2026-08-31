package com.ordia.app.ui
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ordia.app.ui.screens.*

@Composable
fun OrdiaRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "today") {
        composable("today") { TodayScreen() }
        composable("tasks") { TasksScreen() }
        composable("planner") { PlannerScreen() }
        composable("notes") { NotesScreen() }
        composable("projects") { ProjectsScreen() }
        composable("habits") { HabitsScreen() }
        composable("focus") { FocusScreen() }
        composable("search") { SearchScreen() }
        composable("statistics") { StatisticsScreen() }
        composable("settings") { SettingsScreen() }
        composable("archive") { ArchiveScreen() }
    }
}
