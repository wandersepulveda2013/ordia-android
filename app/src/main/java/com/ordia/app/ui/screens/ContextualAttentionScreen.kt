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
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ordia.app.BuildConfig
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
            item { ScreenHeader("PRIVADO Y OPCIONAL", "Atención contextual", "Ordia detecta posibles compromisos localmente y siempre pide confirmación.") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Lectura de notificaciones no disponible", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Esta Preview segura no incluye lectura de notificaciones. " +
                            "Puedes seguir usando el análisis de texto compartido o seleccionado manualmente. " +
                            "La lectura de notificaciones está disponible en la compilación avanzada separada.",
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
        item { ScreenHeader("PRIVADO Y OPCIONAL", "Atención contextual", "Ordia detecta posibles compromisos localmente y siempre pide confirmación.") }
        item {
            SettingRow("Procesamiento contextual", "Permite analizar texto compartido o seleccionado.", enabled) {
                enabled = it; settings.enabled = it
            }
        }
        item {
            SettingRow("Sugerencias desde notificaciones", "Solo procesa el texto visible de notificaciones autorizadas; nunca guarda la conversación completa.", notifications) {
                notifications = it; settings.notificationSuggestions = it
            }
        }
        item { SectionHeader("Aplicaciones autorizadas", "Aunque Android conceda acceso global, Ordia solo procesa las aplicaciones que actives aquí.") }
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
                    Text(if (listenerGranted) "Acceso a notificaciones autorizado" else "Acceso a notificaciones no autorizado", style = MaterialTheme.typography.titleMedium)
                    Text("Ordia funciona manualmente sin este permiso. Actívalo solo si deseas sugerencias en la bandeja contextual.")
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("Abrir permisos de notificaciones")
                    }
                    OutlinedButton(onClick = { settings.pauseOneHour() }) { Text("Pausar durante una hora") }
                    OutlinedButton(onClick = { store.clear(); refresh() }) { Text("Borrar sugerencias") }
                }
            }
        }
        item { SectionHeader("Sugerencias pendientes", "Solo se guardan título, tipo, fecha estimada y una huella no reversible.") }
        if (suggestions.isEmpty()) {
            item { Text("No hay sugerencias. Selecciona texto y usa ‘Procesar texto con Ordia’, o comparte un mensaje con la aplicación.") }
        } else {
            items(suggestions, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("${item.kind.name.lowercase()} · confianza ${(item.confidence * 100).toInt()} %")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { accept(item) }) { Text("Añadir") }
                            OutlinedButton(onClick = { store.remove(item.id); refresh() }) { Text("Descartar") }
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
