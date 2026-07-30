package com.ordia.app.context

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.security.MessageDigest
import java.security.SecureRandom

class ContextualSuggestionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ordia_contextual_inbox", Context.MODE_PRIVATE)
    private val lock = Any()

    fun list(): List<ContextualSuggestion> = synchronized(lock) { read().sortedByDescending { it.createdAt } }

    fun add(suggestion: ContextualSuggestion, dailyLimit: Int): Boolean = synchronized(lock) {
        val storedSuggestion = suggestion.copy(id = saltedId(suggestion.id))
        val current = read().filter { System.currentTimeMillis() - it.createdAt <= MAX_AGE_MS }
        if (current.any { it.id == storedSuggestion.id }) return false
        val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
        val todayCount = current.count { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }
        if (todayCount >= dailyLimit.coerceIn(1, 20)) return false
        write((current + storedSuggestion).sortedByDescending { it.createdAt }.take(MAX_ITEMS))
        true
    }

    fun remove(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }
    fun clear() = synchronized(lock) { prefs.edit().remove(KEY_ITEMS).commit() }

    private fun read(): List<ContextualSuggestion> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val suggestion = runCatching {
                    ContextualSuggestion(
                        id = item.getString("id"),
                        kind = ContextualKind.valueOf(item.getString("kind")),
                        title = item.getString("title").take(100),
                        dueAt = if (item.isNull("dueAt")) null else item.getLong("dueAt"),
                        confidence = item.getDouble("confidence"),
                        sourcePackage = item.optString("sourcePackage").takeIf(String::isNotBlank),
                        createdAt = item.getLong("createdAt")
                    )
                }.getOrNull()
                if (suggestion != null && System.currentTimeMillis() - suggestion.createdAt <= MAX_AGE_MS) add(suggestion)
            }
        }
    }.getOrElse { emptyList() }

    private fun write(items: List<ContextualSuggestion>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { suggestion ->
            array.put(JSONObject()
                .put("id", suggestion.id)
                .put("kind", suggestion.kind.name)
                .put("title", suggestion.title)
                .put("dueAt", suggestion.dueAt ?: JSONObject.NULL)
                .put("confidence", suggestion.confidence)
                .put("sourcePackage", suggestion.sourcePackage ?: "")
                .put("createdAt", suggestion.createdAt))
        }
        check(prefs.edit().putString(KEY_ITEMS, array.toString()).commit()) { "No se pudo guardar la bandeja contextual." }
    }

    private fun saltedId(input: String): String {
        val salt = prefs.getString(KEY_SALT, null) ?: ByteArray(32).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }
            .also { prefs.edit().putString(KEY_SALT, it).commit() }
        return MessageDigest.getInstance("SHA-256")
            .digest("$salt|$input".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_SALT = "salt"
        private const val MAX_ITEMS = 20
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60_000
    }
}
