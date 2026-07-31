package com.ordia.app.context

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

/**
 * Registro de auditoría local para el motor contextual.
 *
 * Almacena QUÉ se creó (tipo, título), CUÁNDO, DESDE DÓNDE (fuente),
 * y la CONFIABILIDAD. NO almacena el texto original.
 *
 * Cumplimiento: todos los datos permanecen en el dispositivo.
 * El registro puede exportarse como JSON para revisión del usuario.
 */
class ContextAuditLog(context: Context) {

    private val appContext = context.applicationContext
    private val dbHelper = AuditDbHelper(context)

    /**
     * Registra una intención confirmada.
     */
    fun logIntentCreated(intent: ContextIntent) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_KIND, intent.kind.name)
            put(COL_TITLE_HASH, titleHash(intent.title))
            put(COL_SOURCE, intent.source.name)
            put(COL_CONFIDENCE, intent.confidence.toDouble())
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_ACTION, "CREATED")
        }
        db.insert(TABLE_AUDIT, null, values)
    }

    /**
     * Registra una intención descartada por baja confianza o filtro.
     */
    fun logIntentDiscarded(intent: ContextIntent, reason: DiscardReason) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_KIND, intent.kind.name)
            put(COL_TITLE_HASH, titleHash(intent.title))
            put(COL_SOURCE, intent.source.name)
            put(COL_CONFIDENCE, intent.confidence.toDouble())
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_ACTION, "DISCARDED")
            put(COL_REASON, reason.name)
        }
        db.insert(TABLE_AUDIT, null, values)
    }

    /**
     * Registra una intención rechazada por el usuario.
     */
    fun logIntentRejected(intent: ContextIntent) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_KIND, intent.kind.name)
            put(COL_TITLE_HASH, titleHash(intent.title))
            put(COL_SOURCE, intent.source.name)
            put(COL_CONFIDENCE, intent.confidence.toDouble())
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_ACTION, "REJECTED")
        }
        db.insert(TABLE_AUDIT, null, values)
    }

    /**
     * Registra una intención modificada por el usuario.
     */
    fun logIntentModified(original: ContextIntent, modified: ContextIntent) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_KIND, modified.kind.name)
            put(COL_TITLE_HASH, titleHash(modified.title))
            put(COL_SOURCE, original.source.name)
            put(COL_CONFIDENCE, modified.confidence.toDouble())
            put(COL_TIMESTAMP, System.currentTimeMillis())
            put(COL_ACTION, "MODIFIED")
            put(COL_PREVIOUS_HASH, titleHash(original.title))
        }
        db.insert(TABLE_AUDIT, null, values)
    }

    /**
     * Obtiene todos los registros en orden cronológico descendente.
     */
    fun getAllEntries(limit: Int = 100): List<AuditEntry> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_AUDIT, null, null, null, null, null,
            "$COL_TIMESTAMP DESC", limit.toString()
        )
        val entries = mutableListOf<AuditEntry>()
        cursor.use {
            while (it.moveToNext()) {
                entries.add(
                    AuditEntry(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        kind = it.getString(it.getColumnIndexOrThrow(COL_KIND)),
                        titleHash = it.getString(it.getColumnIndexOrThrow(COL_TITLE_HASH)),
                        source = it.getString(it.getColumnIndexOrThrow(COL_SOURCE)),
                        confidence = it.getFloat(it.getColumnIndexOrThrow(COL_CONFIDENCE)),
                        timestamp = Date(it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))),
                        action = it.getString(it.getColumnIndexOrThrow(COL_ACTION)),
                        reason = it.getString(it.getColumnIndexOrThrow(COL_REASON))
                    )
                )
            }
        }
        return entries
    }

    /**
     * Limpia registros más antiguos que el período especificado.
     */
    fun cleanOlderThan(ageMs: Long = DEFAULT_RETENTION_MS): Int {
        val db = dbHelper.writableDatabase
        val cutoff = System.currentTimeMillis() - ageMs
        return db.delete(TABLE_AUDIT, "$COL_TIMESTAMP < ?", arrayOf(cutoff.toString()))
    }

    /**
     * Exporta el registro de auditoría como texto JSON.
     */
    fun exportAsJson(): String {
        val entries = getAllEntries()
        val sb = StringBuilder()
        sb.appendLine("[")
        entries.forEachIndexed { index, entry ->
            sb.appendLine("  {")
            sb.appendLine("    \"id\": ${entry.id},")
            sb.appendLine("    \"kind\": \"${entry.kind}\",")
            sb.appendLine("    \"titleHash\": \"${entry.titleHash}\",")
            sb.appendLine("    \"source\": \"${entry.source}\",")
            sb.appendLine("    \"confidence\": ${entry.confidence},")
            sb.appendLine("    \"timestamp\": \"${entry.timestamp}\",")
            sb.appendLine("    \"action\": \"${entry.action}\"${if (entry.reason != null) "," else ""}")
            if (entry.reason != null) {
                sb.appendLine("    \"reason\": \"${entry.reason}\"")
            }
            sb.append("  }")
            if (index < entries.size - 1) sb.appendLine(",") else sb.appendLine()
        }
        sb.appendLine("]")
        return sb.toString()
    }

    /**
     * Obtiene estadísticas resumidas del registro.
     */
    fun getStats(): AuditStats {
        val entries = getAllEntries(limit = Int.MAX_VALUE)
        val byKind = entries.groupBy { it.kind }.mapValues { it.value.size }
        val byAction = entries.groupBy { it.action }.mapValues { it.value.size }
        val bySource = entries.groupBy { it.source }.mapValues { it.value.size }
        val avgConf = entries.map { it.confidence }.average().toFloat()

        return AuditStats(
            totalEntries = entries.size,
            byKind = byKind,
            byAction = byAction,
            bySource = bySource,
            averageConfidence = avgConf,
            retentionDays = DEFAULT_RETENTION_MS / (86_400_000L)
        )
    }

    /**
     * Hash SHA-256 completo con sal aleatoria por instalación.
     *
     * La sal se genera una sola vez (16 bytes) y se guarda en prefs; sin ella,
     * títulos cortos y predecibles podrían recuperarse por ataque de diccionario.
     */
    private fun titleHash(title: String): String {
        val normalized = title.lowercase().trim().replace(Regex("\\s+"), " ")
        val bytes = normalized.toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes + salt)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /** Sal aleatoria persistente por instalación (16 bytes) */
    private val salt: ByteArray by lazy {
        val prefs = appContext.getSharedPreferences(PREFS_SALT, android.content.Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_SALT, null)
        if (stored != null && stored.length == 32) {
            stored.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            val generated = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val hex = generated.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_SALT, hex).apply()
            generated
        }
    }

    fun close() {
        dbHelper.close()
    }

    // Database helpers

    private class AuditDbHelper(context: Context) : SQLiteOpenHelper(
        context, DB_NAME, null, DB_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_AUDIT (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_KIND TEXT NOT NULL,
                    $COL_TITLE_HASH TEXT NOT NULL,
                    $COL_SOURCE TEXT NOT NULL,
                    $COL_CONFIDENCE REAL NOT NULL,
                    $COL_TIMESTAMP INTEGER NOT NULL,
                    $COL_ACTION TEXT NOT NULL,
                    $COL_PREVIOUS_HASH TEXT,
                    $COL_REASON TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX idx_audit_timestamp ON $TABLE_AUDIT($COL_TIMESTAMP)"
            )
            db.execSQL(
                "CREATE INDEX idx_audit_action ON $TABLE_AUDIT($COL_ACTION)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // En producción, migrar; para preview, recrear
            db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIT")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "ordia_context_audit.db"
        private const val DB_VERSION = 1
        private const val TABLE_AUDIT = "context_audit"
        private const val PREFS_SALT = "ordia_audit_salt"
        private const val KEY_SALT = "audit_salt"

        private const val COL_ID = "_id"
        private const val COL_KIND = "kind"
        private const val COL_TITLE_HASH = "title_hash"
        private const val COL_SOURCE = "source"
        private const val COL_CONFIDENCE = "confidence"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_ACTION = "action"
        private const val COL_PREVIOUS_HASH = "previous_hash"
        private const val COL_REASON = "reason"

        /** Retención por defecto: 30 días */
        private const val DEFAULT_RETENTION_MS = 30L * 86_400_000L
    }
}

/** Razón de descarte */
enum class DiscardReason {
    LOW_CONFIDENCE,
    PRIVACY_FILTER,
    BLOCKED_CONTENT,
    DUPLICATE,
    CASUAL_CHAT,
    USER_REJECTED
}

/** Una entrada del registro de auditoría */
data class AuditEntry(
    val id: Long,
    val kind: String,
    val titleHash: String,
    val source: String,
    val confidence: Float,
    val timestamp: Date,
    val action: String,
    val reason: String? = null
)

/** Estadísticas resumidas del registro */
data class AuditStats(
    val totalEntries: Int,
    val byKind: Map<String, Int>,
    val byAction: Map<String, Int>,
    val bySource: Map<String, Int>,
    val averageConfidence: Float,
    val retentionDays: Long
)
