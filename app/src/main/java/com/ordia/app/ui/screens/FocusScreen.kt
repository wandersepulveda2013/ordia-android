package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.domain.FocusClock
import com.ordia.app.domain.FocusTimerRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.GuardianAvatar
import com.ordia.app.ui.components.GuardianMood
import com.ordia.app.ui.components.ScreenHeader
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues) {
    var plannedMinutes by rememberSaveable { mutableIntStateOf(state.preferences.defaultFocusMinutes) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(plannedMinutes * 60) }
    var running by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var deadlineAt by rememberSaveable { mutableLongStateOf(0L) }
    var taskId by rememberSaveable { mutableStateOf<Long?>(state.nextTask?.id) }
    var taskMenu by remember { mutableStateOf(false) }

    LaunchedEffect(running, deadlineAt) {
        while (running && deadlineAt > 0L) {
            val now = System.currentTimeMillis()
            val next = FocusTimerRules.remainingSeconds(deadlineAt, now)
            remainingSeconds = next
            if (next == 0) {
                val completedStart = startedAt
                running = false
                startedAt = 0L
                deadlineAt = 0L
                remainingSeconds = plannedMinutes * 60
                if (completedStart > 0L) {
                    vm.saveFocusSession(taskId, completedStart, now, plannedMinutes, true)
                }
                break
            }
            delay(250L)
        }
    }

    fun reset(minutes: Int = plannedMinutes) {
        running = false
        plannedMinutes = minutes
        remainingSeconds = minutes * 60
        startedAt = 0L
        deadlineAt = 0L
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader(stringResource(R.string.focus_eyebrow), stringResource(R.string.focus_title), stringResource(R.string.focus_subtitle))
        Surface(
            modifier = Modifier.size(270.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuardianAvatar(64.dp, if (running) GuardianMood.FOCUSED else GuardianMood.CALM)
                    Text(FocusClock.format(remainingSeconds), style = MaterialTheme.typography.displayLarge)
                    Text(if (running) stringResource(R.string.focus_in_progress) else stringResource(R.string.focus_ready), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            OrdiaOutlinedButton(onClick = { taskMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(state.task(taskId ?: -1)?.title ?: stringResource(R.string.focus_choose_task), maxLines = 1)
            }
            DropdownMenu(taskMenu, { taskMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.focus_no_task)) }, onClick = { taskId = null; taskMenu = false })
                state.pendingTasks.take(20).forEach { task -> DropdownMenuItem(text = { Text(task.title) }, onClick = { taskId = task.id; taskMenu = false }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45, 60).forEach { minutes -> FilterChip(selected = plannedMinutes == minutes, onClick = { reset(minutes) }, label = { Text(stringResource(R.string.focus_minutes, minutes)) }, enabled = !running) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { reset() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.focus_reset)) }
            OrdiaButton(onClick = {
                val now = System.currentTimeMillis()
                if (running) {
                    remainingSeconds = FocusTimerRules.remainingSeconds(deadlineAt, now)
                    deadlineAt = 0L
                    running = false
                } else if (remainingSeconds > 0) {
                    if (startedAt == 0L) startedAt = now
                    deadlineAt = now + remainingSeconds * 1_000L
                    running = true
                }
            }) {
                Icon(if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
                Text(
                    if (running) stringResource(R.string.focus_pause)
                    else if (startedAt > 0L) stringResource(R.string.focus_resume)
                    else stringResource(R.string.focus_start),
                    Modifier.padding(start = 7.dp)
                )
            }
            IconButton(
                onClick = {
                    if (startedAt > 0L) vm.saveFocusSession(taskId, startedAt, System.currentTimeMillis(), plannedMinutes, false)
                    reset()
                },
                enabled = startedAt > 0L
            ) { Icon(Icons.Outlined.Stop, stringResource(R.string.focus_finish)) }
        }
        Text(
            stringResource(R.string.focus_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
