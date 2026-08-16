package com.ordia.app.intelligence

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Memoria de inteligencia exclusivamente basada en acciones confirmadas.
 *
 * NO almacena conversaciones, NO almacena texto original,
 * NO almacena nada que el usuario no haya aceptado explícitamente.
 *
 * Solo almacena preferencias extraídas de acciones confirmadas:
 * - Lugares frecuentes ("supermercado", "farmacia")
 * - Personas recurrentes ("Juan", "María")
 * - Horarios típicos ("martes", "mañana")
 * - Preferencias de acción ("recordar", "tarea")
 *
 * El buffer de texto se borra inmediatamente después del análisis.
 */
class IntelligenceMemory(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Any()

    /**
     * Registra una preferencia extraída de una acción confirmada por el usuario.
     * Solo se almacena si el usuario aceptó la acción explícitamente.
     */
    fun recordConfirmedAction(label: String, schema: IntelligenceSchema) {
        synchronized(mutex) {
            try {
                val history = getConfirmedHistory().toMutableList()
                history.add(0, ConfirmedAction(
                    label = label,
                    actor = schema.actor.value,
                    actionType = schema.actionSuggested.value,
                    timestampMs = System.currentTimeMillis()
                ))
                if (history.size > MAX_HISTORY) {
                    history.removeAt(history.size - 1)
                }
                prefs.edit()
                    .putString(KEY_CONFIRMED, history.toJson())
                    .apply()

                // Extraer lugares/personas recurrentes
                if (schema.actionParameters.containsKey("place")) {
                    recordFrequent("place", schema.actionParameters["place"]!!)
                }
                if (schema.actionParameters.containsKey("person")) {
                    recordFrequent("person", schema.actionParameters["person"]!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando memoria", e)
            }
        }
    }

    /**
     * Obtiene el historial de acciones confirmadas (solo labels, sin texto original)
     */
    fun getConfirmedHistory(): List<ConfirmedAction> {
        return try {
            val json = prefs.getString(KEY_CONFIRMED, null) ?: return emptyList()
            ConfirmedAction.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo historial", e)
            emptyList()
        }
    }

    /**
     * Obtiene los últimos N labels de acciones confirmadas para contexto entre turnos.
     * Esto es lo único que se pasa al modelo/prompt — nunca texto original.
     */
    fun getRecentContextLabels(limit: Int = 5): List<String> {
        return getConfirmedHistory().take(limit).map { it.label }
    }

    /**
     * Obtiene lugares frecuentes para ayudar al modelo a inferir contexto.
     */
    fun getFrequentPlaces(): List<String> = getFrequent("place")

    /**
     * Obtiene personas frecuentes para ayudar al modelo.
     */
    fun getFrequentPersons(): List<String> = getFrequent("person")

    /** Limpia toda la memoria (solicitado por el usuario) */
    fun clear() {
        synchronized(mutex) {
            prefs.edit()
                .remove(KEY_CONFIRMED)
                .remove(KEY_FREQUENT_PREFIX + "place")
                .remove(KEY_FREQUENT_PREFIX + "person")
                .apply()
        }
    }

    private fun recordFrequent(category: String, value: String) {
        synchronized(mutex) {
            val current = getFrequent(category).toMutableList()
            current.remove(value)
            current.add(0, value)
            val limited = current.take(MAX_FREQUENT)
            prefs.edit()
                .putString(KEY_FREQUENT_PREFIX + category, limited.joinToString(","))
                .apply()
        }
    }

    private fun getFrequent(category: String): List<String> {
        val raw = prefs.getString(KEY_FREQUENT_PREFIX + category, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    data class ConfirmedAction(
        val label: String,
        val actor: String,
        val actionType: String,
        val timestampMs: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("label", label)
            put("actor", actor)
            put("actionType", actionType)
            put("ts", timestampMs)
        }

        companion object {
            fun fromJson(json: String): List<ConfirmedAction> {
                val arr = JSONArray(json)
                return (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ConfirmedAction(
                        label = obj.getString("label"),
                        actor = obj.optString("actor", "yo"),
                        actionType = obj.optString("actionType", "none"),
                        timestampMs = obj.optLong("ts", 0L)
                    )
                }
            }
        }
    }

    private fun List<ConfirmedAction>.toJson(): String =
        JSONArray(map { it.toJson() }).toString()

    companion object {
        private const val TAG = "IntelligenceMemory"
        private const val PREFS_NAME = "intelligence_memory"
        private const val KEY_CONFIRMED = "confirmed_actions"
        private const val KEY_FREQUENT_PREFIX = "frequent_"
        private const val MAX_HISTORY = 50
        private const val MAX_FREQUENT = 10
    }
}
