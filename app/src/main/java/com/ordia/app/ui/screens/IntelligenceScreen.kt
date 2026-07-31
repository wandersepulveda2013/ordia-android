package com.ordia.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.intelligence.IntelligenceModelManager
import com.ordia.app.intelligence.LocalModelProvider
import com.ordia.app.intelligence.OrdiaIntelligenceEngine
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader
import kotlinx.coroutines.launch

/**
 * Pantalla de gestión de inteligencia local.
 * Más > Inteligencia de Ordía
 *
 * Muestra:
 * - Modo actual (básico / local activo)
 * - Perfiles disponibles (ligero / mejor comprensión)
 * - Compatibilidad del dispositivo
 * - Botón de descarga con progreso
 * - Estado del modelo
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
// Los getString restantes viven en LaunchedEffect, corrutinas y lambdas de progreso,
// donde stringResource (composable) no es válido; el acceso vía context es correcto ahí.
@Composable
fun IntelligenceScreen(
    context: Context = LocalContext.current,
    contentPadding: PaddingValues
) {
    val scope = rememberCoroutineScope()
    val engine = remember { OrdiaIntelligenceEngine.getInstance(context) }
    val modelManager = remember { IntelligenceModelManager }

    val currentModeInitial = when {
        engine.isLocalModelAvailable -> stringResource(R.string.intel_mode_local_model)
        engine.isLocalModelEnabled -> stringResource(R.string.intel_mode_local_enabled)
        else -> stringResource(R.string.intel_mode_basic)
    }
    var currentMode by remember { mutableStateOf(currentModeInitial) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStateText by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf(LocalModelProvider.ModelProfile.LIGERO) }
    var modelSize by remember { mutableStateOf("") }
    var compatibilityInfo by remember { mutableStateOf("") }
    var showProfileDialog by remember { mutableStateOf(false) }

    // Cargar info de compatibilidad
    LaunchedEffect(Unit) {
        val compat = modelManager.deviceSupportsProfile(context, selectedProfile)
        compatibilityInfo = buildString {
            append(context.getString(R.string.intel_compat_ram, compat.totalRamMb))
            if (compat.reasons.isNotEmpty()) {
                append("\n")
                append(compat.reasons.joinToString("\n"))
            }
        }
        val size = modelManager.getModelSize(context, selectedProfile.modelFile)
        modelSize = if (size > 0) context.getString(R.string.intel_size_mb, size / (1024 * 1024)) else context.getString(R.string.common_not_downloaded)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(stringResource(R.string.intel_header_eyebrow), stringResource(R.string.intel_header_title))

        // Modo actual
        SectionHeader(stringResource(R.string.intel_section_current_mode))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (engine.isLocalModelAvailable)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(currentMode, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        engine.isLocalModelAvailable -> stringResource(R.string.intel_mode_local_model_desc)
                        engine.isLocalModelEnabled -> stringResource(R.string.intel_mode_local_enabled_desc)
                        else -> stringResource(R.string.intel_mode_basic_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!engine.isLocalModelInferenceSupported) {
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.intel_inference_pending_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.intel_inference_pending_desc),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Selección de perfil
        SectionHeader(stringResource(R.string.intel_section_model_profile))
        LocalModelProvider.ModelProfile.entries.forEach { profile ->
            val isSelected = selectedProfile == profile
            val compat = remember(profile) { modelManager.deviceSupportsProfile(context, profile) }

            Card(
                onClick = { selectedProfile = profile },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder() else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (isSelected) Text(stringResource(R.string.intel_profile_selected), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(profile.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.intel_profile_specs, profile.estimatedSizeMb, profile.minRamMb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!compat.compatible) {
                        Text(stringResource(R.string.intel_device_incompatible, compat.reasons.firstOrNull() ?: stringResource(R.string.intel_device_incompatible_fallback)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Estado del modelo
        SectionHeader(stringResource(R.string.intel_section_state))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val isDownloaded = modelManager.isModelDownloaded(context, selectedProfile.modelFile)
                Text(stringResource(R.string.intel_state_file, selectedProfile.modelFile), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_state_size, modelSize), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_state_downloaded, if (isDownloaded) stringResource(R.string.common_yes) else stringResource(R.string.common_no)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_state_sha), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_state_storage, context.filesDir.absolutePath), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_state_license), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Barra de progreso
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Text(stringResource(R.string.intel_progress_percent, (downloadProgress * 100).toInt(), downloadStateText),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isDownloaded = modelManager.isModelDownloaded(context, selectedProfile.modelFile)

            Button(
                onClick = {
                    scope.launch {
                        isDownloading = true
                        downloadStateText = context.getString(R.string.intel_connecting)
                        val success = modelManager.downloadModelWithProgress(
                            context = context,
                            filename = selectedProfile.modelFile,
                            onProgress = { progress ->
                                downloadProgress = progress
                                downloadStateText = context.getString(R.string.intel_downloading)
                            },
                            onStateChange = { state ->
                                downloadStateText = when (state) {
                                    is IntelligenceModelManager.DownloadState.Idle -> context.getString(R.string.intel_download_idle)
                                    is IntelligenceModelManager.DownloadState.Downloading -> context.getString(R.string.intel_download_downloading)
                                    is IntelligenceModelManager.DownloadState.Paused -> context.getString(R.string.intel_download_paused)
                                    is IntelligenceModelManager.DownloadState.Verifying -> context.getString(R.string.intel_download_verifying)
                                    is IntelligenceModelManager.DownloadState.Ready -> context.getString(R.string.intel_download_ready)
                                    is IntelligenceModelManager.DownloadState.Error -> context.getString(R.string.intel_download_error, state.reason)
                                }
                            }
                        )
                        isDownloading = false
                        if (success) {
                            currentMode = context.getString(R.string.intel_mode_model_downloaded)
                            modelSize = context.getString(R.string.intel_size_mb, modelManager.getModelSize(context, selectedProfile.modelFile) / (1024 * 1024))
                        }
                    }
                },
                enabled = !isDownloading && !isDownloaded,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isDownloaded) stringResource(R.string.common_downloaded) else if (isDownloading) stringResource(R.string.intel_downloading)
                else stringResource(R.string.intel_download_button, selectedProfile.estimatedSizeMb))
            }

            if (isDownloaded) {
                Button(
                    onClick = {
                        scope.launch {
                            engine.isLocalModelEnabled = true
                            val loaded = engine.loadLocalModel()
                            currentMode = if (loaded) {
                                if (engine.isLocalModelAvailable) context.getString(R.string.intel_mode_local_model)
                                else context.getString(R.string.intel_mode_model_loaded)
                            } else context.getString(R.string.intel_mode_load_error)
                        }
                    },
                    enabled = !engine.isLocalModelAvailable,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (engine.isLocalModelAvailable) stringResource(R.string.intel_loaded) else stringResource(R.string.intel_load_model))
                }

                OutlinedButton(
                    onClick = {
                        modelManager.deleteModel(context, selectedProfile.modelFile)
                        currentMode = if (engine.isLocalModelEnabled) context.getString(R.string.intel_mode_local_enabled)
                        else context.getString(R.string.intel_mode_basic)
                        modelSize = context.getString(R.string.common_not_downloaded)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }

        // Información de privacidad
        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.intel_section_privacy))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.intel_privacy_local),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_privacy_storage),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_privacy_no_send),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_privacy_no_conversations),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_privacy_confirmed),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.intel_privacy_license, modelManager.getAllModels().firstOrNull()?.license ?: stringResource(R.string.intel_license_gemma)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Nota sobre el modo básico
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.intel_basic_always_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.intel_basic_always_desc),
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
