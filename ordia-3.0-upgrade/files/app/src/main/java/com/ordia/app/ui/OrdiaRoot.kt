package com.ordia.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.ordia.app.OrdiaApplication
import com.ordia.app.context.ContextualAnalyzer
import com.ordia.app.context.ContextualKind
import com.ordia.app.context.ContextualSuggestion
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.components.ContextualSuggestionDialog
import com.ordia.app.ui.navigation.Destination
import com.ordia.app.ui.navigation.OrdiaNavigation
import com.ordia.app.ui.screens.OnboardingScreen
import com.ordia.app.ui.theme.OrdiaTheme

@Composable
fun OrdiaRoot(
    incomingText: String? = null,
    requestedDestination: String? = null,
    requestedTaskId: Long? = null,
    onIncomingTextConsumed: () -> Unit = {},
    onNavigationRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as OrdiaApplication
    val viewModel: OrdiaViewModel = viewModel(
        factory = OrdiaViewModel.Factory(
            context = context,
            taskRepository = app.container.taskRepository,
            projectRepository = app.container.projectRepository,
            noteRepository = app.container.noteRepository,
            habitRepository = app.container.habitRepository,
            focusRepository = app.container.focusRepository,
            routineRepository = app.container.routineRepository,
            tagRepository = app.container.tagRepository,
            attachmentRepository = app.container.attachmentRepository,
            preferencesRepository = app.container.preferencesRepository,
            reminderScheduler = app.container.reminderScheduler,
            backupManager = app.container.backupManager
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingContext by remember { mutableStateOf<ContextualSuggestion?>(null) }

    val guardianDerivedExperience = remember(
        state.tasks,
        state.habits,
        state.habitLogs,
        state.focusSessions,
        state.notes
    ) {
        GuardianEngine.derivedExperience(
            tasks = state.tasks,
            habits = state.habits,
            habitLogs = state.habitLogs,
            focusSessions = state.focusSessions,
            notes = state.notes
        )
    }

    LaunchedEffect(guardianDerivedExperience) {
        app.container.preferencesRepository.syncGuardianExperience(guardianDerivedExperience)
    }

    LaunchedEffect(incomingText) {
        incomingText?.takeIf { it.isNotBlank() }?.let { text ->
            if (app.container.contextualSettingsStore.isActive()) {
                pendingContext = ContextualAnalyzer.analyze(text)
                if (pendingContext == null) snackbarHostState.showSnackbar("Ordia no procesó el texto porque era sensible o ambiguo.")
            } else {
                snackbarHostState.showSnackbar("Activa la atención contextual para procesar texto compartido.")
            }
            onIncomingTextConsumed()
        }
    }

    LaunchedEffect(state.preferences.onboardingComplete) {
        if (state.preferences.onboardingComplete && pendingContext == null && app.container.contextualSettingsStore.isActive()) {
            pendingContext = app.container.contextualSuggestionStore.list().firstOrNull()
        }
    }
    LaunchedEffect(requestedDestination, requestedTaskId, state.preferences.onboardingComplete) {
        if (!state.preferences.onboardingComplete) return@LaunchedEffect
        when {
            requestedTaskId != null -> navController.navigate(Destination.task(requestedTaskId))
            requestedDestination == "focus" -> navController.navigate(Destination.Focus.route)
            requestedDestination == "guardian" -> navController.navigate(Destination.Guardian.route)
            requestedDestination == "settings" -> navController.navigate(Destination.Settings.route)
            requestedDestination == "contextual" -> navController.navigate(Destination.Contextual.route)
        }
        if (requestedDestination != null || requestedTaskId != null) onNavigationRequestConsumed()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                else -> Unit
            }
        }
    }

    OrdiaTheme(state.preferences.themeMode) {
        pendingContext?.let { suggestion ->
            ContextualSuggestionDialog(
                suggestion = suggestion,
                onConfirm = { title ->
                when (suggestion.kind) {
                    ContextualKind.NOTE -> viewModel.addNote(title, "Creada desde texto compartido y confirmada por el usuario.")
                    ContextualKind.TASK, ContextualKind.EVENT, ContextualKind.STUDY -> viewModel.addTask(
                        title = title,
                        details = "Sugerencia contextual confirmada por el usuario.",
                        dueAt = suggestion.dueAt,
                        priority = TaskPriority.NORMAL
                    )
                }
                    app.container.contextualSuggestionStore.remove(suggestion.id)
                pendingContext = null
            },
                onDismiss = {
                    app.container.contextualSuggestionStore.remove(suggestion.id)
                    pendingContext = null
                }
            )
    }

        if (!state.preferences.onboardingComplete) {
            OnboardingScreen(
                selectedMode = state.preferences.interfaceMode,
                onModeSelected = viewModel::setInterfaceMode,
                onFinish = { viewModel.setOnboardingComplete() }
            )
        } else {
            OrdiaNavigation(
                navController = navController,
                state = state,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState
            )
        }
    }
}
