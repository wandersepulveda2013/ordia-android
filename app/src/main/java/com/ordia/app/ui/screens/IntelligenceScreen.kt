package com.ordia.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
@Composable
fun IntelligenceScreen(
    context: Context = LocalContext.current,
    contentPadding: PaddingValues
) {
    val scope = rememberCoroutineScope()
    val engine = remember { OrdiaIntelligenceEngine.getInstance(context) }
    val modelManager = remember { IntelligenceModelManager }

    var currentMode by remember { mutableStateOf(
        when {
            engine.isLocalModelAvailable -> "Inteligencia local (modelo)"
            engine.isLocalModelEnabled -> "Local activado (inferencia pendiente)"
            else -> "Modo básico (reglas)"
        }
    ) }
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
            append("RAM: ${compat.totalRamMb}MB disponible")
            if (compat.reasons.isNotEmpty()) {
                append("\n")
                append(compat.reasons.joinToString("\n"))
            }
        }
        val size = modelManager.getModelSize(context, selectedProfile.modelFile)
        modelSize = if (size > 0) "${size / (1024 * 1024)} MB" else "No descargado"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("INTELIGENCIA", "Inteligencia de Ordía")

        // Modo actual
        SectionHeader("Modo actual")
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
                        engine.isLocalModelAvailable -> "Las frases se procesan con el modelo local."
                        engine.isLocalModelEnabled -> "Descarga y carga funcionan, pero la inferencia local aún no está implementada (requiere tokenizador). El análisis se resuelve con el motor de reglas."
                        else -> "Las frases se procesan con reglas. Puedes descargar un modelo para tenerlo listo cuando la inferencia local esté disponible."
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
                    Text("Inferencia local pendiente de implementación",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text("La descarga y la carga del modelo Gemma funcionan, pero la inferencia " +
                        "requiere la API de tarea con tokenizador (aún no integrada). Mientras tanto, " +
                        "el análisis se resuelve con el motor de reglas.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Selección de perfil
        SectionHeader("Perfil de modelo")
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
                        if (isSelected) Text("Seleccionado", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(profile.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tamaño: ~${profile.estimatedSizeMb}MB | RAM mínima: ${profile.minRamMb}MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!compat.compatible) {
                        Text("⚠ Dispositivo no compatible: ${compat.reasons.firstOrNull() ?: "requisitos no cumplidos"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Estado del modelo
        SectionHeader("Estado")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val isDownloaded = modelManager.isModelDownloaded(context, selectedProfile.modelFile)
                Text("Archivo: ${selectedProfile.modelFile}", style = MaterialTheme.typography.bodySmall)
                Text("Tamaño: $modelSize", style = MaterialTheme.typography.bodySmall)
                Text("Descargado: ${if (isDownloaded) "Sí" else "No"}", style = MaterialTheme.typography.bodySmall)
                Text("SHA-256: Verificado al descargar", style = MaterialTheme.typography.bodySmall)
                Text("Almacenamiento: Privado (${context.filesDir.absolutePath})", style = MaterialTheme.typography.bodySmall)
                Text("Licencia: Gemma Terms of Use", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Barra de progreso
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Text("${(downloadProgress * 100).toInt()}% - $downloadStateText",
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
                        downloadStateText = "Conectando..."
                        val success = modelManager.downloadModelWithProgress(
                            context = context,
                            filename = selectedProfile.modelFile,
                            onProgress = { progress ->
                                downloadProgress = progress
                                downloadStateText = "Descargando..."
                            },
                            onStateChange = { state ->
                                downloadStateText = when (state) {
                                    is IntelligenceModelManager.DownloadState.Idle -> "Inactivo"
                                    is IntelligenceModelManager.DownloadState.Downloading -> "Descargando"
                                    is IntelligenceModelManager.DownloadState.Paused -> "En pausa"
                                    is IntelligenceModelManager.DownloadState.Verifying -> "Verificando SHA-256..."
                                    is IntelligenceModelManager.DownloadState.Ready -> "¡Listo!"
                                    is IntelligenceModelManager.DownloadState.Error -> "Error: ${state.reason}"
                                }
                            }
                        )
                        isDownloading = false
                        if (success) {
                            currentMode = "Modelo descargado (inferencia pendiente)"
                            modelSize = "${modelManager.getModelSize(context, selectedProfile.modelFile) / (1024 * 1024)} MB"
                        }
                    }
                },
                enabled = !isDownloading && !isDownloaded,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isDownloaded) "Descargado" else if (isDownloading) "Descargando..."
                else "Descargar (${selectedProfile.estimatedSizeMb}MB)")
            }

            if (isDownloaded) {
                Button(
                    onClick = {
                        scope.launch {
                            engine.isLocalModelEnabled = true
                            val loaded = engine.loadLocalModel()
                            currentMode = if (loaded) {
                                if (engine.isLocalModelAvailable) "Inteligencia local (modelo)"
                                else "Modelo cargado (inferencia pendiente)"
                            } else "Error al cargar"
                        }
                    },
                    enabled = !engine.isLocalModelAvailable,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (engine.isLocalModelAvailable) "Cargado" else "Cargar modelo")
                }

                OutlinedButton(
                    onClick = {
                        modelManager.deleteModel(context, selectedProfile.modelFile)
                        currentMode = if (engine.isLocalModelEnabled) "Local activado (inferencia pendiente)"
                        else "Modo básico (reglas)"
                        modelSize = "No descargado"
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            }
        }

        // Información de privacidad
        Spacer(Modifier.height(16.dp))
        SectionHeader("Privacidad")
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("• Todo el procesamiento es 100% local en tu dispositivo.",
                    style = MaterialTheme.typography.bodySmall)
                Text("• El modelo se descarga a almacenamiento privado de Ordía.",
                    style = MaterialTheme.typography.bodySmall)
                Text("• No se envía ningún texto a servidores externos.",
                    style = MaterialTheme.typography.bodySmall)
                Text("• No se almacenan conversaciones ni frases originales.",
                    style = MaterialTheme.typography.bodySmall)
                Text("• Solo se guardan acciones confirmadas por el usuario.",
                    style = MaterialTheme.typography.bodySmall)
                Text("• Licencia: ${modelManager.getAllModels().firstOrNull()?.license ?: "Gemma Terms of Use"}",
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
                Text("Modo básico siempre disponible",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text("Sin inferencia local implementada, Ordía usa su motor de reglas para entender " +
                    "frases. Puedes descargar el modelo para dejar la infraestructura lista cuando la " +
                    "inferencia local esté disponible.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
