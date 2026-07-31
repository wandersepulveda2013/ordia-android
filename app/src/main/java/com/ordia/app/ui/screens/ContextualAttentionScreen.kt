package com.ordia.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ordia.app.BuildConfig
import com.ordia.app.R
import com.ordia.app.OrdiaApplication
import com.ordia.app.context.ContextualKind
import com.ordia.app.context.ContextualSuggestion
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader

@Composable
fun ContextualAttentionScreen(state: OrdiaUiState, vm: OrdiaViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    if (!BuildConfig.CONTEXT_NOTIFICATION_ACCESS_ENABLED) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 20.dp, 20.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ScreenHeader(stringResource(R.string.attention_eyebrow), stringResource(R.string.attention_title), stringResource(R.string.attention_subtitle)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.attention_reading_unavailable_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.attention_reading_unavailable_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        return
    }

    val app = context.applicationContext as OrdiaApplication
    val settings = remember { app.container.contextualSettingsStore }
    val store = remember { app.container.contextualSuggestionStore }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var notifications by remember { mutableStateOf(settings.notificationSuggestions) }
    var suggestions by remember { mutableStateOf(store.list()) }
    var allowedPackages by remember { mutableStateOf(settings.allowedPackages()) }
    val listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun refresh() { suggestions = store.list() }
    fun accept(item: ContextualSuggestion) {
        when (item.kind) {
            ContextualKind.NOTE -> vm.addNote(item.title, "Creada desde una sugerencia contextual confirmada.")
            ContextualKind.TASK, ContextualKind.EVENT, ContextualKind.STUDY -> vm.addTask(
                title = item.title,
                details = "Sugerencia contextual confirmada por el usuario.",
                dueAt = item.dueAt,
                priority = TaskPriority.NORMAL
            )
        }
        store.remove(item.id)
        refresh()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 20.dp, 20.dp, padding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.attention_eyebrow), stringResource(R.string.attention_title), stringResource(R.string.attention_subtitle)) }
        item {
            SettingRow(stringResource(R.string.attention_processing_setting), stringResource(R.string.attention_processing_setting_desc), enabled) {
                enabled = it; settings.enabled = it
            }
        }
        item {
            SettingRow(stringResource(R.string.attention_notifications_setting), stringResource(R.string.attention_notifications_setting_desc), notifications) {
                notifications = it; settings.notificationSuggestions = it
            }
        }
        item { SectionHeader(stringResource(R.string.attention_allowed_apps), stringResource(R.string.attention_allowed_apps_desc)) }
        items(CONTEXT_APPS, key = { it.packageName }) { appOption ->
            val checked = appOption.packageName in allowedPackages
            SettingRow(appOption.label, appOption.packageName, checked) { value ->
                settings.setPackageAllowed(appOption.packageName, value)
                allowedPackages = settings.allowedPackages()
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (listenerGranted) stringResource(R.string.attention_access_granted) else stringResource(R.string.attention_access_not_granted), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.attention_access_body))
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text(stringResource(R.string.attention_open_permissions))
                    }
                    OutlinedButton(onClick = { settings.pauseOneHour() }) { Text(stringResource(R.string.attention_pause_one_hour)) }
                    OutlinedButton(onClick = { store.clear(); refresh() }) { Text(stringResource(R.string.attention_clear_suggestions)) }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.attention_pending_suggestions), stringResource(R.string.attention_pending_suggestions_desc)) }
        if (suggestions.isEmpty()) {
            item { Text(stringResource(R.string.attention_no_suggestions)) }
        } else {
            items(suggestions, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.attention_confidence, item.kind.name.lowercase(), (item.confidence * 100).toInt()))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { accept(item) }) { Text(stringResource(R.string.action_add)) }
                            OutlinedButton(onClick = { store.remove(item.id); refresh() }) { Text(stringResource(R.string.attention_discard)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private data class ContextApp(val label: String, val packageName: String)
private val CONTEXT_APPS = listOf(
    ContextApp("WhatsApp", "com.whatsapp"),
    ContextApp("WhatsApp Business", "com.whatsapp.w4b"),
    ContextApp("Telegram", "org.telegram.messenger"),
    ContextApp("Signal", "org.thoughtcrime.securesms")
)
