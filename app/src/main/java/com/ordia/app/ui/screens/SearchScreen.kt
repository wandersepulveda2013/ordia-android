package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.domain.SearchEngine
import com.ordia.app.domain.SearchKind
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.EmptyState
import com.ordia.app.ui.components.ScreenHeader

@Composable
fun SearchScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    contentPadding: PaddingValues,
    initialQuery: String = "",
    onTask: (Long) -> Unit,
    onProject: (Long) -> Unit,
    onNote: (Long) -> Unit,
    onHabits: () -> Unit,
    onConversations: () -> Unit,
    onAutomations: () -> Unit
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val commitments by vm.commitments.collectAsStateWithLifecycle()
    val automations by vm.automationRules.collectAsStateWithLifecycle()
    val results = remember(query, state.tasks, state.projects, state.notes, state.habits, conversations, commitments, automations) {
        SearchEngine.search(query, state.tasks, state.projects, state.notes, state.habits, conversations, commitments, automations)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.search_header_eyebrow), stringResource(R.string.search_header_title), stringResource(R.string.search_header_subtitle)) }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_field_label)) }, singleLine = true) }
        when {
            query.isBlank() -> item { EmptyState(stringResource(R.string.search_empty_prompt_title), stringResource(R.string.search_empty_prompt_subtitle)) }
            results.isEmpty() -> item { EmptyState(stringResource(R.string.search_no_results_title), stringResource(R.string.search_no_results_subtitle)) }
            else -> items(results, key = { "${it.kind}-${it.id}" }) { result ->
                Card(onClick = {
                    when (result.kind) {
                        SearchKind.TASK -> onTask(result.id)
                        SearchKind.PROJECT -> onProject(result.id)
                        SearchKind.NOTE -> onNote(result.id)
                        SearchKind.HABIT -> onHabits()
                        SearchKind.CONVERSATION, SearchKind.COMMITMENT -> onConversations()
                        SearchKind.AUTOMATION -> onAutomations()
                    }
                }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            when (result.kind) {
                                SearchKind.TASK -> Icons.Outlined.CheckCircle
                                SearchKind.PROJECT -> Icons.Outlined.Folder
                                SearchKind.NOTE -> Icons.Outlined.Description
                                SearchKind.HABIT -> Icons.Outlined.Spa
                                SearchKind.CONVERSATION, SearchKind.COMMITMENT -> Icons.Outlined.ChatBubbleOutline
                                SearchKind.AUTOMATION -> Icons.Outlined.Bolt
                            },
                            contentDescription = null
                        )
                        Column(Modifier.weight(1f)) {
                            Text(result.title, style = MaterialTheme.typography.titleMedium)
                            if (result.subtitle.isNotBlank()) Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(result.kind.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchKind.label(): String = when (this) {
    SearchKind.TASK -> stringResource(R.string.search_kind_task)
    SearchKind.PROJECT -> stringResource(R.string.search_kind_project)
    SearchKind.NOTE -> stringResource(R.string.search_kind_note)
    SearchKind.HABIT -> stringResource(R.string.search_kind_habit)
    SearchKind.CONVERSATION -> stringResource(R.string.search_kind_conversation)
    SearchKind.COMMITMENT -> stringResource(R.string.search_kind_commitment)
    SearchKind.AUTOMATION -> stringResource(R.string.search_kind_automation)
}
