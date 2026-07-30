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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ordia.app.BuildConfig
import com.ordia.app.backup.BackupSecurityRules
import com.ordia.app.OrdiaApplication
import com.ordia.app.data.preferences.GuardianMode
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.data.preferences.ThemeMode
import com.ordia.app.domain.DateRules
import com.ordia.app.overlay.GuardianOverlayService
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.InfoBanner
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.updates.OrdiaUpdateManager
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Composable
fun SettingsScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val app = context.applicationContext as OrdiaApplication
    val repository = app.container.preferencesRepository
    val scope = rememberCoroutineScope()
    var backupJson by remember { mutableStateOf<String?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var quietStart by remember(state.preferences.quietStartMinutes) { mutableStateOf(DateRules.minutesToClock(state.preferences.quietStartMinutes)) }
    var quietEnd by remember(state.preferences.quietEndMinutes) { mutableStateOf(DateRules.minutesToClock(state.preferences.quietEndMinutes)) }
    var guardianName by remember(state.preferences.guardianName) { mutableStateOf(state.preferences.guardianName) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var guardianStatus by remember { mutableStateOf<String?>(null) }
    var quietStatus by remember { mutableStateOf<String?>(null) }
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = backupJson
        when {
            uri == null -> backupStatus = "Creación de copia cancelada."
            json == null -> backupStatus = "No había datos preparados para exportar."
            else -> runCatching {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: error("Android no permitió escribir el archivo.")
                stream.bufferedWriter().use { it.write(json) }
            }.onSuccess {
                backupStatus = "Copia guardada correctamente."
            }.onFailure {
                backupStatus = "No se pudo guardar la copia: ${it.message ?: "error de escritura"}"
            }
        }
        backupJson = null
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { readUtf8Limited(it, BackupSecurityRules.MAX_UTF8_BYTES) }
        }.onSuccess { raw ->
            if (raw != null) pendingRestoreJson = raw
        }.onFailure {
            backupStatus = "No se pudo leer la copia: ${it.message ?: "archivo inválido"}"
        }
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = Settings.canDrawOverlays(context)
        if (granted && startGuardian(context)) {
            vm.setGuardianEnabled(true)
            guardianStatus = "Guardián flotante activado."
        } else {
            vm.setGuardianEnabled(false)
            guardianStatus = if (granted) "Android no permitió iniciar el guardián." else "Permiso de superposición no concedido."
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    pendingRestoreJson?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("¿Restaurar esta copia?") },
            text = { Text("La restauración reemplazará todas las tareas, notas, proyectos, hábitos, rutinas y ajustes actuales. Crea una copia reciente antes de continuar.") },
            confirmButton = {
                Button(onClick = {
                    pendingRestoreJson = null
                    backupStatus = "Restaurando la copia…"
                    vm.importBackup(raw)
                }) { Text("Reemplazar y restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("A TU MANERA", "Ajustes", "Personaliza Ordia 2.0, tu guardián y las actualizaciones sin perder datos.") }

        item { SectionHeader("Apariencia") }
        item {
            SettingsCard(Icons.Outlined.DarkMode, "Tema") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries) { mode ->
                        FilterChip(selected = state.preferences.themeMode == mode, onClick = { vm.setThemeMode(mode) }, label = { Text(mode.label()) })
                    }
                }
            }
        }
        item {
            SettingsCard(null, "Nivel de interfaz") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.preferences.interfaceMode == mode,
                            onClick = { vm.setInterfaceMode(mode) },
                            label = { Text(mode.label()) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item { SettingSwitch("Navegación compacta", "Usa la barra inferior incluso en tabletas.", state.preferences.compactNavigation, vm::setCompactNavigation) }
        item { SettingSwitch("Reducir movimiento", "Evita animaciones no esenciales.", state.preferences.reduceMotion, vm::setReduceMotion) }

        item { SectionHeader("Guardián virtual") }
        item {
            InfoBanner(
                "Un compañero, no una obligación",
                "El guardián reacciona a tus avances, pero nunca enferma ni pierde progreso cuando descansas. No lee el contenido de otras aplicaciones."
            )
        }
        item {
            SettingSwitch(
                "Activar guardián flotante",
                if (Settings.canDrawOverlays(context)) "Puede acompañarte sobre otras aplicaciones." else "Primero debes autorizar la superposición.",
                state.preferences.guardianEnabled
            ) { enabled ->
                if (enabled && !Settings.canDrawOverlays(context)) {
                    overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    if (enabled) {
                        val started = startGuardian(context)
                        vm.setGuardianEnabled(started)
                        guardianStatus = if (started) "Guardián flotante activado." else "Android no permitió iniciar el guardián."
                    } else {
                        vm.setGuardianEnabled(false)
                        context.stopService(Intent(context, GuardianOverlayService::class.java))
                        guardianStatus = "Guardián flotante desactivado."
                    }
                }
            }
        }
        guardianStatus?.let { status ->
            item { Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            SettingsCard(Icons.Outlined.Shield, "Presencia") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GuardianMode.entries) { mode ->
                        FilterChip(selected = state.preferences.guardianMode == mode, onClick = { vm.setGuardianMode(mode) }, label = { Text(mode.label()) })
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Outlined.AutoAwesome, "Identidad y dinámica") {
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it.take(24) },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { scope.launch { repository.setGuardianName(guardianName) } },
                    enabled = guardianName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar nombre") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GuardianSpecies.entries) { species ->
                        FilterChip(
                            selected = state.preferences.guardianSpecies == species,
                            onClick = { scope.launch { repository.setGuardianSpecies(species) } },
                            label = { Text(species.label) }
                        )
                    }
                }
                Text(
                    state.preferences.guardianSpecies.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingSwitch(
                "Animaciones del guardián",
                "Permite movimiento, parpadeo, reacciones y efectos de evolución.",
                state.preferences.guardianAnimations
            ) { scope.launch { repository.setGuardianAnimations(it) } }
        }
        item {
            SettingsCard(null, "Horas de silencio") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(quietStart, { quietStart = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("Desde") }, singleLine = true)
                    OutlinedTextField(quietEnd, { quietEnd = it.take(5) }, modifier = Modifier.weight(1f), label = { Text("Hasta") }, singleLine = true)
                    Button(onClick = {
                        val start = parseClock(quietStart)
                        val end = parseClock(quietEnd)
                        when {
                            start == null || end == null -> quietStatus = "Usa el formato HH:mm, por ejemplo 22:00."
                            start == end -> quietStatus = "La hora inicial y final deben ser diferentes."
                            else -> {
                                vm.setQuietHours(start, end)
                                quietStatus = "Horario de silencio guardado."
                            }
                        }
                    }) { Text("Guardar") }
                }
                quietStatus?.let { status ->
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (BuildConfig.SELF_UPDATE_ENABLED) {
        item { SectionHeader("Actualizaciones automáticas") }
        item {
            InfoBanner(
                "Actualización segura",
                "Ordia comprueba GitHub Releases, valida el SHA-256, el paquete y la firma de la APK. Android siempre exige un toque final para autorizar la instalación."
            )
        }
        item {
            SettingSwitch(
                "Buscar versiones automáticamente",
                "Comprueba dos veces al día si existe una compilación más reciente.",
                state.preferences.autoUpdateEnabled
            ) { enabled ->
                scope.launch {
                    repository.setAutoUpdateEnabled(enabled)
                    if (BuildConfig.SELF_UPDATE_ENABLED && enabled) OrdiaUpdateManager.schedule(context)
                    else OrdiaUpdateManager.cancelSchedule(context)
                }
            }
        }
        item {
            SettingSwitch(
                "Descargar actualizaciones",
                "Descarga la APK nueva y te avisa cuando esté lista para instalar.",
                state.preferences.autoDownloadUpdates
            ) { scope.launch { repository.setAutoDownloadUpdates(it) } }
        }
        item {
            SettingsCard(Icons.Outlined.SystemUpdate, "Comprobar ahora") {
                Text(
                    updateStatus ?: "Versión instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        updateStatus = "Buscando una versión nueva…"
                        scope.launch {
                            when (val result = OrdiaUpdateManager.checkDetailed()) {
                                OrdiaUpdateManager.CheckResult.UpToDate -> {
                                    updateStatus = "Ordia está actualizada."
                                }
                                is OrdiaUpdateManager.CheckResult.Failed -> {
                                    updateStatus = "No se pudo comprobar: ${result.reason}"
                                }
                                is OrdiaUpdateManager.CheckResult.Available -> {
                                    val release = result.release
                                    if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                                        updateStatus = "Activa las notificaciones para recibir el aviso de instalación verificada."
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val id = OrdiaUpdateManager.download(context, release, allowMetered = true, userInitiated = true)
                                        updateStatus = if (id != null) "Descargando ${release.tag}."
                                        else "No se pudo iniciar la descarga verificada."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Buscar actualización") }
            }
        }

        }

        item { SectionHeader("Planificación") }
        item {
            SettingsCard(Icons.Outlined.Notifications, "Notificaciones y recordatorios") {
                Text(
                    if (notificationsGranted) "Permiso activo. Ordia puede mostrar recordatorios y avisos de actualización."
                    else "Activa el permiso para recibir recordatorios y avisos.",
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(15, 25, 45, 60)) { value ->
                        FilterChip(selected = state.preferences.defaultFocusMinutes == value, onClick = { vm.setDefaultFocusMinutes(value) }, label = { Text("$value min") })
                    }
                }
            }
        }

        item { SectionHeader("Tus datos") }
        item {
            SettingsCard(Icons.Outlined.Backup, "Copia de seguridad") {
                Text("Exporta tareas, proyectos, notas, hábitos, rutinas, sesiones, ajustes y progreso del guardián en un archivo local sin cifrar. Los adjuntos se guardan como referencias, no como copias de sus archivos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            Text(
                "Ordia ${BuildConfig.VERSION_NAME} · Local primero · Sin cuenta obligatoria",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
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
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

private fun startGuardian(context: Context): Boolean = runCatching {
    ContextCompat.startForegroundService(context, Intent(context, GuardianOverlayService::class.java))
    true
}.getOrDefault(false)

private fun readUtf8Limited(input: java.io.InputStream, maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        require(output.size() + read <= maxBytes) { "La copia supera el límite de 10 MB." }
        output.write(buffer, 0, read)
    }
    require(output.size() > 1) { "La copia está vacía." }
    return BackupSecurityRules.decodeUtf8Strict(output.toByteArray())
        ?: error("La copia no está codificada en UTF-8 válido.")
}
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
