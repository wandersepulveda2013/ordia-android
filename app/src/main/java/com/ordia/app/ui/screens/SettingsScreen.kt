package com.ordia.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ordia.app.data.preferences.GuardianMode
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.data.preferences.ThemeMode
import com.ordia.app.domain.DateRules
import com.ordia.app.overlay.GuardianOverlayService
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.InfoBanner
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.update.UpdateResult
import java.time.LocalDate

@Composable
fun SettingsScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    var backupJson by remember { mutableStateOf<String?>(null) }
    var quietStart by remember(state.preferences.quietStartMinutes) { mutableStateOf(DateRules.minutesToClock(state.preferences.quietStartMinutes)) }
    var quietEnd by remember(state.preferences.quietEndMinutes) { mutableStateOf(DateRules.minutesToClock(state.preferences.quietEndMinutes)) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = backupJson
        if (uri != null && json != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } }
        backupJson = null
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            .onSuccess { raw -> if (raw != null) vm.importBackup(raw) }
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = Settings.canDrawOverlays(context)
        vm.setGuardianEnabled(granted)
        if (granted) startGuardian(context)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("A TU MANERA", "Ajustes", "Cambia la profundidad de Ordia sin perder tus datos.") }
        item { SectionHeader("Apariencia") }
        item {
            SettingsCard(Icons.Outlined.DarkMode, "Tema") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode -> FilterChip(selected = state.preferences.themeMode == mode, onClick = { vm.setThemeMode(mode) }, label = { Text(mode.label()) }) }
                }
            }
        }
        item {
            SettingsCard(null, "Nivel de interfaz") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceMode.entries.forEach { mode ->
                        FilterChip(selected = state.preferences.interfaceMode == mode, onClick = { vm.setInterfaceMode(mode) }, label = { Text(mode.label()) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item {
            SettingsCard(null, "Color de acento") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.ordia.app.data.preferences.AccentPalette.entries.forEach { palette ->
                        val swatch = com.ordia.app.ui.theme.accentSwatches.getValue(palette)
                        val selected = state.preferences.accentPalette == palette
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = swatch.lightSecondary,
                            border = androidx.compose.foundation.BorderStroke(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { vm.setAccentPalette(palette) }
                        ) {
                            if (palette == com.ordia.app.data.preferences.AccentPalette.SYSTEM) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Outlined.DarkMode,
                                        "Sistema",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    state.preferences.accentPalette.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { SettingSwitch("Navegación compacta", "Usa la barra inferior incluso en tabletas.", state.preferences.compactNavigation, vm::setCompactNavigation) }
        item { SettingSwitch("Reducir movimiento", "Evita animaciones no esenciales.", state.preferences.reduceMotion, vm::setReduceMotion) }

        item { SectionHeader("Guardián flotante") }
        item {
            InfoBanner(
                "Controlado por ti",
                "El guardián no lee otras aplicaciones. Solo muestra sus propios controles y guarda lo que tú escribes o dictas."
            )
        }
        item {
            SettingSwitch(
                "Activar guardián",
                if (Settings.canDrawOverlays(context)) "Disponible sobre otras aplicaciones." else "Primero debes autorizar la superposición.",
                state.preferences.guardianEnabled
            ) { enabled ->
                if (enabled && !Settings.canDrawOverlays(context)) {
                    overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    vm.setGuardianEnabled(enabled)
                    if (enabled) {
                        startGuardian(context)
                    } else {
                        val intent = Intent(context, GuardianOverlayService::class.java)
                        context.stopService(intent)
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Outlined.Shield, "Comportamiento") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuardianMode.entries.forEach { mode -> FilterChip(selected = state.preferences.guardianMode == mode, onClick = { vm.setGuardianMode(mode) }, label = { Text(mode.label()) }) }
                }
            }
        }
        item {
            SettingsCard(null, "Horas de silencio") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(quietStart, { quietStart = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("Desde") }, singleLine = true)
                    OutlinedTextField(quietEnd, { quietEnd = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("Hasta") }, singleLine = true)
                    Button(onClick = { parseClock(quietStart)?.let { start -> parseClock(quietEnd)?.let { end -> vm.setQuietHours(start, end) } } }) { Text("Guardar") }
                }
            }
        }

        item { SectionHeader("Planificación") }
        item {
            SettingsCard(Icons.Outlined.Notifications, "Notificaciones y recordatorios") {
                Text(
                    if (notificationsGranted) "Permiso activo. Ordia puede mostrar recordatorios programados."
                    else "Activa el permiso cuando quieras recibir avisos de tareas y rutinas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                    Button(
                        onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text("Permitir notificaciones", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { SettingSwitch("La semana empieza el lunes", "Afecta el planificador semanal.", state.preferences.weekStartsMonday, vm::setWeekStartsMonday) }
        item {
            SettingsCard(null, "Duración de enfoque predeterminada") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { value -> FilterChip(selected = state.preferences.defaultFocusMinutes == value, onClick = { vm.setDefaultFocusMinutes(value) }, label = { Text("$value min") }) }
                }
            }
        }

        item { SectionHeader("Actualizaciones") }
        item { UpdateSection(vm) }

        item { SectionHeader("Tus datos") }
        item {
            SettingsCard(Icons.Outlined.Backup, "Copia de seguridad") {
                Text("Exporta tareas, proyectos, notas, hábitos, rutinas y sesiones en un archivo local.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    vm.exportBackup { json ->
                        backupJson = json
                        createBackup.launch("Ordia-${LocalDate.now()}.ordia.json")
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Backup, null)
                    Text("Crear copia", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Restore, null)
                    Text("Restaurar copia", Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            Text("Ordia 3.0.1 · Local primero · Sin cuenta obligatoria", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateSection(vm: OrdiaViewModel) {
    val updateState by vm.updateState.collectAsState()
    val downloadProgress by (vm.downloadProgress?.collectAsState() ?: remember { mutableStateOf(0) })
    val isDownloading by (vm.isDownloading?.collectAsState() ?: remember { mutableStateOf(false) })

    LaunchedEffect(Unit) { vm.checkForUpdates() }

    SettingsCard(Icons.Outlined.SystemUpdate, "Versión instalada: ${com.ordia.app.BuildConfig.VERSION_NAME}") {
        when (val result = updateState) {
            is UpdateResult.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Buscando actualizaciones…", style = MaterialTheme.typography.bodyMedium)
            }
            is UpdateResult.UpToDate -> Text("Tienes la última versión.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is UpdateResult.Available -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nueva versión disponible: ${result.versionName}", style = MaterialTheme.typography.bodyMedium)
                Text(result.releaseNotes.take(300), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isDownloading) {
                    LinearProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Descargando… $downloadProgress%", style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(onClick = { vm.downloadAndInstallUpdate(result.apkDownloadUrl) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.SystemUpdate, null)
                        Text("Descargar e instalar", Modifier.padding(start = 8.dp))
                    }
                }
            }
            is UpdateResult.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No se pudo verificar: ${result.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { vm.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, null)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onChecked)
        }
    }
}

private fun startGuardian(context: Context) = ContextCompat.startForegroundService(context, Intent(context, GuardianOverlayService::class.java))
private fun parseClock(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
private fun ThemeMode.label() = when (this) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" }
private fun InterfaceMode.label() = when (this) { InterfaceMode.SIMPLE -> "Simple"; InterfaceMode.ORGANIZED -> "Organizado"; InterfaceMode.ADVANCED -> "Avanzado" }
private fun GuardianMode.label() = when (this) { GuardianMode.DORMANT -> "Dormido"; GuardianMode.DISCREET -> "Discreto"; GuardianMode.COMPANION -> "Compañero" }
private fun com.ordia.app.data.preferences.AccentPalette.label() = when (this) {
    com.ordia.app.data.preferences.AccentPalette.GOLD -> "Oro"
    com.ordia.app.data.preferences.AccentPalette.SAGE -> "Salvia"
    com.ordia.app.data.preferences.AccentPalette.ROSE -> "Rosa"
    com.ordia.app.data.preferences.AccentPalette.LAVENDER -> "Lavanda"
    com.ordia.app.data.preferences.AccentPalette.OCEAN -> "Océano"
    com.ordia.app.data.preferences.AccentPalette.TERRACOTTA -> "Terracota"
    com.ordia.app.data.preferences.AccentPalette.SYSTEM -> "Sistema (Material You)"
}
