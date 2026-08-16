package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ordia.app.BuildConfig
import com.ordia.app.R
import com.ordia.app.backup.BackupSecurityRules
import com.ordia.app.OrdiaApplication
import com.ordia.app.data.preferences.GuardianMode
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.data.preferences.ThemeMode
import com.ordia.app.domain.DateRules
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.BackupRestoreState
import com.ordia.app.ui.components.InfoBanner
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.descriptionRes
import com.ordia.app.ui.labelRes
import com.ordia.app.overlay.GuardianOverlayService
import com.ordia.app.ui.components.SectionHeader
import com.ordia.app.updates.OrdiaUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@SuppressLint("LocalContextGetResourceValueCall")
// Los getString de Ajustes viven en callbacks de launchers (CreateDocument/OpenDocument/
// RequestPermission) y corrutinas, ámbitos no-componibles donde stringResource no aplica.
@Composable
fun SettingsScreen(state: OrdiaUiState, vm: OrdiaViewModel, contentPadding: PaddingValues, onUpdates: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OrdiaApplication
    val repository = app.container.preferencesRepository
    val scope = rememberCoroutineScope()
    var backupJson by remember { mutableStateOf<String?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var restoreFileName by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val backupRestoreState by vm.backupState.collectAsState()
    var quietStart by remember(state.preferences.quietStartMinutes) { mutableStateOf(DateRules.minutesToClock(state.preferences.quietStartMinutes)) }
    var quietEnd by remember(state.preferences.quietEndMinutes) { mutableStateOf(DateRules.minutesToClock(state.preferences.quietEndMinutes)) }
    var guardianName by remember(state.preferences.guardianName) { mutableStateOf(state.preferences.guardianName) }
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
            uri == null -> backupStatus = context.getString(R.string.settings_backup_cancelled)
            json == null -> backupStatus = context.getString(R.string.settings_backup_no_data)
            else -> scope.launch(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error(context.getString(R.string.settings_backup_error_android_write))
                    stream.bufferedWriter().use { it.write(json) }
                }.onSuccess {
                    backupStatus = context.getString(R.string.settings_backup_saved)
                }.onFailure {
                    backupStatus = context.getString(R.string.settings_backup_save_failed, it.message ?: context.getString(R.string.settings_backup_error_write))
                }
            }
        }
        backupJson = null
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // No permitir seleccionar otra copia mientras hay una restauración en curso.
        if (vm.backupState.value.inProgress) {
            backupStatus = context.getString(R.string.settings_backup_in_progress)
            return@rememberLauncherForActivityResult
        }
        // Leer hasta 10 MB fuera del hilo principal (la UI no debe bloquearse).
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { readUtf8Limited(context, it, BackupSecurityRules.MAX_UTF8_BYTES) }
            }.onSuccess { raw ->
                if (raw != null) {
                    if (raw.isBlank()) {
                        backupStatus = context.getString(R.string.settings_backup_error_empty)
                    } else {
                        pendingRestoreJson = raw
                        restoreFileName = uri.lastPathSegment ?: context.getString(R.string.settings_backup_default_filename)
                    }
                } else {
                    backupStatus = context.getString(R.string.settings_backup_error_invalid)
                }
            }.onFailure {
                backupStatus = context.getString(R.string.settings_backup_read_failed, it.message ?: context.getString(R.string.settings_backup_error_invalid))
            }
        }
    }
    val overlayPermission = if (BuildConfig.OVERLAY_ENABLED) {
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = Settings.canDrawOverlays(context)
            if (granted && startGuardian(context)) {
                vm.setGuardianEnabled(true)
                guardianStatus = context.getString(R.string.settings_guardian_float_active)
            } else {
                vm.setGuardianEnabled(false)
                guardianStatus = if (granted) context.getString(R.string.settings_guardian_float_blocked) else context.getString(R.string.settings_guardian_overlay_not_granted)
            }
        }
    } else null
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    pendingRestoreJson?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text(stringResource(R.string.settings_restore_dialog_title)) },
            text = { Text(stringResource(R.string.settings_restore_dialog_text)) },
            confirmButton = {
                OrdiaButton(onClick = {
                    val name = restoreFileName
                    pendingRestoreJson = null
                    restoreFileName = null
                    vm.restoreBackup(raw, name)
                }) { Text(stringResource(R.string.settings_restore_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    when (val restoreResult = backupRestoreState) {
        is BackupRestoreState.Success -> AlertDialog(
            onDismissRequest = { vm.dismissRestoreResult() },
            title = { Text(stringResource(R.string.settings_restore_success_title)) },
            text = { Text(restoreResult.message) },
            confirmButton = {
                OrdiaButton(onClick = { vm.dismissRestoreResult() }) { Text(stringResource(R.string.action_understood)) }
            }
        )
        is BackupRestoreState.Error -> AlertDialog(
            onDismissRequest = { vm.dismissRestoreResult() },
            title = { Text(stringResource(R.string.settings_restore_error_title)) },
            text = { Text(restoreResult.message) },
            confirmButton = {
                OrdiaButton(onClick = { vm.dismissRestoreResult() }) { Text(stringResource(R.string.action_understood)) }
            }
        )
        else -> Unit
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, contentPadding.calculateTopPadding() + 20.dp, 20.dp, contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader(stringResource(R.string.settings_header_eyebrow), stringResource(R.string.settings_header_title), stringResource(R.string.settings_header_subtitle)) }

        @Suppress("UNUSED_EXPRESSION")
        if (BuildConfig.PREVIEW) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (BuildConfig.OVERLAY_ENABLED) stringResource(R.string.settings_preview_full) else stringResource(R.string.settings_preview),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(
                                R.string.settings_preview_version,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                                if (BuildConfig.DEBUG) stringResource(R.string.settings_preview_build_debug) else stringResource(R.string.settings_preview_build_release)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_preview_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (!BuildConfig.OVERLAY_ENABLED) {
                            Text(
                                stringResource(R.string.settings_preview_limits),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
        item {
            SettingsCard(Icons.Outlined.DarkMode, stringResource(R.string.settings_card_theme)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ThemeMode.entries) { mode ->
                        FilterChip(selected = state.preferences.themeMode == mode, onClick = { vm.setThemeMode(mode) }, label = { Text(stringResource(mode.labelRes())) })
                    }
                }
            }
        }
        item {
            SettingsCard(null, stringResource(R.string.settings_card_interface_level)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.preferences.interfaceMode == mode,
                            onClick = { vm.setInterfaceMode(mode) },
                            label = { Text(stringResource(mode.labelRes())) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item { SettingSwitch(stringResource(R.string.settings_compact_nav), stringResource(R.string.settings_compact_nav_desc), state.preferences.compactNavigation, vm::setCompactNavigation) }
        item {
            SettingSwitch(
                stringResource(R.string.settings_show_floating_capture),
                stringResource(R.string.settings_show_floating_capture_desc),
                state.preferences.showFloatingCapture
            ) { enabled -> scope.launch { repository.setShowFloatingCapture(enabled) } }
        }
        item { SettingSwitch(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_desc), state.preferences.reduceMotion, vm::setReduceMotion) }

        item { SectionHeader(stringResource(R.string.settings_section_guardian)) }
        item {
            InfoBanner(
                stringResource(R.string.settings_guardian_banner_title),
                stringResource(R.string.settings_guardian_banner_text)
            )
        }
        if (BuildConfig.OVERLAY_ENABLED) {
            item {
                SettingSwitch(
                    stringResource(R.string.settings_guardian_float_enable),
                    if (Settings.canDrawOverlays(context)) stringResource(R.string.settings_guardian_float_available) else stringResource(R.string.settings_guardian_float_need_overlay),
                    state.preferences.guardianEnabled
                ) { enabled ->
                    if (enabled && !Settings.canDrawOverlays(context)) {
                        overlayPermission!!.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    } else {
                        if (enabled) {
                            val started = startGuardian(context)
                            vm.setGuardianEnabled(started)
                            guardianStatus = if (started) context.getString(R.string.settings_guardian_float_active) else context.getString(R.string.settings_guardian_float_blocked)
                        } else {
                            vm.setGuardianEnabled(false)
                            val stopIntent = android.content.Intent(context, com.ordia.app.overlay.GuardianOverlayService::class.java)
                            context.stopService(stopIntent)
                            guardianStatus = context.getString(R.string.settings_guardian_float_disabled)
                        }
                    }
                }
            }
            guardianStatus?.let { status ->
                item { Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            item {
                Card {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.settings_guardian_unavailable_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_preview_limits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Outlined.Shield, stringResource(R.string.settings_card_presence)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GuardianMode.entries) { mode ->
                        FilterChip(selected = state.preferences.guardianMode == mode, onClick = { vm.setGuardianMode(mode) }, label = { Text(stringResource(mode.labelRes())) })
                    }
                }
            }
        }
        item {
            SettingsCard(Icons.Outlined.AutoAwesome, stringResource(R.string.settings_card_identity)) {
                OrdiaInput(
                    value = guardianName,
                    onValueChange = { guardianName = it.take(24) },
                    label = { Text(stringResource(R.string.settings_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OrdiaButton(
                    onClick = { scope.launch { repository.setGuardianName(guardianName) } },
                    enabled = guardianName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_save_name)) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GuardianSpecies.entries) { species ->
                        FilterChip(
                            selected = state.preferences.guardianSpecies == species,
                            onClick = { scope.launch { repository.setGuardianSpecies(species) } },
                            label = { Text(stringResource(species.labelRes())) }
                        )
                    }
                }
                Text(
                    stringResource(state.preferences.guardianSpecies.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingSwitch(
                stringResource(R.string.settings_guardian_animations),
                stringResource(R.string.settings_guardian_animations_desc),
                state.preferences.guardianAnimations
            ) { scope.launch { repository.setGuardianAnimations(it) } }
        }
        item {
            SettingSwitch(
                stringResource(R.string.settings_learning_switch),
                stringResource(R.string.settings_learning_desc),
                state.preferences.learningEnabled
            ) { scope.launch { repository.setLearningEnabled(it) } }
        }
        item {
            SettingsCard(null, stringResource(R.string.settings_card_quiet_hours)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrdiaInput(quietStart, { quietStart = it.take(5) }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.settings_quiet_from)) }, singleLine = true)
                    OrdiaInput(quietEnd, { quietEnd = it.take(5) }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.settings_quiet_to)) }, singleLine = true)
                    OrdiaButton(onClick = {
                        val start = parseClock(quietStart)
                        val end = parseClock(quietEnd)
                        when {
                            start == null || end == null -> quietStatus = context.getString(R.string.settings_quiet_format_hint)
                            start == end -> quietStatus = context.getString(R.string.settings_quiet_same_time)
                            else -> {
                                vm.setQuietHours(start, end)
                                quietStatus = context.getString(R.string.settings_quiet_saved)
                            }
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                }
                quietStatus?.let { status ->
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (BuildConfig.SELF_UPDATE_ENABLED) {
        item { SectionHeader(stringResource(R.string.settings_section_updates)) }
        item {
            InfoBanner(
                stringResource(R.string.settings_updates_banner_title),
                stringResource(R.string.settings_updates_banner_text)
            )
        }
        item {
            SettingSwitch(
                stringResource(R.string.settings_updates_auto_check),
                stringResource(R.string.settings_updates_auto_check_desc),
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
                stringResource(R.string.settings_updates_auto_download),
                stringResource(R.string.settings_updates_auto_download_desc),
                state.preferences.autoDownloadUpdates
            ) { scope.launch { repository.setAutoDownloadUpdates(it) } }
        }
        item {
            SettingsCard(Icons.Outlined.SystemUpdate, stringResource(R.string.settings_card_check_now)) {
                Text(
                    stringResource(R.string.settings_update_installed_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OrdiaButton(
                    onClick = onUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_update_check_button)) }
            }
        }

        }

        item { SectionHeader(stringResource(R.string.settings_section_planning)) }
        item {
            SettingsCard(Icons.Outlined.Notifications, stringResource(R.string.settings_card_notifications)) {
                Text(
                    if (notificationsGranted) stringResource(R.string.settings_notifications_active)
                    else stringResource(R.string.settings_notifications_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!notificationsGranted && Build.VERSION.SDK_INT >= 33) {
                    OrdiaButton(
                        onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text(stringResource(R.string.settings_notifications_allow), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item { SettingSwitch(stringResource(R.string.settings_week_starts_monday), stringResource(R.string.settings_week_starts_monday_desc), state.preferences.weekStartsMonday, vm::setWeekStartsMonday) }
        item {
            SettingsCard(null, stringResource(R.string.settings_card_focus_duration)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(15, 25, 45, 60)) { value ->
                        FilterChip(selected = state.preferences.defaultFocusMinutes == value, onClick = { vm.setDefaultFocusMinutes(value) }, label = { Text(stringResource(R.string.settings_focus_minutes, value)) })
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_data)) }
        item {
            SettingsCard(Icons.Outlined.Backup, stringResource(R.string.settings_card_backup)) {
                Text(stringResource(R.string.settings_backup_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                when (val restoreProgress = backupRestoreState) {
                    is BackupRestoreState.FileSelected -> ProgressLine(stringResource(R.string.settings_backup_progress_reading))
                    is BackupRestoreState.Validating -> ProgressLine(stringResource(R.string.settings_backup_progress_validating))
                    is BackupRestoreState.CreatingSafetyBackup -> ProgressLine(stringResource(R.string.settings_backup_progress_safety))
                    is BackupRestoreState.Restoring -> ProgressLine(stringResource(R.string.settings_backup_progress_restoring))
                    is BackupRestoreState.Verifying -> ProgressLine(stringResource(R.string.settings_backup_progress_verifying))
                    else -> Unit
                }
                OrdiaButton(onClick = {
                    vm.exportBackup { json ->
                        backupJson = json
                        createBackup.launch("Ordía-${LocalDate.now()}.ordia.json")
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Backup, null)
                    Text(stringResource(R.string.settings_backup_create_button), Modifier.padding(start = 8.dp))
                }
                OrdiaOutlinedButton(
                    onClick = { openBackup.launch(arrayOf("application/json", "text/plain")) },
                    enabled = !backupRestoreState.inProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Restore, null)
                    Text(stringResource(R.string.settings_backup_restore_button), Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            val previewLabel = if (BuildConfig.PREVIEW) {
                (if (BuildConfig.OVERLAY_ENABLED) stringResource(R.string.settings_preview_full) else stringResource(R.string.settings_preview)) + " · "
            } else null
            val footerLocalFirst = stringResource(R.string.settings_footer_local_first)
            Text(
                buildString {
                    if (previewLabel != null) append(previewLabel)
                    append("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    append(footerLocalFirst)
                },
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

/** Línea de progreso del flujo de restauración (Fase 4). */
@Composable
private fun ProgressLine(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                modifier = Modifier.semantics {
                    contentDescription = if (subtitle.isBlank()) title else "$title. $subtitle"
                }
            )
        }
    }
}

private fun startGuardian(context: Context): Boolean = runCatching {
    ContextCompat.startForegroundService(context, Intent(context, GuardianOverlayService::class.java))
    true
}.getOrDefault(false)

private fun readUtf8Limited(context: Context, input: java.io.InputStream, maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        require(output.size() + read <= maxBytes) { context.getString(R.string.settings_backup_error_limit) }
        output.write(buffer, 0, read)
    }
    require(output.size() > 1) { context.getString(R.string.settings_backup_error_empty) }
    return BackupSecurityRules.decodeUtf8Strict(output.toByteArray())
        ?: error(context.getString(R.string.settings_backup_error_utf8))
}
private fun parseClock(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
private fun ThemeMode.labelRes(): Int = when (this) { ThemeMode.SYSTEM -> R.string.settings_theme_system; ThemeMode.LIGHT -> R.string.settings_theme_light; ThemeMode.DARK -> R.string.settings_theme_dark }
private fun InterfaceMode.labelRes(): Int = when (this) { InterfaceMode.SIMPLE -> R.string.settings_interface_simple; InterfaceMode.ORGANIZED -> R.string.settings_interface_organized; InterfaceMode.ADVANCED -> R.string.settings_interface_advanced }
private fun GuardianMode.labelRes(): Int = when (this) { GuardianMode.DORMANT -> R.string.settings_guardian_mode_dormant; GuardianMode.DISCREET -> R.string.settings_guardian_mode_discreet; GuardianMode.COMPANION -> R.string.settings_guardian_mode_companion }
