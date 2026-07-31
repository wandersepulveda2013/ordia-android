package com.ordia.app.intelligence

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Proveedor de inteligencia que ejecuta un modelo de lenguaje local
 * en el dispositivo mediante TensorFlow Lite.
 *
 * ESTADO REAL (auditoría ORD-003):
 * - La descarga y la carga del modelo TFLite SÍ funcionan.
 * - La inferencia NO está implementada de forma correcta: Gemma en TFLite
 *   exige la API de tarea (GemmaTask/LLM) con tokenizador SentencePiece y
 *   decodificación iterativa. Inferir con bytes UTF-8 crudos (como hacía la
 *   versión anterior) nunca produce resultados válidos.
 * - Por tanto, `isAvailable` es FALSE hasta que se implemente la inferencia
 *   real. `analyze()` devuelve un estado Unsupported explícito y documentado
 *   en lugar de fingir un análisis.
 *
 * PERFILES:
 * - Ligero: Gemma 3 1B (para la mayoría con >=4GB RAM)
 * - Mejor comprensión: Gemma 2B (solo >=6GB RAM)
 * - Modo básico: BasicRuleProvider (sin modelo descargado)
 *
 * @property appContext Contexto de aplicación
 */
class LocalModelProvider(private val appContext: Context) : IntelligenceProvider {

    override val displayName: String = "Inteligencia local (modelo)"
    override val providerId: ProviderSource = ProviderSource.LOCAL_MODEL

    private var interpreter: Any? = null // org.tensorflow.lite.Interpreter
    private var _profile: ModelProfile = ModelProfile.LIGERO
    private var _isLoading = false
    private var _loadError: String? = null

    val profile: ModelProfile get() = _profile
    val isLoading: Boolean get() = _isLoading
    val loadError: String? get() = _loadError

    /**
     * ¿La inferencia local real está implementada?
     *
     * FALSE: la infraestructura de descarga/carga existe, pero la inferencia
     * requiere la API de tarea Gemma con tokenizador, todavía no integrada
     * (ORD-003). El router debe seguir usando BasicRuleProvider.
     */
    val isInferenceSupported: Boolean = false

    override val isAvailable: Boolean
        get() = interpreter != null && !_isLoading && isInferenceSupported

    /**
     * Perfiles de modelo disponibles.
     */
    enum class ModelProfile(
        val displayName: String,
        val modelFile: String,
        val estimatedSizeMb: Int,
        val minRamMb: Int,
        val minStorageMb: Int,
        val description: String
    ) {
        LIGERO(
            displayName = "Ligero (recomendado)",
            modelFile = "gemma3-1b-it-q4.tflite",
            estimatedSizeMb = 800,
            minRamMb = 4096,
            minStorageMb = 2048,
            description = "Gemma 3 1B cuantizado a 4 bits. Buen español, recomendado para la mayoría de dispositivos."
        ),
        MEJOR_COMPRENSION(
            displayName = "Mejor comprensión",
            modelFile = "gemma2-2b-it-q4.tflite",
            estimatedSizeMb = 1500,
            minRamMb = 6144,
            minStorageMb = 3072,
            description = "Gemma 2B cuantizado a 4 bits. Mejor comprensión, solo en dispositivos con >=6GB RAM."
        )
    }

    /**
     * Verifica si el dispositivo es compatible con un perfil.
     */
    fun deviceSupportsProfile(profile: ModelProfile, totalRamMb: Long): Boolean {
        return totalRamMb >= profile.minRamMb
    }

    /**
     * Carga el modelo TFLite desde el archivo descargado.
     */
    suspend fun loadModel(profile: ModelProfile = _profile): Boolean = withContext(Dispatchers.IO) {
        if (_isLoading) return@withContext false
        _isLoading = true
        _loadError = null
        _profile = profile

        try {
            val modelFile = IntelligenceModelManager.modelFile(appContext, profile.modelFile)
            if (!modelFile.exists() || modelFile.length() < 1_000_000L) {
                _loadError = "Modelo no encontrado: ${modelFile.absolutePath}. Descárgalo desde Más > Inteligencia de Ordía."
                Log.w(TAG, _loadError ?: "Error desconocido")
                _isLoading = false
                return@withContext false
            }

            // Cargar con TensorFlow Lite
            val tfliteOptions = org.tensorflow.lite.Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = org.tensorflow.lite.Interpreter(modelFile, tfliteOptions)

            Log.i(TAG, "Modelo TFLite cargado: perfil=${profile.displayName}, archivo=${modelFile.name}")
            _isLoading = false
            true
        } catch (e: Exception) {
            _loadError = "Error cargando modelo: ${e.message?.take(120)}"
            Log.e(TAG, _loadError, e)
            interpreter = null
            _isLoading = false
            false
        }
    }

    /**
     * Asegura que el modelo esté descargado antes de cargar.
     */
    suspend fun ensureModelDownloaded(profile: ModelProfile): Boolean {
        return IntelligenceModelManager.ensureModelDownloaded(appContext, profile.modelFile)
    }

    override suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        val startTime = System.currentTimeMillis()

        // ORD-003: la inferencia local no está implementada. En lugar de
        // ejecutar una inferencia falsa (bytes UTF-8 crudos sobre un tensor
        // de tokens que jamás funciona con Gemma TFLite), se devuelve un
        // estado Unsupported explícito y honesto.
        Log.w(TAG, "analyze() llamado pero la inferencia local no está implementada. " +
            "Usar BasicRuleProvider (ORD-003).")
        return IntelligenceResponse(
            schema = IntelligenceSchema(),
            confidenceScore = 0f,
            providerSource = ProviderSource.LOCAL_MODEL,
            processingTimeMs = System.currentTimeMillis() - startTime,
            unsupportedReason = UNSUPPORTED_REASON
        )
    }

    /**
     * Libera el modelo de memoria.
     */
    fun unloadModel() {
        (interpreter as? org.tensorflow.lite.Interpreter)?.close()
        interpreter = null
        _isLoading = false
        _loadError = null
        Log.d(TAG, "Modelo TFLite descargado")
    }

    companion object {
        private const val TAG = "LocalModelProvider"

        /** Razón explícita y documentada del estado Unsupported (ORD-003). */
        const val UNSUPPORTED_REASON =
            "La inferencia local requiere la API de tarea Gemma (tokenizador SentencePiece y " +
            "decodificación iterativa), todavía no integrada. La descarga y carga del modelo " +
            "funcionan, pero el análisis se resuelve con el motor de reglas."
    }
}
