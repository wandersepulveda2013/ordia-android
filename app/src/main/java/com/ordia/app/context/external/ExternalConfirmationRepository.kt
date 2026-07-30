package com.ordia.app.context.external

import android.content.Context
import android.content.SharedPreferences
import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextIntentKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia para la cola de sugerencias externas y configuración.
 *
 * Almacena únicamente datos estructurados (nunca texto original del usuario).
 * La cola se serializa como JSON para sobrevivir a muerte de proceso.
 * Los patrones ignorados se almacenan como hash normalizado + tipo de intención.
 */
class ExternalConfirmationRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========================================================================
    // Cola persistida
    // ========================================================================

    /** Guarda la cola completa. */
    fun saveQueue(items: List<ExternalSuggestion>) {
        val json = JSONArray()
        items.forEach { json.put(serialize(it)) }
        prefs.edit().putString(KEY_QUEUE, json.toString()).apply()
    }

    /** Restaura la cola guardada. */
    fun loadQueue(): List<ExternalSuggestion> {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        val list = mutableListOf<ExternalSuggestion>()
        try {
            val json = JSONArray(raw)
            for (i in 0 until json.length()) {
                val item = deserialize(json.getJSONObject(i))
                if (item != null && item.isActionable && !item.isExpired) {
                    list.add(item)
                }
            }
        } catch (_: Exception) {
            // Si el JSON está corrupto, empezar de nuevo
            prefs.edit().remove(KEY_QUEUE).apply()
        }
        return list
    }

    /** Limpia la cola persistida. */
    fun clearQueue() {
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    // ========================================================================
    // Patrones ignorados ("No detectar frases parecidas")
    // ========================================================================

    data class IgnoredPattern(
        val kind: ContextIntentKind,
        val tokensHash: Int,
        val sourceApp: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    /** Guarda un patrón ignorado. */
    fun addIgnoredPattern(pattern: IgnoredPattern) {
        val set = prefs.getStringSet(KEY_IGNORED_PATTERNS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add("${pattern.kind.name}:${pattern.tokensHash}:${pattern.sourceApp}")
        prefs.edit().putStringSet(KEY_IGNORED_PATTERNS, set).apply()
    }

    /** Verifica si un patrón está ignorado. */
    fun isPatternIgnored(kind: ContextIntentKind, tokensHash: Int, sourceApp: String = ""): Boolean {
        val set = prefs.getStringSet(KEY_IGNORED_PATTERNS, emptySet()) ?: emptySet()
        return set.contains("${kind.name}:${tokensHash}:${sourceApp}")
    }

    /** Obtiene todos los patrones ignorados. */
    fun getIgnoredPatterns(): List<IgnoredPattern> {
        val set = prefs.getStringSet(KEY_IGNORED_PATTERNS, emptySet()) ?: emptySet()
        return set.mapNotNull { raw ->
            val parts = raw.split(":")
            if (parts.size >= 2) {
                try {
                    val kind = ContextIntentKind.valueOf(parts[0])
                    IgnoredPattern(kind, parts[1].toInt(), parts.getOrElse(2) { "" })
                } catch (_: Exception) { null }
            } else null
        }
    }

    /** Elimina un patrón ignorado. */
    fun removeIgnoredPattern(kind: ContextIntentKind, tokensHash: Int, sourceApp: String = "") {
        val set = prefs.getStringSet(KEY_IGNORED_PATTERNS, mutableSetOf())?.toMutableSet() ?: return
        set.remove("${kind.name}:${tokensHash}:${sourceApp}")
        prefs.edit().putStringSet(KEY_IGNORED_PATTERNS, set).apply()
    }

    // ========================================================================
    // Configuración
    // ========================================================================

    var overlayPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_REQUESTED, false)
        set(value) { prefs.edit().putBoolean(KEY_OVERLAY_REQUESTED, value).apply() }

    var externalConfirmationEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXTERNAL_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_EXTERNAL_ENABLED, value).apply() }

    var excludedApps: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_EXCLUDED_APPS, value).apply() }

    var quietHoursStart: Int // minutos desde medianoche
        get() = prefs.getInt(KEY_QUIET_HOURS_START, 22 * 60) // 22:00
        set(value) { prefs.edit().putInt(KEY_QUIET_HOURS_START, value).apply() }

    var quietHoursEnd: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_END, 7 * 60) // 07:00
        set(value) { prefs.edit().putInt(KEY_QUIET_HOURS_END, value).apply() }

    var reducedMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCED_MOTION, false)
        set(value) { prefs.edit().putBoolean(KEY_REDUCED_MOTION, value).apply() }

    var consentGiven: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_GIVEN, false)
        set(value) { prefs.edit().putBoolean(KEY_CONSENT_GIVEN, value).apply() }

    /** Normaliza un título para generar un hash de tokens. */
    companion object {
        private const val PREFS_NAME = "ordia_external_confirmation"
        private const val KEY_QUEUE = "suggestion_queue"
        private const val KEY_IGNORED_PATTERNS = "ignored_patterns"
        private const val KEY_OVERLAY_REQUESTED = "overlay_permission_requested"
        private const val KEY_EXTERNAL_ENABLED = "external_confirmation_enabled"
        private const val KEY_EXCLUDED_APPS = "excluded_apps"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        private const val KEY_REDUCED_MOTION = "reduced_motion"
        private const val KEY_CONSENT_GIVEN = "consent_given"

        /** Genera un hash normalizado de tokens organizativos a partir de un título. */
        fun normalizeTokensHash(title: String): Int {
            // Extraer solo palabras significativas, normalizar
            val tokens = title.lowercase()
                .replace(Regex("[^a-záéíóúüñ0-9 ]"), "")
                .split(" ")
                .filter { it.length > 3 }
                .sorted()
                .take(5)
            return tokens.hashCode()
        }
    }

    // ========================================================================
    // Serialización
    // ========================================================================

    private fun serialize(s: ExternalSuggestion): JSONObject {
        return JSONObject().apply {
            put("id", s.id)
            put("confirmationId", s.confirmationId)
            put("kind", s.kind.name)
            put("title", s.title)
            put("dueAt", s.dueAt ?: -1L)
            put("source", s.source.name)
            put("priority", s.priority)
            put("confidence", s.confidence.toDouble())
            put("createdAt", s.createdAt)
            put("expiresAt", s.expiresAt)
            put("state", s.state.name)
        }
    }

    private fun deserialize(json: JSONObject): ExternalSuggestion? {
        return try {
            ExternalSuggestion(
                id = json.getString("id"),
                confirmationId = json.getString("confirmationId"),
                kind = ContextIntentKind.valueOf(json.getString("kind")),
                title = json.getString("title"),
                dueAt = json.optLong("dueAt", -1L).let { if (it < 0) null else it },
                source = ContextCaptureSource.valueOf(json.getString("source")),
                priority = json.getInt("priority"),
                confidence = json.getDouble("confidence").toFloat(),
                createdAt = json.getLong("createdAt"),
                expiresAt = json.getLong("expiresAt"),
                state = ExternalSuggestionState.valueOf(json.getString("state"))
            )
        } catch (_: Exception) { null }
    }
}
