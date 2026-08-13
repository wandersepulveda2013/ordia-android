package com.ordia.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.ordia.app.OrdiaApplication
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
            habitReminderScheduler = app.container.habitReminderScheduler,
            backupManager = app.container.backupManager
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(incomingText) {
        incomingText?.takeIf { it.isNotBlank() }?.let {
            viewModel.captureSharedText(it)
            onIncomingTextConsumed()
        }
    }
    LaunchedEffect(requestedDestination, requestedTaskId, state.preferences.onboardingComplete) {
        if (!state.preferences.onboardingComplete) return@LaunchedEffect
        when {
            requestedTaskId != null -> navController.navigate(Destination.task(requestedTaskId))
            requestedDestination == "focus" -> navController.navigate(Destination.Focus.route)
        }
        if (requestedDestination != null || requestedTaskId != null) onNavigationRequestConsumed()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = event.actionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        event.onAction?.invoke()
                    }
                }
                else -> Unit
            }
        }
    }

    OrdiaTheme(state.preferences.themeMode, state.preferences.accentPalette) {
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
