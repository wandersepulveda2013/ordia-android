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
 * SOPORTA:
 * - Gemma 3 1B cuantizado (recomendado, ~800 MB, buen español)
 * - Gemma 2B cuantizado (~1.5 GB, mejor comprensión)
 * - Cualquier modelo en formato .tflite con salida de texto
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

    override val isAvailable: Boolean
        get() = interpreter != null && !_isLoading

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

        if (!isAvailable) {
            return IntelligenceResponse(
                schema = IntelligenceSchema(),
                confidenceScore = 0f,
                providerSource = ProviderSource.LOCAL_MODEL,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            val prompt = buildPrompt(request)

            // TF Lite inference
            val tflite = interpreter as org.tensorflow.lite.Interpreter
            val inputBytes = prompt.toByteArray(Charsets.UTF_8)
            val outputBytes = ByteArray(4096) // buffer de salida
            tflite.run(inputBytes, outputBytes)
            val result = String(outputBytes, Charsets.UTF_8).trimEnd('\u0000').trim()
            val schema = IntelligenceSchema.fromJson(result)

            return IntelligenceResponse(
                schema = schema ?: IntelligenceSchema(),
                rawModelOutput = result,
                confidenceScore = if (schema != null) 0.85f else 0f,
                providerSource = ProviderSource.LOCAL_MODEL,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en inferencia TFLite", e)
            return IntelligenceResponse(
                schema = IntelligenceSchema(),
                confidenceScore = 0f,
                providerSource = ProviderSource.LOCAL_MODEL,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }
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

    private fun buildPrompt(request: IntelligenceRequest): String {
        val safeText = IntelligenceSafetyGate.sanitize(request.originalText)
        val context = request.recentConfirmedHistory.take(5)
            .joinToString("\n") { "- $it" }

        return """<bos><start_of_turn>system
Eres un asistente organizativo llamado Ordía. Analiza el texto del usuario y extrae información estructurada en JSON.

Debes devolver SOLO un objeto JSON válido sin explicaciones adicionales:

{
  "actor": "yo" | "alguien" | "alguienMas" | "nosotros",
  "polarity": "positivo" | "negativo",
  "certainty": "cierto" | "probable" | "dudoso" | "condicional",
  "temporalDirection": "pasado" | "presente" | "futuro" | "futuroCercano" | "condicionalFuturo",
  "actionSuggested": "task" | "shopping" | "appointment" | "meeting" | "reminder" | "call" | "payment" | "study" | "exercise" | "deadline" | "household" | "none",
  "actionParameters": {},
  "followUpQuestion": "pregunta de seguimiento o null",
  "privacyResult": "segura" | "bloqueada"
}

REGLAS:
- privacyResult "bloqueada" para información sensible (contraseñas, salud, violencia, sexo, drogas).
- polarity "negativo" para negaciones explícitas ("no", "nunca", "jamás").
- certainty "dudoso" para "tal vez", "quizás", "a lo mejor".
- certainty "condicional" para "cuando", "si", "en cuanto".
- Si el actor NO es "yo", actionSuggested debe ser "none".
- Si temporalDirection es "pasado", actionSuggested debe ser "none".
- Si certainty es "dudoso", incluir followUpQuestion.
- Para "cuando + subjuntivo" usar temporalDirection "condicionalFuturo".
<end_of_turn>

<start_of_turn>context
Historial: ${context.ifEmpty { "Sin historial reciente." }}
<end_of_turn>

<start_of_turn>user
${safeText}
<end_of_turn>

<start_of_turn>model
"""
    }

    companion object {
        private const val TAG = "LocalModelProvider"
    }
}
