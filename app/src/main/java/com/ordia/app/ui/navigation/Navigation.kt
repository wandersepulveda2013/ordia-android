package com.ordia.app.ui.navigation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.ordia.app.R
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
import com.ordia.app.ui.screens.ConversationsScreen
import com.ordia.app.ui.screens.CaptureScreen
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
import com.ordia.app.overlay.QuickCaptureActivity

sealed class Destination(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    data object Today : Destination("today", R.string.nav_today, Icons.Outlined.Home)
    data object Inbox : Destination("inbox", R.string.nav_inbox, Icons.Outlined.Inbox)
    data object Tasks : Destination("tasks", R.string.nav_tasks, Icons.Outlined.CheckCircle)
    data object Capture : Destination("capture", R.string.nav_capture, Icons.Outlined.AddCircleOutline)
    data object Planner : Destination("planner", R.string.nav_planner, Icons.Outlined.CalendarMonth)
    data object Projects : Destination("projects", R.string.nav_projects, Icons.Outlined.Folder)
    data object Notes : Destination("notes", R.string.nav_notes, Icons.Outlined.Description)
    data object Habits : Destination("habits", R.string.nav_habits, Icons.Outlined.Spa)
    data object Focus : Destination("focus", R.string.nav_focus, Icons.Outlined.Timer)
    data object Search : Destination("search", R.string.nav_search, Icons.Outlined.Search)
    data object Statistics : Destination("statistics", R.string.nav_statistics, Icons.Outlined.BarChart)
    data object Archive : Destination("archive", R.string.nav_archive, Icons.Outlined.Archive)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Outlined.Settings)
    data object More : Destination("more", R.string.nav_more, Icons.Outlined.MoreHoriz)
    data object Guardian : Destination("guardian", R.string.nav_guardian, Icons.Outlined.Psychology)
    data object Contextual : Destination("contextual", R.string.nav_contextual, Icons.Outlined.AutoAwesome)
    data object Conversations : Destination("conversations", R.string.nav_conversations, Icons.Outlined.ChatBubbleOutline)

    companion object {
        const val TASK_ROUTE = "task/{taskId}"
        const val NOTE_ROUTE = "note/{noteId}"
        const val PROJECT_ROUTE = "project/{projectId}"
        fun task(id: Long) = "task/$id"
        fun note(id: Long) = "note/$id"
        fun project(id: Long) = "project/$id"
    }
}

private val compactItems = listOf(Destination.Today, Destination.Tasks, Destination.Capture, Destination.Notes, Destination.More)
private val compactMoreRoutes = setOf(
    Destination.Inbox.route,
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
    Destination.Conversations.route,
    Destination.Settings.route
)
private val topLevelRoutes = setOf(
    Destination.Today.route,
    Destination.Inbox.route,
    Destination.Tasks.route,
    Destination.Capture.route,
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
    Destination.Conversations.route,
    Destination.Settings.route,
    Destination.More.route
)

private fun wideItems(mode: InterfaceMode): List<Destination> = when (mode) {
    InterfaceMode.SIMPLE -> listOf(
        Destination.Today, Destination.Capture, Destination.Inbox, Destination.Tasks, Destination.Conversations, Destination.Planner,
        Destination.Notes, Destination.Guardian, Destination.Focus, Destination.Search
    )
    InterfaceMode.ORGANIZED -> listOf(
        Destination.Today, Destination.Capture, Destination.Inbox, Destination.Tasks, Destination.Conversations, Destination.Planner,
        Destination.Projects, Destination.Notes, Destination.Habits, Destination.Guardian, Destination.Focus, Destination.Search
    )
    InterfaceMode.ADVANCED -> listOf(
        Destination.Today, Destination.Capture, Destination.Inbox, Destination.Tasks, Destination.Conversations, Destination.Planner,
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
                            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                                GuardianAvatar(36.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.app_short_name), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    ) {
                        wideItems(state.preferences.interfaceMode).forEach { item ->
                            NavigationRailItem(
                                selected = route == item.route,
                                onClick = { navController.navigateSingle(item.route) },
                                icon = { Icon(item.icon, stringResource(item.labelRes)) },
                                label = { Text(stringResource(item.labelRes)) },
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
                            icon = { Icon(Destination.Settings.icon, stringResource(Destination.Settings.labelRes)) },
                            label = { Text(stringResource(Destination.Settings.labelRes)) },
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
                    modifier = Modifier.weight(1f),
                    floatingActionButton = {
                        QuickCaptureFab(route, navController)
                    }
                ) { padding -> OrdiaNavHost(navController, state, viewModel, padding) }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
                floatingActionButton = {
                    if (showTopLevelNavigation) QuickCaptureFab(route, navController)
                },
                bottomBar = {
                    if (showTopLevelNavigation) {
                        Column(Modifier.navigationBarsPadding()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            NavigationBar(
                                modifier = Modifier.height(64.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
                            ) {
                                compactItems.forEach { item ->
                                    val selected = route == item.route || (item == Destination.More && route in compactMoreRoutes)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navController.navigateSingle(item.route) },
                                        icon = { Icon(item.icon, stringResource(item.labelRes)) },
                                        label = { Text(stringResource(item.labelRes)) },
                                        alwaysShowLabel = false,
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
                onOpenInbox = { navController.navigateSingle(Destination.Inbox.route) },
                onOpenPlanner = { navController.navigateSingle(Destination.Planner.route) },
                onReviewMessages = { navController.navigateSingle(Destination.Conversations.route) },
                onQuickNote = { navController.navigate(Destination.note(0L)) }
            )
        }
        composable(Destination.Inbox.route) { InboxScreen(state, vm, padding, onTask = { navController.navigate(Destination.task(it)) }) }
        composable(Destination.Tasks.route) { TasksScreen(state, vm, padding, onTask = { navController.navigate(Destination.task(it)) }) }
        composable(Destination.Capture.route) {
            CaptureScreen(
                vm = vm,
                padding = padding,
                onTask = { navController.navigate(Destination.task(it)) },
                onNote = { navController.navigate(Destination.note(it)) }
            )
        }
        composable(Destination.Planner.route) { PlannerScreen(state, vm, padding, onTask = { navController.navigate(Destination.task(it)) }) }
        composable(Destination.Projects.route) { ProjectsScreen(state, vm, padding, onProject = { navController.navigate(Destination.project(it)) }) }
        composable(Destination.Notes.route) { NotesScreen(state, vm, padding, onNote = { navController.navigate(Destination.note(it)) }) }
        composable(Destination.Habits.route) { HabitsScreen(state, vm, padding) }
        composable(Destination.Focus.route) { FocusScreen(state, vm, padding) }
        composable(Destination.Guardian.route) { GuardianScreen(state, padding) }
        composable(Destination.Contextual.route) { ContextualAttentionScreen(state, vm, padding) }
        composable(Destination.Conversations.route) {
            ConversationsScreen(
                vm = vm,
                padding = padding,
                onTask = { navController.navigate(Destination.task(it)) }
            )
        }
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
                onBack = { navController.popBackStack() },
                onTask = { navController.navigate(Destination.task(it)) }
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

@Composable
private fun QuickCaptureFab(route: String, navController: NavHostController) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(route) {
        expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.NAVIGATE)
    }
    BackHandler(expanded) {
        expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.BACK)
    }

    fun launchCapture(mode: String, voice: Boolean = false) {
        expanded = false
        context.startActivity(
            Intent(context, QuickCaptureActivity::class.java)
                .putExtra(QuickCaptureActivity.EXTRA_MODE, mode)
                .putExtra(QuickCaptureActivity.EXTRA_START_VOICE, voice)
        )
    }

    Box(
        Modifier.onPreviewKeyEvent { event ->
            if (expanded && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.ESCAPE)
                true
            } else false
        }
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.OUTSIDE)
            }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fab_task)) },
                leadingIcon = { Icon(Icons.Outlined.AddTask, null) },
                onClick = { launchCapture(QuickCaptureActivity.MODE_TASK) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fab_note)) },
                leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                onClick = { launchCapture(QuickCaptureActivity.MODE_NOTE) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fab_voice)) },
                leadingIcon = { Icon(Icons.Outlined.Mic, null) },
                onClick = { launchCapture(QuickCaptureActivity.MODE_TASK, voice = true) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fab_organize)) },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
                onClick = {
                    expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.NAVIGATE)
                    navController.navigateSingle(Destination.Planner.route)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.fab_messages)) },
                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) },
                onClick = {
                    expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.NAVIGATE)
                    navController.navigateSingle(Destination.Conversations.route)
                }
            )
        }
        FloatingActionButton(onClick = {
            expanded = QuickCaptureMenuState.reduce(expanded, QuickCaptureMenuEvent.TOGGLE)
        }) {
            Icon(
                if (expanded) Icons.Outlined.Close else Icons.Outlined.Add,
                stringResource(if (expanded) R.string.fab_close else R.string.fab_capture),
                Modifier.size(24.dp)
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
