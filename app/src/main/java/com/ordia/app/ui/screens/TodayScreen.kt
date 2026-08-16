package com.ordia.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayLoad
import com.ordia.app.domain.SummaryEngine
import com.ordia.app.domain.SubtaskRules
import com.ordia.app.domain.WhatNowEngine
import com.ordia.app.domain.WhatNowReason
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.CaptureChips
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.ui.components.TaskEditorDialog
import com.ordia.app.ui.components.TaskRow
import com.ordia.app.ui.components.recurrenceChipLabel
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.LearningEngine
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.CaptureSource
import com.ordia.app.data.local.CaptureTarget
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

/**
 * Inicio orientado a decisiones. Mantiene visibles la captura y el siguiente
 * paso, pero desplaza estadísticas, proyectos, hábitos y configuración a sus
 * pantallas propias para recuperar espacio real de trabajo.
 */
@Composable
fun TodayScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    onTask: (Long) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenPlanner: () -> Unit,
    onReviewMessages: () -> Unit,
    onQuickNote: () -> Unit
) {
    var quickText by rememberSaveable { mutableStateOf("") }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    if (showTaskDialog) {
        TaskEditorDialog(
            projects = state.projects,
            tags = state.tags,
            onAddTag = vm::addTag,
            onDismiss = { showTaskDialog = false },
            onSave = { task, tags ->
                vm.saveTask(task, tags)
                showTaskDialog = false
            }
        )
    }

    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            clockNow = System.currentTimeMillis()
        }
    }
    val today = remember(clockNow) { LocalDate.now() }
    val whatNow = remember(state.tasks, clockNow) { WhatNowEngine.suggest(state.tasks, clockNow) }
    val summary = remember(state.tasks, clockNow, state.preferences.learningEnabled) {
        // El veredicto del día (LIGHT/ON_TRACK/FULL/OVERLOADED) usa la misma
        // ventana de jornada que el planificador. Si el aprendizaje está activo,
        // se basa en los horarios reales del usuario en vez del 9–18 fijo, para
        // que la tarjeta de hoy no mienta ("OVERLOADED" a las 17:00 para quien
        // trabaja hasta las 23:00). Sin aprendizaje → defaults 9–18.
        val profile = if (state.preferences.learningEnabled) {
            LearningEngine.learn(state.tasks, clockNow)
        } else null
        SummaryEngine.summarize(state.tasks, clockNow, ZoneId.systemDefault(), profile, state.commitments)
    }
    val capture = {
        if (quickText.isNotBlank()) {
            vm.submitCapture(
                content = quickText,
                requestedTarget = CaptureTarget.AUTO,
                source = CaptureSource.COMPOSER
            )
            quickText = ""
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                title = greeting(),
                subtitle = when {
                    state.pendingCount == 0 -> stringResource(R.string.today_subtitle_clear)
                    state.overdueTasks.isNotEmpty() -> stringResource(R.string.today_subtitle_overdue, state.overdueTasks.size)
                    else -> stringResource(R.string.today_subtitle_active, state.pendingCount)
                },
                actionLabel = stringResource(R.string.today_new),
                onAction = { showTaskDialog = true }
            )
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quickText,
                        onValueChange = { quickText = it.take(10_000) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.today_capture_type_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        trailingIcon = {
                            FilledIconButton(onClick = capture, enabled = quickText.isNotBlank()) {
                                Icon(Icons.Outlined.Send, stringResource(R.string.today_capture_keyboard_action))
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { capture() })
                    )
                    val preview = remember(quickText) {
                        quickText.takeIf(String::isNotBlank)?.let { NaturalTaskParser.parse(it) }
                    }
                    preview?.let { parsed ->
                        val chips = buildList {
                            parsed.dueAt?.let { add("${DateRules.formatDate(it)} ${DateRules.formatTime(it)}".trim()) }
                            if (parsed.priority != TaskPriority.NORMAL) add(parsed.priority.name.lowercase())
                            parsed.reminderOffsetMinutes?.let { add(stringResource(R.string.capture_chip_reminder, it)) }
                            parsed.durationMinutes?.let { add(stringResource(R.string.capture_chip_duration, it)) }
                            recurrenceChipLabel(parsed.recurrence, parsed.recurrenceInterval, parsed.recurrenceDays)?.let(::add)
                        }
                        CaptureChips(
                            chips = chips,
                            hint = if (parsed.confidence < 0.5f) stringResource(R.string.capture_hint_inbox) else null
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onOpenPlanner,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.today_organize_day), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.today_organize_day_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                    )
                }
                Icon(Icons.Outlined.ArrowForward, null)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactAction(
                    label = stringResource(R.string.today_review_messages),
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = onReviewMessages,
                    modifier = Modifier.weight(1f)
                )
                CompactAction(
                    label = stringResource(R.string.today_quick_note),
                    icon = Icons.Outlined.EditNote,
                    onClick = onQuickNote,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.today_overdue_summary, state.overdueTasks.size),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.today_overdue_recovery),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = onOpenPlanner) { Icon(Icons.Outlined.ArrowForward, stringResource(R.string.today_open_planner_cd)) }
                    }
                }
            }
        }

        item {
            val title = whatNow?.task?.title ?: stringResource(R.string.what_now_empty)
            Card(
                onClick = { whatNow?.let { onTask(it.task.id) } },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.what_now_eyebrow), style = MaterialTheme.typography.labelSmall)
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        whatNow?.let {
                            Text(
                                whatNowReasonLabel(it.reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (whatNow != null) Icon(Icons.Outlined.ArrowForward, stringResource(R.string.what_now_open))
                }
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.today_next_deadlines),
                stringResource(R.string.today_next_deadlines_support),
                action = if (state.inboxTasks.isNotEmpty()) stringResource(R.string.today_inbox_badge, state.inboxTasks.size) else null,
                onAction = if (state.inboxTasks.isNotEmpty()) onOpenInbox else null
            )
        }
        val nextTasks = (state.todayTasks + state.overdueTasks).distinctBy(TaskEntity::id).take(3)
        if (nextTasks.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.today_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(nextTasks, key = { "priority-${it.id}" }) { task ->
                TaskItem(state, vm, task, onTask)
            }
        }

        item {
            val verdict = dayLoadVerdict(summary.dayLoad)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.summary_completed_pending, summary.completedToday, summary.remainingToday),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.today_badge_minutes, summary.remainingMinutesToday),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (verdict != null) {
                        val suggestion = summary.deferralSuggestion
                        // Cuando el día está saturado y NO hay nada de hoy que
                        // posponer, la saturación viene de las vencidas (o de lo
                        // de hoy ya en marcha). En ese caso "dejar para mañana"
                        // es un consejo dañino para trabajo vencido: se sustituye
                        // por un mensaje honesto que replantea la decisión real.
                        val verdictText = when {
                            verdict == R.string.summary_load_overloaded && suggestion != null -> {
                                if (suggestion.canDefer) {
                                    stringResource(R.string.summary_load_overloaded_actionable, suggestion.title)
                                } else {
                                    stringResource(R.string.summary_load_overloaded_suggestion, suggestion.title)
                                }
                            }
                            verdict == R.string.summary_load_overloaded && summary.overdue > 0 -> {
                                stringResource(R.string.summary_load_overloaded_overdue, summary.overdue)
                            }
                            // Sobrecarga SOLO por olvidos agendados: no hay nada
                            // de hoy posponible (suggestion==null) ni vencidas.
                            // El genérico "dejar para mañana" es dañino aquí (un
                            // olvido se agrava al posponerlo); nombra la causa real.
                            verdict == R.string.summary_load_overloaded && summary.missedStart > 0 -> {
                                stringResource(R.string.summary_load_overloaded_missed_start, summary.missedStart)
                            }
                            else -> stringResource(verdict)
                        }
                        Text(
                            verdictText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .let {
                                    if (verdict == R.string.summary_load_overloaded &&
                                        suggestion != null && suggestion.canDefer) {
                                        it.clickable { vm.deferTaskToTomorrow(suggestion.taskId) }
                                    } else it
                                }
                        )
                    }
                    // Cola informativa del "3.er olvido" (missed-start) en la
                    // tarjeta: el olvido silencioso ya cuenta como carga del día
                    // (c.247), pero callarlo rompe la paridad con overdue y
                    // overdueCommitments (que sí se nombran). Aparece cuando hay
                    // olvidos y NO estamos ya en el veredicto saturado-por-olvidos
                    // (que nombra la causa arriba) para no duplicar el mensaje.
                    // Toca para ir a "¿Qué hago ahora?", donde el olvido se
                    // recupera como "Se te pasó su hora" (c.243/WhatNow paridad).
                    val showMissedStartInfo = summary.missedStart > 0 &&
                        !(verdict == R.string.summary_load_overloaded &&
                            summary.deferralSuggestion == null &&
                            summary.overdue == 0)
                    if (showMissedStartInfo) {
                        Text(
                            stringResource(R.string.today_missed_start, summary.missedStart),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { onOpenFocus() }
                        )
                    }
                    // Cuarta clase de olvido como cola informativa en la tarjeta
                    // de resumen: un compromiso vencido de conversación ("te llamo
                    // el viernes") no es tarea hasta convertirse, así que no
                    // influye en la carga/veredicto (c.246), pero callarlo haría
                    // que un día con 0 tareas vencidas leyera "0 vencidas" /
                    // "El día va a tiempo" ocultando el olvido real. Paridad con
                    // el asistente (c.286), el nudge del guardián (c.288), la
                    // tarjeta de insight (c.289) y la planificación (c.294). Un
                    // tap abre Conversaciones para revisarlo (fricción mínima).
                    if (summary.overdueCommitments > 0) {
                        Text(
                            stringResource(R.string.today_overdue_commitments, summary.overdueCommitments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { onReviewMessages() }
                        )
                    }
                }
            }
        }

        item {
            Surface(
                onClick = onOpenFocus,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Timer, null)
                    Text(stringResource(R.string.today_focus_mode), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.Outlined.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun CompactAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskItem(state: OrdiaUiState, vm: OrdiaViewModel, task: TaskEntity, onTask: (Long) -> Unit) {
    val subtasks = state.subtasks(task.id)
    TaskRow(
        task = task,
        project = state.project(task.projectId),
        subtaskProgress = SubtaskRules.progress(subtasks),
        onToggle = { vm.toggleTask(task) },
        onEdit = { onTask(task.id) },
        onDuplicate = { vm.duplicateTask(task) },
        onDelete = { vm.deleteTask(task) }
    )
}

@Composable
private fun whatNowReasonLabel(reason: WhatNowReason): String = when (reason) {
    WhatNowReason.IN_PROGRESS_NOW -> stringResource(R.string.what_now_reason_in_progress)
    WhatNowReason.OVERDUE -> stringResource(R.string.what_now_reason_overdue)
    WhatNowReason.IMMINENT_START -> stringResource(R.string.what_now_reason_imminent)
    WhatNowReason.MISSED_START -> stringResource(R.string.what_now_reason_missed_start)
    WhatNowReason.DUE_TODAY -> stringResource(R.string.what_now_reason_due_today)
    WhatNowReason.URGENT -> stringResource(R.string.what_now_reason_urgent)
    WhatNowReason.HIGH_PRIORITY -> stringResource(R.string.what_now_reason_high)
    WhatNowReason.NEXT_INBOX -> stringResource(R.string.what_now_reason_inbox)
    WhatNowReason.SCHEDULED_LATER -> stringResource(R.string.what_now_reason_scheduled_later)
}

@Composable
private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.today_greeting_morning)
    in 12..18 -> stringResource(R.string.today_greeting_afternoon)
    else -> stringResource(R.string.today_greeting_evening)
}

/**
 * Veredicto honesto del día como recurso de string, o null si no aporta
 * información (LIGHT: la tarjeta de conteo ya dice "0 para hoy").
 */
@Composable
private fun dayLoadVerdict(load: DayLoad): Int? = when (load) {
    DayLoad.LIGHT -> null
    DayLoad.ON_TRACK -> R.string.summary_load_on_track
    DayLoad.FULL -> R.string.summary_load_full
    DayLoad.OVERLOADED -> R.string.summary_load_overloaded
}
