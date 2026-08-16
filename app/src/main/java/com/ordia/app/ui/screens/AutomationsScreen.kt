package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ordia.app.R
import com.ordia.app.automation.AutomationRuleCatalog
import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.domain.DateRules
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader

@Composable
fun AutomationsScreen(vm: OrdiaViewModel, padding: PaddingValues) {
    val rules by vm.automationRules.collectAsStateWithLifecycle()
    val history by vm.automationHistory.collectAsStateWithLifecycle()
    var instruction by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.automations_title),
                subtitle = stringResource(R.string.automations_subtitle)
            )
        }
        item {
            OrdiaCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrdiaInput(
                        value = instruction,
                        onValueChange = { instruction = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.automation_instruction_label)) },
                        supportingText = { Text(stringResource(R.string.automation_instruction_hint)) },
                        minLines = 2
                    )
                    OrdiaButton(
                        onClick = {
                            vm.createAutomationFromText(instruction)
                            instruction = ""
                        },
                        enabled = instruction.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Bolt, null)
                        Text(stringResource(R.string.automation_create), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.automation_templates), stringResource(R.string.automation_templates_desc)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AutomationRuleCatalog.templates, key = { it.key }) { template ->
                    OrdiaOutlinedButton(onClick = { vm.createAutomationTemplate(template.key) }) { Text(template.name) }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.automation_rules), stringResource(R.string.automation_rules_desc)) }
        if (rules.isEmpty()) {
            item { Text(stringResource(R.string.automation_rules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(rules, key = { it.id }) { rule ->
                AutomationRuleCard(
                    rule = rule,
                    onEnabled = { vm.setAutomationEnabled(rule, it) },
                    onTest = { vm.testAutomation(rule) },
                    onRun = { vm.runAutomationNow(rule) },
                    onDelete = { vm.deleteAutomation(rule) }
                )
            }
        }
        item { SectionHeader(stringResource(R.string.automation_history), stringResource(R.string.automation_history_desc)) }
        if (history.isEmpty()) {
            item { Text(stringResource(R.string.automation_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(history.take(30), key = { "log-${it.id}" }) { log -> AutomationHistoryRow(log) }
            item {
                TextButton(onClick = vm::undoLastAutomation, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.automation_undo_latest))
                }
            }
        }
    }
}

@Composable
private fun AutomationRuleCard(
    rule: AutomationRuleEntity,
    onEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    OrdiaCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    Text(rule.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onEnabled,
                    modifier = Modifier.semantics { contentDescription = rule.name }
                )
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.automation_delete)) }
            }
            Text(
                "${rule.trigger.name} · ${rule.condition.name} · ${rule.action.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.automation_limits, rule.frequencyMinutes, rule.maxRunsPerDay),
                style = MaterialTheme.typography.labelSmall
            )
            if (rule.lastError.isNotBlank()) {
                Text(rule.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrdiaOutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Science, null)
                    Text(stringResource(R.string.automation_test), Modifier.padding(start = 6.dp))
                }
                OrdiaButton(onClick = onRun, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Text(stringResource(R.string.automation_run), Modifier.padding(start = 6.dp))
                }
            }
            Text(
                stringResource(R.string.automation_last_result, rule.lastResult.name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AutomationHistoryRow(log: AutomationLogEntity) {
    OrdiaCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(log.description.ifBlank { log.type }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${DateRules.formatDate(log.createdAt)} · ${DateRules.formatTime(log.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                when {
                    log.undone -> stringResource(R.string.automation_status_undone)
                    log.type.startsWith("test:") -> stringResource(R.string.automation_status_test)
                    else -> stringResource(R.string.automation_status_done)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
