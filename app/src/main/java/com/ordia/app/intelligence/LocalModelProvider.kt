package com.ordia.app.intelligence

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Proveedor de inteligencia que usa un modelo de lenguaje local (Gemma 2B)
 * ejecutado en el dispositivo a través de MediaPipe LLM Inference.
 *
 * REQUISITOS:
 * - Android 14+ (API 34+) preferido para aceleración GPU
 * - Modelo TFLite descargado (~1.5 GB) en almacenamiento privado
 * - Dependencia: com.google.mediapipe:tasks-text (agregada en build.gradle.kts)
 *
 * El modelo recibe un prompt de sistema que le exige devolver JSON
 * estructurado siguiendo IntelligenceSchema. Si el JSON es inválido,
 * se fuerza un esquema de rechazo (actionSuggested = NONE).
 *
 * @see IntelligenceProvider Interfaz que implementa
 * @see IntelligenceSchema Esquema de salida estructurada
 */
class LocalModelProvider(private val appContext: Context) : IntelligenceProvider {

    override val displayName: String = "Inteligencia local (modelo)"
    override val providerId: ProviderSource = ProviderSource.LOCAL_MODEL

    private var mediaPipeLoaded: Boolean = false
    private var inferenceModel: Any? = null // MediaPipe LLMInference

    override val isAvailable: Boolean
        get() = mediaPipeLoaded && inferenceModel != null

    /**
     * Carga el modelo TFLite en MediaPipe LLM Inference.
     * Debe llamarse después de que IntelligenceModelManager haya descargado y verificado el modelo.
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = IntelligenceModelManager.modelFile(appContext)
            if (!modelFile.exists()) {
                Log.w(TAG, "Modelo no encontrado en ${modelFile.absolutePath}")
                return@withContext false
            }

            // Cargar modelo via MediaPipe LLM Inference
            // Nota: MediaPipe requiere el modelo en formato TFLite con metadatos específicos
            // El siguiente código es la integración real con MediaPipe:
            //
            // val modelPath = modelFile.absolutePath
            // val options = LlmInference.LlmInferenceOptions.builder()
            //     .setModelPath(modelPath)
            //     .setMaxTokens(512)
            //     .setTemperature(0.2f)  // Baja temperatura para salida estructurada
            //     .setTopK(40)
            //     .build()
            // inferenceModel = LlmInference.createFromOptions(appContext, options)
            // mediaPipeLoaded = true

            // Simulación mientras no se pueda probar en dispositivo:
            // Cuando el modelo real esté presente, MediaPipe lo cargará.
            // Por ahora, reportamos que la infraestructura está lista
            // pero la carga real requiere el archivo TFLite en disco.
            if (modelFile.length() > 10_000_000L) { // Mínimo 10MB para ser un modelo válido
                // Intentar carga real (fallará sin el modelo, pero la infraestructura es correcta)
                Log.i(TAG, "Modelo encontrado (${modelFile.length()} bytes). Pendiente de carga MediaPipe.")
                // En dispositivo real con el modelo:
                // mediaPipeLoaded = true
                // inferenceModel = ...
            }

            Log.i(TAG, "Infraestructura LocalModelProvider lista. " +
                    "Requiere validación en dispositivo Android con el modelo descargado.")
            // Para desarrollo/pruebas retornamos false hasta que se valide en dispositivo
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando modelo local", e)
            false
        }
    }

    override suspend fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        val startTime = System.currentTimeMillis()

        if (!isAvailable) {
            return IntelligenceResponse(
                schema = IntelligenceSchema(
                    privacyResult = IntelligenceSafetyGate.evaluate(request.originalText)
                ),
                confidenceScore = 0f,
                providerSource = ProviderSource.LOCAL_MODEL,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            // Construir el prompt de sistema con el esquema JSON
            val prompt = buildPrompt(request)

            // Ejecutar inferencia con MediaPipe
            // val result = inferenceModel?.generateResponse(prompt) ?: "{}"
            // Por ahora, como el modelo no está cargado, devolvemos fallback
            val result = "{}"

            val schema = IntelligenceSchema.fromJson(result)
            val confidence = if (schema != null) 1.0f else 0.0f

            return IntelligenceResponse(
                schema = schema ?: IntelligenceSchema(
                    privacyResult = IntelligenceSafetyGate.evaluate(request.originalText)
                ),
                rawModelOutput = result,
                confidenceScore = confidence,
                providerSource = ProviderSource.LOCAL_MODEL,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en inferencia del modelo", e)
            return IntelligenceResponse(
                schema = IntelligenceSchema(
                    privacyResult = IntelligenceSafetyGate.evaluate(request.originalText)
                ),
                confidenceScore = 0f,
                providerSource = ProviderSource.LOCAL_MODEL,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Construye el prompt completo para el modelo de lenguaje.
     * Incluye:
     * - Instrucciones de sistema (exigir JSON estructurado)
     * - Esquema de salida (IntelligenceSchema)
     * - Texto del usuario
     * - Contexto de memoria (últimas acciones confirmadas)
     *
     * El prompt está diseñado para que Gemma 2B produzca JSON válido.
     */
    private fun buildPrompt(request: IntelligenceRequest): String {
        val safeText = IntelligenceSafetyGate.sanitize(request.originalText)
        val context = buildContextString(request.recentConfirmedHistory)

        return """<bos><start_of_turn>system
Eres un asistente organizativo llamado Ordía. Analiza el texto del usuario y extrae información estructurada en JSON.

Debes devolver SOLO un objeto JSON válido sin explicaciones adicionales. Sigue este esquema exactamente:

{
  "actor": "yo" | "alguien" | "alguienMas" | "nosotros",
  "polarity": "positivo" | "negativo",
  "certainty": "cierto" | "probable" | "dudoso" | "condicional",
  "temporalDirection": "pasado" | "presente" | "futuro" | "futuroCercano" | "condicionalFuturo",
  "actionSuggested": "task" | "shopping" | "appointment" | "meeting" | "reminder" | "call" | "payment" | "study" | "exercise" | "deadline" | "household" | "none",
  "actionParameters": {
    "place": "lugar si aplica",
    "person": "persona si aplica",
    "item": "ítem si aplica"
  },
  "followUpQuestion": "pregunta de seguimiento o null si no aplica",
  "privacyResult": "segura" | "bloqueada"
}

REGLAS:
- Si el texto contiene información sensible (contraseñas, datos bancarios, salud, violencia, sexo, drogas), marca privacyResult como "bloqueada".
- Si no hay una acción clara, usa actionSuggested "none".
- Para negaciones explícitas ("no", "nunca", "jamás"), usa polarity "negativo".
- Para condicionales ("cuando", "si"), usa certainty "condicional".
- Para duda ("tal vez", "quizás"), usa certainty "dudoso".
- Detecta correctamente quien realiza la acción.
<end_of_turn>

<start_of_turn>context
${context}
<end_of_turn>

<start_of_turn>user
${safeText}
<end_of_turn>

<start_of_turn>model
"""
    }

    private fun buildContextString(history: List<String>): String {
        if (history.isEmpty()) return "Sin historial reciente."
        return "Acciones recientes confirmadas:\n" +
            history.take(5).joinToString("\n") { "- $it" }
    }

    companion object {
        private const val TAG = "LocalModelProvider"
    }
}
