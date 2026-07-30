package com.ordia.app.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.GuardianAvatar
import com.ordia.app.ui.screens.ArchiveScreen
import com.ordia.app.ui.screens.ContextualAttentionScreen
import com.ordia.app.ui.screens.FocusScreen
import com.ordia.app.ui.screens.GuardianScreen
import com.ordia.app.ui.screens.HabitsScreen
import com.ordia.app.ui.screens.InboxScreen
import com.ordia.app.ui.screens.MoreScreen
import com.ordia.app.ui.screens.NoteEditorScreen
import com.ordia.app.ui.screens.NotesScreen
import com.ordia.app.ui.screens.PlannerScreen
import com.ordia.app.ui.screens.ProjectDetailScreen
import com.ordia.app.ui.screens.ProjectsScreen
import com.ordia.app.ui.screens.SearchScreen
import com.ordia.app.ui.screens.SettingsScreen
import com.ordia.app.ui.screens.StatisticsScreen
import com.ordia.app.ui.screens.TaskDetailScreen
import com.ordia.app.ui.screens.TasksScreen
import com.ordia.app.ui.screens.TodayScreen

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Today : Destination("today", "Hoy", Icons.Outlined.Home)
    data object Inbox : Destination("inbox", "Bandeja", Icons.Outlined.Inbox)
    data object Tasks : Destination("tasks", "Tareas", Icons.Outlined.CheckCircle)
    data object Planner : Destination("planner", "Plan", Icons.Outlined.CalendarMonth)
    data object Projects : Destination("projects", "Proyectos", Icons.Outlined.Folder)
    data object Notes : Destination("notes", "Notas", Icons.Outlined.Description)
    data object Habits : Destination("habits", "Hábitos", Icons.Outlined.Spa)
    data object Focus : Destination("focus", "Enfoque", Icons.Outlined.Timer)
    data object Search : Destination("search", "Buscar", Icons.Outlined.Search)
    data object Statistics : Destination("statistics", "Progreso", Icons.Outlined.BarChart)
    data object Archive : Destination("archive", "Archivo", Icons.Outlined.Archive)
    data object Settings : Destination("settings", "Ajustes", Icons.Outlined.Settings)
    data object More : Destination("more", "Más", Icons.Outlined.MoreHoriz)
    data object Guardian : Destination("guardian", "Guardián", Icons.Outlined.Psychology)
    data object Contextual : Destination("contextual", "Contexto", Icons.Outlined.AutoAwesome)

    companion object {
        const val TASK_ROUTE = "task/{taskId}"
        const val NOTE_ROUTE = "note/{noteId}"
        const val PROJECT_ROUTE = "project/{projectId}"
        fun task(id: Long) = "task/$id"
        fun note(id: Long) = "note/$id"
        fun project(id: Long) = "project/$id"
    }
}

private val compactItems = listOf(Destination.Today, Destination.Tasks, Destination.Planner, Destination.More)
private val compactMoreRoutes = setOf(
    Destination.Inbox.route,
    Destination.Projects.route,
    Destination.Notes.route,
    Destination.Habits.route,
    Destination.Focus.route,
    Destination.Search.route,
    Destination.Statistics.route,
    Destination.Archive.route,
    Destination.Guardian.route,
    Destination.Contextual.route,
    Destination.Settings.route
)
private val topLevelRoutes = setOf(
    Destination.Today.route,
    Destination.Inbox.route,
    Destination.Tasks.route,
    Destination.Planner.route,
    Destination.Projects.route,
    Destination.Notes.route,
    Destination.Habits.route,
    Destination.Focus.route,
    Destination.Search.route,
    Destination.Statistics.route,
    Destination.Archive.route,
    Destination.Guardian.route,
    Destination.Contextual.route,
    Destination.Settings.route,
    Destination.More.route
)

private fun wideItems(mode: InterfaceMode): List<Destination> = when (mode) {
    InterfaceMode.SIMPLE -> listOf(
        Destination.Today, Destination.Inbox, Destination.Tasks, Destination.Planner,
        Destination.Notes, Destination.Guardian, Destination.Focus, Destination.Search
    )
    InterfaceMode.ORGANIZED -> listOf(
        Destination.Today, Destination.Inbox, Destination.Tasks, Destination.Planner,
        Destination.Projects, Destination.Notes, Destination.Habits, Destination.Guardian, Destination.Focus, Destination.Search
    )
    InterfaceMode.ADVANCED -> listOf(
        Destination.Today, Destination.Inbox, Destination.Tasks, Destination.Planner,
        Destination.Projects, Destination.Notes, Destination.Habits, Destination.Guardian, Destination.Focus,
        Destination.Search, Destination.Statistics, Destination.Archive
    )
}

@Composable
fun OrdiaNavigation(
    navController: NavHostController,
    state: OrdiaUiState,
    viewModel: OrdiaViewModel,
    snackbarHostState: SnackbarHostState
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 760.dp && !state.preferences.compactNavigation
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route.orEmpty()
        val showTopLevelNavigation = route in topLevelRoutes

        if (useRail && showTopLevelNavigation) {
            Row(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        header = {
                            Column(Modifier.padding(top = 16.dp, bottom = 10.dp)) {
                                GuardianAvatar(42.dp)
                                Spacer(Modifier.height(6.dp))
                                Text("Ordia", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    ) {
                        wideItems(state.preferences.interfaceMode).forEach { item ->
                            NavigationRailItem(
                                selected = route == item.route,
                                onClick = { navController.navigateSingle(item.route) },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = route == Destination.Settings.route,
                            onClick = { navController.navigateSingle(Destination.Settings.route) },
                            icon = { Icon(Destination.Settings.icon, Destination.Settings.label) },
                            label = { Text(Destination.Settings.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.weight(1f)
                ) { padding -> OrdiaNavHost(navController, state, viewModel, padding) }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showTopLevelNavigation) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 4.dp
                        ) {
                            compactItems.forEach { item ->
                                val selected = route == item.route || (item == Destination.More && route in compactMoreRoutes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.navigateSingle(item.route) },
                                    icon = { Icon(item.icon, item.label) },
                                    label = { Text(item.label) },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding -> OrdiaNavHost(navController, state, viewModel, padding) }
        }
    }
}

@Composable
private fun OrdiaNavHost(
    navController: NavHostController,
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    NavHost(navController, startDestination = Destination.Today.route, modifier = Modifier.fillMaxSize()) {
        composable(Destination.Today.route) {
            TodayScreen(
                state,
                vm,
                padding,
                onTask = { navController.navigate(Destination.task(it)) },
                onOpenFocus = { navController.navigateSingle(Destination.Focus.route) },
                onOpenInbox = { navController.navigateSingle(Destination.Inbox.route) }
            )
        }
        composable(Destination.Inbox.route) { InboxScreen(state, vm, padding, onTask = { navController.navigate(Destination.task(it)) }) }
        composable(Destination.Tasks.route) { TasksScreen(state, vm, padding, onTask = { navController.navigate(Destination.task(it)) }) }
        composable(Destination.Planner.route) { PlannerScreen(state, vm, padding, onTask = { navController.navigate(Destination.task(it)) }) }
        composable(Destination.Projects.route) { ProjectsScreen(state, vm, padding, onProject = { navController.navigate(Destination.project(it)) }) }
        composable(Destination.Notes.route) { NotesScreen(state, vm, padding, onNote = { navController.navigate(Destination.note(it)) }) }
        composable(Destination.Habits.route) { HabitsScreen(state, vm, padding) }
        composable(Destination.Focus.route) { FocusScreen(state, vm, padding) }
        composable(Destination.Guardian.route) { GuardianScreen(state, padding) }
        composable(Destination.Contextual.route) { ContextualAttentionScreen(state, vm, padding) }
        composable(Destination.Search.route) {
            SearchScreen(
                state,
                padding,
                onTask = { navController.navigate(Destination.task(it)) },
                onProject = { navController.navigate(Destination.project(it)) },
                onNote = { navController.navigate(Destination.note(it)) },
                onHabits = { navController.navigateSingle(Destination.Habits.route) }
            )
        }
        composable(Destination.Statistics.route) { StatisticsScreen(state, padding) }
        composable(Destination.Archive.route) { ArchiveScreen(state, vm, padding) }
        composable(Destination.Settings.route) { SettingsScreen(state, vm, padding) }
        composable(Destination.More.route) {
            MoreScreen(state = state, padding = padding, open = { navController.navigateSingle(it) })
        }
        composable(
            Destination.TASK_ROUTE,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType })
        ) { entry ->
            TaskDetailScreen(
                state,
                vm,
                entry.arguments?.getLong("taskId") ?: 0L,
                padding,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Destination.NOTE_ROUTE,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { entry ->
            NoteEditorScreen(
                state,
                vm,
                entry.arguments?.getLong("noteId") ?: 0L,
                padding,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Destination.PROJECT_ROUTE,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
        ) { entry ->
            ProjectDetailScreen(
                state,
                vm,
                entry.arguments?.getLong("projectId") ?: 0L,
                padding,
                onBack = { navController.popBackStack() },
                onTask = { navController.navigate(Destination.task(it)) },
                onNote = { navController.navigate(Destination.note(it)) }
            )
        }
    }
}

private fun NavHostController.navigateSingle(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
