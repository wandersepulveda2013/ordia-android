package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ordia.app.BuildConfig
import com.ordia.app.R
import com.ordia.app.ui.components.InfoBanner
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.updates.OrdiaUpdateController
import com.ordia.app.updates.OrdiaUpdateManager
import com.ordia.app.updates.OrdiaUpdateController.UpdateState
import java.text.DateFormat
import java.util.Date

/** Pantalla Ajustes → Actualizaciones: versión instalada, canal, estado, búsqueda, changelog y progreso. */
@Composable
fun UpdatesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val state by OrdiaUpdateController.state.collectAsStateWithLifecycle()
    val lastCheck = remember(state) { OrdiaUpdateController.lastCheckAt(context) }

    // Comprobar siempre al abrir la pantalla de actualizaciones, sin depender del
    // ajuste de auto-update: así el usuario obtiene un diagnóstico fresco y claro
    // (instalado vs remoto, disponible, al día o el motivo exacto del fallo).
    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle || state is UpdateState.Failed) {
            OrdiaUpdateController.checkNow(context)
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = stringResource(R.string.updates_screen_eyebrow),
                title = stringResource(R.string.updates_screen_title),
                subtitle = stringResource(R.string.updates_screen_subtitle)
            )
        }
        item {
            InfoBanner(
                stringResource(R.string.settings_updates_banner_title),
                stringResource(R.string.settings_updates_banner_text)
            )
        }
        item { InstalledCard(lastCheck) }
        val currentState = state
        when (currentState) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> item { StatusCard(stringResource(R.string.updates_checking), busy = true) }
            UpdateState.UpToDate -> item { StatusCard(stringResource(R.string.updates_uptodate), busy = false) }
            is UpdateState.Available -> item { AvailableCard(currentState) }
            is UpdateState.Downloading -> item { DownloadingCard(currentState) }
            is UpdateState.Ready -> item { ReadyCard(currentState) }
            UpdateState.Installing -> item { StatusCard(stringResource(R.string.updates_installing), busy = true) }
            UpdateState.Installed -> item { StatusCard(stringResource(R.string.updates_installed), busy = false) }
            is UpdateState.Failed -> item { FailedCard(currentState) }
        }
        item {
            Button(
                onClick = { OrdiaUpdateController.checkNow(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = currentState !is UpdateState.Checking && currentState !is UpdateState.Downloading
            ) { Text(stringResource(R.string.updates_check_button)) }
        }
    }
}

@Composable
private fun InstalledCard(lastCheck: Long) {
    val context = LocalContext.current
    val canInstall = remember { context.packageManager.canRequestPackageInstalls() }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.SystemUpdate, null)
                Text(stringResource(R.string.updates_installed_label), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.settings_update_installed_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.updates_channel_label) + ": " + BuildConfig.UPDATE_FLAVOR,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.updates_last_check_label) + ": " +
                    if (lastCheck > 0L) DateFormat.getDateTimeInstance().format(Date(lastCheck))
                    else stringResource(R.string.updates_last_check_never),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!canInstall) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.update_install_permission_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }) { Text(stringResource(R.string.update_install_permission_grant)) }
            } else {
                Text(
                    stringResource(R.string.update_install_permission_ok),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusCard(text: String, busy: Boolean) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AvailableCard(state: UpdateState.Available) {
    val context = LocalContext.current
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.updates_new_version), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.updates_version, state.release.tag),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.mandatory) {
                Text(
                    stringResource(R.string.updates_mandatory_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.release.changelog.isNotBlank()) {
                Text(stringResource(R.string.updates_changelog_label), style = MaterialTheme.typography.labelMedium)
                Text(state.release.changelog, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { OrdiaUpdateController.download(context, state.release) }) {
                    Text(stringResource(R.string.updates_install_now))
                }
                TextButton(onClick = { OrdiaUpdateController.dismissAvailable() }) {
                    Text(stringResource(R.string.updates_later))
                }
            }
        }
    }
}

@Composable
private fun DownloadingCard(state: UpdateState.Downloading) {
    val context = LocalContext.current
    val total = state.total
    val fraction = if (total > 0L) (state.bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.updates_downloading, state.release.tag), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { fraction ?: 0f }, Modifier.fillMaxWidth())
            Text(
                if (total > 0L && state.bytes >= 0L) {
                    stringResource(R.string.updates_progress_mb, state.bytes / 1_000_000, total / 1_000_000)
                } else {
                    "${state.bytes.coerceAtLeast(0L) / 1_000_000} MB"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { OrdiaUpdateController.cancel(context) }) {
                Text(stringResource(R.string.updates_cancel))
            }
        }
    }
}

@Composable
private fun ReadyCard(state: UpdateState.Ready) {
    val context = LocalContext.current
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.updates_ready), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.updates_ready_desc), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { OrdiaUpdateController.install(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_install))
            }
        }
    }
}

@Composable
private fun FailedCard(state: UpdateState.Failed) {
    val context = LocalContext.current
    val signatureIssue = state.reason.contains("firma", ignoreCase = true)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.updates_failed), style = MaterialTheme.typography.titleMedium)
            Text(state.reason, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { OrdiaUpdateController.retry(context) }) {
                    Text(stringResource(R.string.updates_retry))
                }
                if (signatureIssue) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(OrdiaUpdateManager.releasePageUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text(stringResource(R.string.update_download_manual)) }
                }
            }
        }
    }
}
