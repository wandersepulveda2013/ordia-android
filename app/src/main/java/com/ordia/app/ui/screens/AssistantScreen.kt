package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ordia.app.R
import com.ordia.app.assistant.AssistantAction
import com.ordia.app.assistant.AssistantAnswer
import com.ordia.app.assistant.AssistantEngine
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ScreenHeader
import java.time.LocalDate

@Composable
fun AssistantScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    padding: PaddingValues,
    onPlanner: () -> Unit,
    onConversations: () -> Unit,
    onSearch: (String) -> Unit,
    onTask: (Long) -> Unit
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val commitments by vm.commitments.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var latest by remember { mutableStateOf<AssistantAnswer?>(null) }
    val prompts = listOf(
        stringResource(R.string.assistant_prompt_day),
        stringResource(R.string.assistant_prompt_now),
        stringResource(R.string.assistant_prompt_overdue),
        stringResource(R.string.assistant_prompt_minimal)
    )
    fun ask(value: String) {
        latest = AssistantEngine.answer(value, state.tasks, conversations, commitments)
        input = ""
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 20.dp, 20.dp, padding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.assistant_eyebrow), stringResource(R.string.assistant_title), stringResource(R.string.assistant_subtitle)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.assistant_private), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(prompts) { prompt -> AssistChip(onClick = { ask(prompt) }, label = { Text(prompt) }) }
            }
        }
        item {
            OrdiaInput(
                value = input,
                onValueChange = { input = it.take(2_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.assistant_field)) },
                minLines = 2,
                trailingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }
            )
        }
        item { OrdiaButton(onClick = { ask(input) }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.assistant_ask)) } }
        latest?.let { answer ->
            item {
                OrdiaCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(answer.text, style = MaterialTheme.typography.bodyLarge)
                        answer.relatedTaskIds.mapNotNull(state::task).forEach { task ->
                            AssistChip(onClick = { onTask(task.id) }, label = { Text(task.title) })
                        }
                        if (answer.action != AssistantAction.NONE) {
                            OrdiaButton(onClick = {
                                when (answer.action) {
                                    AssistantAction.OPEN_PLANNER -> onPlanner()
                                    AssistantAction.OPEN_CONVERSATIONS -> onConversations()
                                    AssistantAction.RUN_REPLAN -> vm.replanDay(LocalDate.now())
                                    AssistantAction.CREATE_NOTE -> vm.addNote(stringResourceSafeTitle(), answer.actionPayload)
                                    AssistantAction.OPEN_SEARCH -> onSearch(answer.actionPayload.substringAfter(' ', answer.actionPayload))
                                    AssistantAction.NONE -> Unit
                                }
                            }) { Text(answer.action.label()) }
                        }
                    }
                }
            }
        }
    }
}

private fun stringResourceSafeTitle() = "Nota del asistente"

@Composable
private fun AssistantAction.label(): String = when (this) {
    AssistantAction.OPEN_PLANNER -> stringResource(R.string.assistant_action_planner)
    AssistantAction.OPEN_CONVERSATIONS -> stringResource(R.string.assistant_action_conversations)
    AssistantAction.RUN_REPLAN -> stringResource(R.string.assistant_action_replan)
    AssistantAction.CREATE_NOTE -> stringResource(R.string.assistant_action_note)
    AssistantAction.OPEN_SEARCH -> stringResource(R.string.assistant_action_search)
    AssistantAction.NONE -> ""
}
