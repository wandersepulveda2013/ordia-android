package com.ordia.app.intelligence

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Gestor de descarga, verificación y caché del modelo de lenguaje local.
 *
 * El modelo se descarga como activo externo verificable (no incluido en la APK).
 * Soporta Gemma 2B en formato TFLite con metadatos de MediaPipe.
 *
 * Flujo:
 * 1. Verificar si el modelo ya está cacheado y es válido
 * 2. Si no, descargar desde URL oficial verificada
 * 3. Verificar SHA-256 y tamaño
 * 4. Mover a almacenamiento privado
 * 5. Cargar en MediaPipe LLM Inference
 *
 * Tamaño esperado: ~1.5 GB (Gemma 2B 4-bit quantized)
 * Para pruebas/demo, usar TinyLlama 1.1B (~700 MB).
 */
object IntelligenceModelManager {

    private const val TAG = "IntelligenceModelManager"
    private const val MODEL_DIR = "intelligence-model"
    private const val MODEL_FILENAME = "gemma-2b-cpu-int4.tflite"
    private const val CHECKSUM_FILENAME = "gemma-2b-cpu-int4.tflite.sha256"
    private const val MAX_MODEL_BYTES = 3L * 1024L * 1024L * 1024L  // 3 GB
    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT = 30_000
    private const val READ_TIMEOUT = 60_000

    /** URLs oficiales de descarga del modelo (verificadas SHA-256). */
    // Gemma 2B 4-bit quantized desde HuggingFace (Kaggle model)
    private const val MODEL_URL_DEFAULT =
        "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-cpu-int4.tflite"
    private const val CHECKSUM_URL_DEFAULT =
        "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-cpu-int4.tflite.sha256"

    /** SHA-256 conocido del modelo oficial (se verifica en tiempo de descarga). */
    private val EXPECTED_SHA256: String? = null // Se obtiene del checksum remoto

    /** Tamaño esperado del modelo en bytes (verificado del remoto). */
    private val EXPECTED_BYTES: Long = 0L // Se obtiene del remoto

    /**
     * Estados del modelo de lenguaje local.
     */
    sealed class ModelState {
        /** No se ha iniciado ninguna operación */
        data object Idle : ModelState()
        /** Descargando el modelo (progreso 0..100) */
        data class Downloading(val progress: Float) : ModelState()
        /** Modelo descargado pero no verificado */
        data object Downloaded : ModelState()
        /** Modelo descargado y verificado, listo para cargar */
        data object Ready : ModelState()
        /** Modelo cargado en memoria y disponible para inferencia */
        data object Loaded : ModelState()
        /** Error durante descarga, verificación o carga */
        data class Error(val reason: String) : ModelState()
    }

    private var _state: ModelState = ModelState.Idle
    private var _onStateChange: ((ModelState) -> Unit)? = null

    /** Estado actual del modelo */
    val state: ModelState get() = _state

    /** ¿El modelo está cargado y listo para inferencia? */
    val isModelAvailable: Boolean get() = _state is ModelState.Loaded

    /**
     * Inicia la descarga del modelo desde la URL oficial.
     * Reporta progreso vía onStateChange.
     */
    suspend fun download(
        context: Context,
        onProgress: ((Float) -> Unit)? = null,
        onStateChange: ((ModelState) -> Unit)? = null
    ): ModelState = withContext(Dispatchers.IO) {
        _onStateChange = onStateChange
        _state = ModelState.Idle

        // 1. Verificar si ya existe en caché
        val cachedModel = modelFile(context)
        if (cachedModel.exists() && cachedModel.length() > 0L) {
            Log.d(TAG, "Modelo encontrado en caché: ${cachedModel.length()} bytes")
            _state = ModelState.Downloaded
            onStateChange?.invoke(_state)

            // Verificar checksum
            val verified = verifyModel(cachedModel, checksumFile(context))
            if (verified) {
                _state = ModelState.Ready
                onStateChange?.invoke(_state)
                return@withContext _state
            } else {
                Log.w(TAG, "Caché inválida, redescargando")
                cachedModel.delete()
                checksumFile(context).delete()
            }
        }

        // 2. Descargar checksum
        _state = ModelState.Downloading(0f)
        onStateChange?.invoke(_state)
        val checksum = downloadChecksum(context) ?: run {
            _state = ModelState.Error("No se pudo obtener el checksum del modelo")
            onStateChange?.invoke(_state)
            return@withContext _state
        }

        // 3. Descargar modelo
        val success = downloadModel(context, checksum, onProgress)
        if (!success) return@withContext _state

        // 4. Verificar
        _state = ModelState.Downloaded
        onStateChange?.invoke(_state)
        val verified = verifyModel(modelFile(context), checksumFile(context))
        if (!verified) {
            _state = ModelState.Error("El checksum del modelo no coincide")
            onStateChange?.invoke(_state)
            modelFile(context).delete()
            checksumFile(context).delete()
            return@withContext _state
        }

        _state = ModelState.Ready
        onStateChange?.invoke(_state)
        _state
    }

    /** Carga el modelo en memoria para inferencia (implementación específica del provider). */
    suspend fun loadForInference(context: Context): ModelState = withContext(Dispatchers.IO) {
        val model = modelFile(context)
        if (!model.exists() || model.length() == 0L) {
            _state = ModelState.Error("El modelo no está descargado. Descárgalo primero.")
            _onStateChange?.invoke(_state)
            return@withContext _state
        }

        // La carga real la realiza LocalModelProvider usando MediaPipe LLM Inference
        // Aquí solo marcamos que el archivo está listo
        _state = ModelState.Loaded
        _onStateChange?.invoke(_state)
        _state
    }

    /** Elimina el modelo descargado y libera espacio. */
    fun deleteModel(context: Context) {
        modelFile(context).delete()
        checksumFile(context).delete()
        modelDirectory(context).listFiles()?.forEach { it.delete() }
        _state = ModelState.Idle
        _onStateChange?.invoke(_state)
    }

    /** Obtiene el tamaño del modelo descargado en bytes (para UI). */
    fun getModelSizeBytes(context: Context): Long = modelFile(context).length()

    /** Ruta al archivo del modelo en almacenamiento privado. */
    fun modelFile(context: Context): File = File(modelDirectory(context), MODEL_FILENAME)

    private fun modelDirectory(context: Context): File =
        File(context.filesDir, MODEL_DIR).also { it.mkdirs() }

    private fun checksumFile(context: Context): File = File(modelDirectory(context), CHECKSUM_FILENAME)

    private suspend fun downloadChecksum(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val url = CHECKSUM_URL_DEFAULT
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"

            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "Error HTTP ${connection.responseCode} obteniendo checksum")
                return@withContext null
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            val checksum = text.split("\\s+".toRegex()).firstOrNull()
                ?.takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdefABCDEF" } }

            // Guardar checksum
            if (checksum != null) {
                checksumFile(context).writeText(checksum)
            }

            checksum
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando checksum", e)
            null
        }
    }

    private suspend fun downloadModel(
        context: Context,
        expectedChecksum: String,
        onProgress: ((Float) -> Unit)?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val destination = modelFile(context)
            val tempFile = File(modelDirectory(context), "$MODEL_FILENAME.part")

            // Download con seguimiento de progreso
            val url = MODEL_URL_DEFAULT
            var currentUrl = url
            var redirectCount = 0

            while (redirectCount <= MAX_REDIRECTS) {
                val connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Ordia/3.0")

                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (location == null || redirectCount >= MAX_REDIRECTS) {
                        Log.e(TAG, "Redirección sin destino o demasiadas")
                        return@withContext false
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirectCount++
                    connection.disconnect()
                    continue
                }

                if (status !in 200..299) {
                    Log.e(TAG, "Error HTTP $status descargando modelo")
                    return@withContext false
                }

                val contentLength = connection.contentLengthLong
                if (contentLength > MAX_MODEL_BYTES) {
                    Log.e(TAG, "Modelo demasiado grande: $contentLength bytes")
                    return@withContext false
                }

                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes: Long = 0
                    val input = connection.inputStream

                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (totalBytes > MAX_MODEL_BYTES) {
                            Log.e(TAG, "Descarga supera tamaño máximo")
                            return@withContext false
                        }
                        if (contentLength > 0) {
                            val progress = totalBytes.toFloat() / contentLength
                            onProgress?.invoke(progress)
                            _state = ModelState.Downloading(progress)
                            _onStateChange?.invoke(_state)
                        }
                    }
                }

                connection.disconnect()

                // Verificar tamaño mínimo
                if (tempFile.length() < 1_000_000L) { // Mínimo 1MB
                    Log.e(TAG, "Modelo descargado demasiado pequeño: ${tempFile.length()} bytes")
                    tempFile.delete()
                    return@withContext false
                }

                // Renombrar
                destination.delete()
                if (!tempFile.renameTo(destination)) {
                    tempFile.copyTo(destination, overwrite = true)
                    tempFile.delete()
                }

                Log.i(TAG, "Modelo descargado: ${destination.length()} bytes")
                return@withContext true
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo", e)
            _state = ModelState.Error("Error de descarga: ${e.message?.take(100)}")
            _onStateChange?.invoke(_state)
            false
        }
    }

    private fun verifyModel(modelFile: File, checksumFile: File): Boolean {
        if (!modelFile.exists()) return false
        if (!checksumFile.exists()) return false

        try {
            val expected = checksumFile.readText().trim().lowercase()
            if (expected.length != 64 || expected.any { it !in "0123456789abcdef" }) return false

            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }

            return actual == expected
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando modelo", e)
            return false
        }
    }
}
