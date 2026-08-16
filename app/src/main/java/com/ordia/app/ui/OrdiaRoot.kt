package com.ordia.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextEngine
import com.ordia.app.context.ContextIntent
import com.ordia.app.context.ContextIntentKind
import com.ordia.app.context.ContextResult
import com.ordia.app.context.ContextualKind
import com.ordia.app.context.ContextualSuggestion
import com.ordia.app.conversations.ChatImportParser
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.CaptureSource
import com.ordia.app.data.local.CaptureTarget
import com.ordia.app.data.preferences.ThemeMode
import com.ordia.app.domain.GuardianEngine
import com.ordia.app.ui.components.ContextualSuggestionDialog
import com.ordia.app.ui.navigation.Destination
import com.ordia.app.ui.navigation.OrdiaNavigation
import com.ordia.app.ui.screens.OnboardingScreen
import com.ordia.app.ui.theme.OrdiaTheme
import com.ordia.app.updates.OrdiaUpdateController
import com.ordia.app.updates.OrdiaUpdateController.UpdateState

/** Convierte un ContextIntent del nuevo motor al modelo ContextualSuggestion del diálogo existente */
private fun ContextIntent.toContextualSuggestion(): ContextualSuggestion {
    val kind = when (kind) {
        ContextIntentKind.STUDY -> ContextualKind.STUDY
        ContextIntentKind.EVENT, ContextIntentKind.APPOINTMENT, ContextIntentKind.MEETING,
        ContextIntentKind.CALL, ContextIntentKind.VISIT -> ContextualKind.EVENT
        ContextIntentKind.NOTE, ContextIntentKind.GOAL, ContextIntentKind.HABIT,
        ContextIntentKind.COMMITMENT_PERSONAL, ContextIntentKind.COMMITMENT_WORK -> ContextualKind.NOTE
        else -> ContextualKind.TASK
    }
    return ContextualSuggestion(
        id = id.replace("-", "").take(64).padEnd(64, '0'),
        kind = kind,
        title = title.take(100),
        dueAt = dueAt,
        confidence = confidence.toDouble().coerceIn(0.0, 1.0),
        sourcePackage = sourcePackage
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
// Los getString de OrdiaRoot viven en LaunchedEffect (procesamiento de texto entrante),
// ámbito no-componible donde stringResource no es válido.
@Composable
fun OrdiaRoot(
    incomingText: String? = null,
    incomingAttachmentUri: String? = null,
    incomingMimeType: String? = null,
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
            automationLogRepository = app.container.automationLogRepository,
            automationRuleRepository = app.container.automationRuleRepository,
            automationEngine = app.container.automationEngine,
            captureRepository = app.container.captureRepository,
            conversationRepository = app.container.conversationRepository,
            observationRepository = app.container.observationRepository,
            contextualSettingsStore = app.container.contextualSettingsStore,
            preferencesRepository = app.container.preferencesRepository,
            reminderScheduler = app.container.reminderScheduler,
            habitReminderScheduler = app.container.habitReminderScheduler,
            backupManager = app.container.backupManager
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onboardingBusy by viewModel.onboardingBusy.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingContext by remember { mutableStateOf<ContextualSuggestion?>(null) }
    var pendingConfirmationId by remember { mutableStateOf<String?>(null) }
    val updateState by OrdiaUpdateController.state.collectAsStateWithLifecycle()
    var updateDialogHidden by remember { mutableStateOf(false) }
    val noteFromContextText = stringResource(R.string.root_note_created_from_context)
    val taskFromContextText = stringResource(R.string.root_task_created_from_context)

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

    LaunchedEffect(incomingText, incomingAttachmentUri, incomingMimeType) {
        if (!incomingAttachmentUri.isNullOrBlank()) {
            viewModel.submitCapture(
                content = incomingText.orEmpty(),
                requestedTarget = CaptureTarget.AUTO,
                source = CaptureSource.SHARE,
                attachmentUri = incomingAttachmentUri,
                mimeType = incomingMimeType.orEmpty()
            )
            onIncomingTextConsumed()
        } else incomingText?.takeIf { it.isNotBlank() }?.let { text ->
            if (ChatImportParser.looksLikeConversation(text)) {
                viewModel.prepareSharedConversation(text)
                navController.navigate(Destination.Conversations.route) {
                    launchSingleTop = true
                }
            } else if (app.container.contextualSettingsStore.isActive()) {
                val engine = ContextEngine.getInstance(context)
                val source = ContextCaptureSource.SHARED_TEXT
                val result = engine.processTextAsync(text, source)
                when (result) {
                    is ContextResult.PendingConfirmation -> {
                        pendingContext = result.intent.toContextualSuggestion()
                        pendingConfirmationId = result.confirmationId
                    }
                    is ContextResult.Created -> {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.root_context_detected, result.intent.kind.displayName, result.intent.title.take(40))
                        )
                    }
                    is ContextResult.Discarded -> {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.root_context_discarded)
                        )
                    }
                }
            } else {
                // Captura universal segura: sin motor contextual activo, el texto
                // compartido se interpreta como tarea y cae en la Bandeja (INBOX)
                // si la confianza es baja. Nada se pierde.
                viewModel.captureSharedText(text)
                snackbarHostState.showSnackbar(context.getString(R.string.root_capture_saved_to_inbox))
            }
            onIncomingTextConsumed()
        }
    }

    LaunchedEffect(state.preferences.onboardingComplete) {
        if (state.preferences.onboardingComplete && pendingContext == null && app.container.contextualSettingsStore.isActive()) {
            // Compatibilidad: procesar sugerencias del store antiguo (migración)
            val old = app.container.contextualSuggestionStore.list().firstOrNull()
            if (old != null) {
                val engine = ContextEngine.getInstance(context)
                val result = engine.processTextAsync(old.title, ContextCaptureSource.SHARED_TEXT)
                if (result is ContextResult.PendingConfirmation) {
                    pendingContext = result.intent.toContextualSuggestion()
                    pendingConfirmationId = result.confirmationId
                }
                app.container.contextualSuggestionStore.remove(old.id)
            }
        }
    }
    LaunchedEffect(requestedDestination, requestedTaskId, state.preferences.onboardingComplete) {
        if (!state.preferences.onboardingComplete) return@LaunchedEffect
        when {
            requestedTaskId != null -> navController.navigate(Destination.task(requestedTaskId))
            requestedDestination == "focus" -> navController.navigate(Destination.Focus.route)
            requestedDestination == "guardian" -> navController.navigate(Destination.Guardian.route)
            requestedDestination == "settings" -> navController.navigate(Destination.Settings.route)
            requestedDestination == "contextual" -> navController.navigate(Destination.Conversations.route)
            requestedDestination == "conversations" -> navController.navigate(Destination.Conversations.route)
        }
        if (requestedDestination != null || requestedTaskId != null) onNavigationRequestConsumed()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.AutomationApplied -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = context.getString(R.string.automation_undo_action)
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoLastAutomation()
                }
                is UiEvent.Archived -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = context.getString(R.string.archive_undo_action)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreArchived(event.kind, event.id)
                    }
                }
                else -> Unit
            }
        }
    }

    OrdiaTheme(state.preferences.themeMode) {
        val dark = when (state.preferences.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val rootView = LocalView.current
        SideEffect {
            val window = (rootView.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, rootView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        pendingContext?.let { suggestion ->
            ContextualSuggestionDialog(
                suggestion = suggestion,
                onConfirm = { title ->
                    val engine = ContextEngine.getInstance(context)
                    pendingConfirmationId?.let { engine.resolveConfirmation(it, accepted = true) }
                    when (suggestion.kind) {
                        ContextualKind.NOTE -> viewModel.addNote(title, noteFromContextText)
                        ContextualKind.TASK, ContextualKind.EVENT, ContextualKind.STUDY -> viewModel.addTask(
                            title = title,
                            details = taskFromContextText,
                            dueAt = suggestion.dueAt,
                            priority = TaskPriority.NORMAL
                        )
                    }
                    app.container.contextualSuggestionStore.remove(suggestion.id)
                    pendingContext = null
                    pendingConfirmationId = null
                },
                onDismiss = {
                    val engine = ContextEngine.getInstance(context)
                    pendingConfirmationId?.let { engine.resolveConfirmation(it, accepted = false) }
                    app.container.contextualSuggestionStore.remove(suggestion.id)
                    pendingContext = null
                    pendingConfirmationId = null
                }
            )
    }

    val availableUpdate = updateState as? UpdateState.Available
    if (availableUpdate != null && !updateDialogHidden && state.preferences.onboardingComplete) {
        val release = availableUpdate.release
        AlertDialog(
            onDismissRequest = { if (!availableUpdate.mandatory) updateDialogHidden = true },
            title = { Text(stringResource(R.string.update_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.update_dialog_text, release.tag))
                    if (availableUpdate.mandatory) {
                        Text(stringResource(R.string.update_dialog_mandatory))
                    }
                    if (release.changelog.isNotBlank()) {
                        Text(
                            release.changelog.take(220),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    OrdiaUpdateController.download(context, release)
                    updateDialogHidden = true
                }) { Text(stringResource(R.string.update_dialog_now)) }
            },
            dismissButton = if (availableUpdate.mandatory) null else {
                {
                    TextButton(onClick = { updateDialogHidden = true }) {
                        Text(stringResource(R.string.update_dialog_later))
                    }
                }
            }
        )
    }

    if (!state.preferences.onboardingComplete) {
            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                Box(Modifier.padding(padding)) {
                    OnboardingScreen(
                        selectedMode = state.preferences.interfaceMode,
                        onModeSelected = viewModel::setInterfaceMode,
                        onFinish = viewModel::finishOnboarding,
                        finishing = onboardingBusy
                    )
                }
            }
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
