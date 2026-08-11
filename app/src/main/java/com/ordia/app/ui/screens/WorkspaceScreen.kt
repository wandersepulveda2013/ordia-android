package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ordia.app.R
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.workspace.WorkspaceEvent
import com.ordia.app.workspace.WorkspacePanel
import com.ordia.app.workspace.WorkspacePanels

@Composable
fun WorkspaceScreen(state: OrdiaUiState, vm: OrdiaViewModel, padding: PaddingValues, openRoute: (WorkspacePanel) -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val automations by vm.automationRules.collectAsStateWithLifecycle()
    var encoded by rememberSaveable { mutableStateOf(WorkspacePanels.encode(com.ordia.app.workspace.WorkspaceState())) }
    val workspace = WorkspacePanels.decode(encoded)
    fun dispatch(event: WorkspaceEvent) { encoded = WorkspacePanels.encode(WorkspacePanels.reduce(workspace, event)) }
    val searchHint = stringResource(R.string.workspace_search_hint)
    val data: (WorkspacePanel) -> Pair<Int, List<String>> = { panel ->
        when (panel) {
            WorkspacePanel.TASKS -> state.pendingTasks.size to state.pendingTasks.take(6).map { it.title }
            WorkspacePanel.NOTES -> state.notes.size to state.notes.filterNot { it.archived }.take(6).map { it.title }
            WorkspacePanel.CONVERSATIONS -> conversations.size to conversations.take(6).map { it.title }
            WorkspacePanel.AUTOMATIONS -> automations.size to automations.take(6).map { it.name }
            WorkspacePanel.DAILY_PLAN -> state.todayTasks.size to state.todayTasks.take(6).map { it.title }
            WorkspacePanel.SEARCH -> (state.tasks.size + state.notes.size + conversations.size + automations.size) to listOf(searchHint)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
        val wide = maxWidth >= 840.dp
        val veryWide = maxWidth >= 1120.dp
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenHeader(stringResource(R.string.workspace_eyebrow), stringResource(R.string.workspace_title), stringResource(R.string.workspace_subtitle))
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PanelManager(workspace.open, workspace.minimized, workspace.active, Modifier.width(270.dp).fillMaxHeight(), ::dispatch)
                    val visible = workspace.visible()
                    workspace.active?.takeIf(visible::contains)?.let { panel ->
                        PanelView(panel, data(panel), Modifier.weight(1f).fillMaxHeight(), { openRoute(panel) })
                    }
                    if (veryWide) visible.firstOrNull { it != workspace.active }?.let { panel ->
                        PanelView(panel, data(panel), Modifier.weight(1f).fillMaxHeight(), { openRoute(panel) })
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PanelManager(workspace.open, workspace.minimized, workspace.active, Modifier.fillMaxWidth(), ::dispatch)
                    workspace.active?.takeIf(workspace.visible()::contains)?.let { panel ->
                        PanelView(panel, data(panel), Modifier.fillMaxWidth(), { openRoute(panel) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelManager(open: List<WorkspacePanel>, minimized: Set<WorkspacePanel>, active: WorkspacePanel?, modifier: Modifier, dispatch: (WorkspaceEvent) -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workspace_panels), style = MaterialTheme.typography.titleMedium)
            WorkspacePanel.entries.forEach { panel ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { dispatch(if (panel in open) WorkspaceEvent.Activate(panel) else WorkspaceEvent.Open(panel)) }, modifier = Modifier.weight(1f)) {
                        Text(panel.label(), color = if (panel == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    if (panel in open) {
                        IconButton(onClick = { dispatch(if (panel in minimized) WorkspaceEvent.Activate(panel) else WorkspaceEvent.Minimize(panel)) }) {
                            Icon(if (panel in minimized) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.Minimize, stringResource(if (panel in minimized) R.string.workspace_restore else R.string.workspace_minimize))
                        }
                        IconButton(onClick = { dispatch(WorkspaceEvent.Close(panel)) }) { Icon(Icons.Outlined.Close, stringResource(R.string.workspace_close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelView(panel: WorkspacePanel, data: Pair<Int, List<String>>, modifier: Modifier, open: () -> Unit) {
    Card(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(panel.label(), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.workspace_items, data.first), color = MaterialTheme.colorScheme.onSurfaceVariant)
            data.second.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = open) { Text(stringResource(R.string.workspace_open_full)) }
        }
    }
}

@Composable
private fun WorkspacePanel.label(): String = stringResource(when (this) {
    WorkspacePanel.TASKS -> R.string.workspace_tasks
    WorkspacePanel.NOTES -> R.string.workspace_notes
    WorkspacePanel.CONVERSATIONS -> R.string.workspace_conversations
    WorkspacePanel.AUTOMATIONS -> R.string.workspace_automations
    WorkspacePanel.DAILY_PLAN -> R.string.workspace_plan
    WorkspacePanel.SEARCH -> R.string.workspace_search
})
