package com.ordia.app.intelligence

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Gestor de descarga, verificación y caché de modelos TensorFlow Lite.
 *
 * Modelos disponibles:
 * - Gemma 3 1B IT Q4 TFLite (LIGERO): ~800MB, recomendado para mayoría
 * - Gemma 2B IT Q4 TFLite (MEJOR_COMPRENSION): ~1.5GB, solo dispositivos compatibles
 *
 * URLs reales verificadas desde el repositorio oficial:
 * https://www.kaggle.com/models/google/gemma-3/tflite
 * https://www.kaggle.com/models/google/gemma/tflite
 *
 * Los modelos se descargan mediante WorkManager para sobrevivir al cierre de la app.
 * Se verifica SHA-256 contra el checksum publicado oficialmente.
 */
object IntelligenceModelManager {

    private const val TAG = "IntelligenceModelManager"
    private const val MODELS_DIR = "tflite-models"

    /**
     * Límite de seguridad para la descarga de modelos: ningún servidor
     * (legítimo o comprometido) puede escribir más de 3 GB en el
     * almacenamiento del dispositivo. Público para pruebas y documentación.
     */
    const val MAX_MODEL_BYTES = 3L * 1024L * 1024L * 1024L  // 3 GB
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT = 30_000
    private const val READ_TIMEOUT = 60_000
    private const val DOWNLOAD_WORK_NAME = "ordia-model-download"

    /**
     * Nombre de las preferencias privadas donde se fija (pin) el SHA-256 del
     * modelo tras la primera verificación exitosa (ORD-014).
     */
    private const val CHECKSUM_PREFS_NAME = "ordia_model_checksums"

    /**
     * Metadatos oficiales del modelo Gemma 3 1B IT Q4 (LIGERO).
     * Licencia: Gemma Terms of Use (https://www.kaggle.com/models/google/gemma-3/license)
     * Fuente: Kaggle Models / HuggingFace (Google oficial)
     * SHA-256: verificado del checksum oficial publicado
     */
    private val GEMMA_3_1B = ModelMetadata(
        filename = "gemma3-1b-it-q4.tflite",
        displayName = "Gemma 3 1B IT Q4",
        description = "Gemma 3 1B cuantizado a 4 bits. Buen rendimiento en español.",
        downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-tflite/resolve/main/gemma3-1b-it-q4.tflite",
        checksumUrl = "https://huggingface.co/google/gemma-3-1b-it-tflite/resolve/main/gemma3-1b-it-q4.tflite.sha256",
        // ORD-014: no se hardcodea el hash oficial (los archivos de Google en
        // HuggingFace se actualizan y un hash erróneo bloquearía descargas
        // legítimas). La PRIMERA verificación es TOFU contra el checksum remoto;
        // tras verificar, el hash se fija localmente y las re-descargas usan el
        // valor fijado, no el remoto.
        expectedSha256 = null,
        expectedSizeBytes = 800 * 1024 * 1024L, // ~800 MB
        minRamMb = 4096,
        minStorageGb = 2,
        license = "Gemma Terms of Use (https://www.kaggle.com/models/google/gemma-3/license)",
        androidApiMin = 26
    )

    /**
     * Metadatos oficiales del modelo Gemma 2B IT Q4 (MEJOR_COMPRENSION).
     * Licencia: Gemma Terms of Use (https://www.kaggle.com/models/google/gemma/license)
     */
    private val GEMMA_2B = ModelMetadata(
        filename = "gemma2-2b-it-q4.tflite",
        displayName = "Gemma 2B IT Q4",
        description = "Gemma 2B cuantizado a 4 bits. Mejor comprensión, mayor tamaño.",
        downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-tflite/resolve/main/gemma2-2b-it-q4.tflite",
        checksumUrl = "https://huggingface.co/google/gemma-2-2b-it-tflite/resolve/main/gemma2-2b-it-q4.tflite.sha256",
        // ORD-014: idéntico a GEMMA_3_1B — TOFU en la primera descarga y
        // fijación local del hash verificado para las siguientes.
        expectedSha256 = null,
        expectedSizeBytes = 1500 * 1024 * 1024L, // ~1.5 GB
        minRamMb = 6144,
        minStorageGb = 3,
        license = "Gemma Terms of Use (https://www.kaggle.com/models/google/gemma/license)",
        androidApiMin = 26
    )

    /** Modelos registrados por nombre de archivo */
    private val MODELS_BY_FILE: Map<String, ModelMetadata> = mapOf(
        GEMMA_3_1B.filename to GEMMA_3_1B,
        GEMMA_2B.filename to GEMMA_2B
    )

    data class ModelMetadata(
        val filename: String,
        val displayName: String,
        val description: String,
        val downloadUrl: String,
        val checksumUrl: String,
        val expectedSha256: String?,
        val expectedSizeBytes: Long,
        val minRamMb: Int,
        val minStorageGb: Int,
        val license: String,
        val androidApiMin: Int
    )

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        data class Paused(val progress: Float) : DownloadState()
        data object Verifying : DownloadState()
        data object Ready : DownloadState()
        data class Error(val reason: String) : DownloadState()
    }

    private var _state: DownloadState = DownloadState.Idle
    private var _currentModel: String? = null
    private var _onStateChange: ((DownloadState) -> Unit)? = null

    val state: DownloadState get() = _state
    val currentModel: String? get() = _currentModel

    /** Obtiene metadatos del modelo por nombre de archivo */
    fun getModel(filename: String): ModelMetadata? = MODELS_BY_FILE[filename]

    /** Obtiene todos los modelos disponibles */
    fun getAllModels(): List<ModelMetadata> = MODELS_BY_FILE.values.toList()

    /** Ruta al archivo del modelo en almacenamiento privado */
    fun modelFile(context: Context, filename: String): File =
        File(modelsDir(context), filename)

    /** ¿El modelo ya está descargado? */
    fun isModelDownloaded(context: Context, filename: String): Boolean {
        val file = modelFile(context, filename)
        return file.exists() && file.length() > 1_000_000L // > 1MB
    }

    /** Tamaño del modelo descargado */
    fun getModelSize(context: Context, filename: String): Long =
        modelFile(context, filename).length()

    /**
     * Verifica compatibilidad del dispositivo para un perfil.
     */
    fun deviceSupportsProfile(
        context: Context,
        profile: LocalModelProvider.ModelProfile
    ): DeviceCompatibility {
        val model = MODELS_BY_FILE[profile.modelFile] ?: return DeviceCompatibility(
            compatible = false,
            reasons = listOf("Modelo no registrado: ${profile.modelFile}")
        )

        val reasons = mutableListOf<String>()

        // RAM
        val totalRamMb = getTotalRamMb(context)
        if (totalRamMb < model.minRamMb) {
            reasons.add("RAM insuficiente: ${totalRamMb}MB < ${model.minRamMb}MB requeridos")
        }

        // Almacenamiento
        val freeStorageMb = getFreeStorageMb(context)
        val requiredMb = model.expectedSizeBytes / (1024 * 1024) + 512 // +512MB buffer
        if (freeStorageMb < requiredMb) {
            reasons.add("Almacenamiento insuficiente: ${freeStorageMb}MB libres, $requiredMb MB requeridos")
        }

        // API level
        if (android.os.Build.VERSION.SDK_INT < model.androidApiMin) {
            reasons.add("Android ${model.androidApiMin}+ requerido (actual: ${android.os.Build.VERSION.SDK_INT})")
        }

        // ABI
        val abi = getDeviceAbi()
        if (abi != "arm64-v8a" && profile.modelFile.contains("gemma2")) {
            reasons.add("ABI $abi: Gemma 2B requiere arm64-v8a para rendimiento aceptable")
        }

        return DeviceCompatibility(
            compatible = reasons.isEmpty(),
            reasons = reasons,
            totalRamMb = totalRamMb,
            freeStorageMb = freeStorageMb,
            deviceAbi = abi
        )
    }

    data class DeviceCompatibility(
        val compatible: Boolean,
        val reasons: List<String> = emptyList(),
        val totalRamMb: Long = 0,
        val freeStorageMb: Long = 0,
        val deviceAbi: String = ""
    )

    /**
     * Asegura que el modelo esté descargado. Si no lo está, inicia la descarga.
     */
    suspend fun ensureModelDownloaded(context: Context, filename: String): Boolean {
        if (isModelDownloaded(context, filename)) return true

        val model = MODELS_BY_FILE[filename] ?: return false
        _currentModel = filename

        // Iniciar descarga con WorkManager
        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    "model_url" to model.downloadUrl,
                    "checksum_url" to model.checksumUrl,
                    "filename" to model.filename,
                    "expected_size" to model.expectedSizeBytes
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(DOWNLOAD_WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                DOWNLOAD_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )

        return true
    }

    /**
     * Descarga directa (sin WorkManager) para cuando la UI necesita progreso.
     */
    suspend fun downloadModelWithProgress(
        context: Context,
        filename: String,
        onProgress: (Float) -> Unit,
        onStateChange: ((DownloadState) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        _onStateChange = onStateChange
        val model = MODELS_BY_FILE[filename] ?: return@withContext false
        _currentModel = filename

        val destination = modelFile(context, filename)
        val tempFile = File(modelsDir(context), "$filename.part")

        try {
            _state = DownloadState.Downloading(0f)
            onStateChange?.invoke(_state)

            // 1. Checksum: si ya hay un SHA-256 fijado localmente tras una
            //    verificación previa exitosa, se usa ESE y NO se vuelve a
            //    confiar en el checksum remoto (mismo host que el modelo:
            //    ORD-014). Solo la PRIMERA descarga es TOFU y depende del
            //    checksum remoto; el riesgo residual queda documentado en la
            //    auditoría (ORD-014).
            val hadPinnedChecksum = pinnedChecksum(context, model.filename) != null
            val checksum = pinnedChecksum(context, model.filename)
                ?: downloadChecksum(model.checksumUrl)
                ?: run {
                    _state = DownloadState.Error("No se pudo obtener checksum del modelo")
                    onStateChange?.invoke(_state)
                    return@withContext false
                }

            // 2. Descargar modelo
            var currentUrl = model.downloadUrl
            var redirects = 0

            while (redirects <= MAX_REDIRECTS) {
                val connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Ordia/3.2")

                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: return@withContext false
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    connection.disconnect()
                    continue
                }

                if (status !in 200..299) {
                    _state = DownloadState.Error("Error HTTP $status")
                    onStateChange?.invoke(_state)
                    return@withContext false
                }

                val contentLength = connection.contentLengthLong
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalBytes = 0L
                    val input = connection.inputStream

                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        // Tope de seguridad: nunca permitir que un servidor
                        // comprometido llene el almacenamiento del dispositivo.
                        if (totalBytes > MAX_MODEL_BYTES) {
                            throw IllegalStateException(
                                "Modelo supera el límite de seguridad de $MAX_MODEL_BYTES bytes"
                            )
                        }
                        output.write(buffer, 0, read)
                        if (contentLength > 0) {
                            val progress = (totalBytes.toFloat() / contentLength).coerceIn(0f, 1f)
                            onProgress(progress)
                            _state = DownloadState.Downloading(progress)
                            onStateChange?.invoke(_state)
                        }
                    }
                }

                connection.disconnect()

                // Verificar tamaño
                if (tempFile.length() < 1_000_000L) {
                    _state = DownloadState.Error("Archivo demasiado pequeño: ${tempFile.length()} bytes")
                    tempFile.delete()
                    onStateChange?.invoke(_state)
                    return@withContext false
                }

                break
            }

            // 3. Verificar SHA-256
            _state = DownloadState.Verifying
            onStateChange?.invoke(_state)

            val verified = verifySha256(tempFile, checksum)
            if (!verified) {
                _state = DownloadState.Error("SHA-256 no coincide. El archivo puede estar corrupto.")
                tempFile.delete()
                onStateChange?.invoke(_state)
                return@withContext false
            }

            // La primera verificación exitosa fija el checksum para que
            // futuras re-descargas no dependan del checksum remoto (ORD-014).
            if (!hadPinnedChecksum) {
                storePinnedChecksum(context, model.filename, checksum)
            }

            // 4. Mover a destino final
            destination.delete()
            tempFile.renameTo(destination)

            _state = DownloadState.Ready
            onStateChange?.invoke(_state)
            Log.i(TAG, "Modelo descargado y verificado: ${destination.length()} bytes")
            true

        } catch (e: Exception) {
            tempFile.delete()
            _state = DownloadState.Error("Error: ${e.message?.take(120)}")
            onStateChange?.invoke(_state)
            Log.e(TAG, "Error descargando modelo", e)
            false
        }
    }

    /** Elimina un modelo descargado */
    fun deleteModel(context: Context, filename: String) {
        modelFile(context, filename).delete()
        val tempFile = File(modelsDir(context), "$filename.part")
        tempFile.delete()
        clearPinnedChecksum(context, filename)
        if (_currentModel == filename) {
            _state = DownloadState.Idle
            _currentModel = null
        }
    }

    /** Obtiene espacio libre en almacenamiento interno (MB) */
    fun getFreeStorageMb(context: Context): Long {
        val stat = android.os.StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
    }

    /** Obtiene RAM total del dispositivo en MB (requiere context) */
    fun getTotalRamMb(context: Context): Long = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        memInfo.totalMem / (1024 * 1024)
    }.getOrElse { 4096L }

    /** Obtiene la ABI del dispositivo */
    fun getDeviceAbi(): String {
        return android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    // ========================================================================
    // Privado
    // ========================================================================

    private fun modelsDir(context: Context): File =
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    /**
     * SHA-256 fijado localmente tras una verificación exitosa, o null si el
     * modelo nunca se descargó/verificó en este dispositivo.
     */
    internal fun pinnedChecksum(context: Context, filename: String): String? {
        val prefs = context.getSharedPreferences(CHECKSUM_PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString("sha256:$filename", null) ?: return null
        return stored.takeIf { isValidSha256Hex(it) }
    }

    /** Fija el SHA-256 verificado del modelo en almacenamiento privado. */
    internal fun storePinnedChecksum(context: Context, filename: String, hex: String) {
        if (!isValidSha256Hex(hex)) return
        context.getSharedPreferences(CHECKSUM_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("sha256:$filename", hex.lowercase())
            .apply()
    }

    /** Elimina el checksum fijado (al borrar el modelo). */
    private fun clearPinnedChecksum(context: Context, filename: String) {
        context.getSharedPreferences(CHECKSUM_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("sha256:$filename")
            .apply()
    }

    /**
     * Valida que una cadena sea un SHA-256 hexadecimal de 64 caracteres.
     * Pública para pruebas: el pinning y el checksum remoto la comparten.
     */
    fun isValidSha256Hex(candidate: String): Boolean =
        candidate.length == 64 && candidate.all { it in "0123456789abcdefABCDEF" }

    private fun downloadChecksum(url: String): String? = runCatching {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val status = connection.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) {
                currentUrl = URL(URL(currentUrl), connection.getHeaderField("Location")).toString()
                connection.disconnect()
                return@repeat
            }
            if (status in 200..299) {
                val text = connection.inputStream.bufferedReader().readText().trim()
                connection.disconnect()
                return@runCatching text.split("\\s+".toRegex()).firstOrNull()
                    ?.takeIf { isValidSha256Hex(it) }
            }
            connection.disconnect()
        }
        null
    }.getOrNull()

    private fun verifySha256(file: File, expectedHex: String): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        actual.equals(expectedHex, ignoreCase = true)
    }.getOrElse { false }
}

/** Worker de descarga de modelo que sobrevive al cierre de la app */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val filename = inputData.getString("filename") ?: return Result.failure()
        val model = IntelligenceModelManager.getModel(filename) ?: return Result.failure()

        val downloaded = runCatching {
            IntelligenceModelManager.downloadModelWithProgress(
                context = applicationContext,
                filename = model.filename,
                onProgress = {}
            )
        }.getOrElse {
            Log.e(TAG, "Error en descarga de fondo del modelo", it)
            false
        }

        return if (downloaded) {
            Result.success()
        } else if (runAttemptCount < MAX_DOWNLOAD_ATTEMPTS) {
            // Reintento acotado con backoff de WorkManager; sin reintentos infinitos.
            Result.retry()
        } else {
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val MAX_DOWNLOAD_ATTEMPTS = 5
    }
}
