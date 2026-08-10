package com.ordia.app.context.external

import android.content.Context
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.repository.NoteRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.reminders.ReminderScheduler
import java.security.MessageDigest
import org.json.JSONObject

class RepositoryContextActionPersistence(
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository
) : ContextActionPersistence {
    override suspend fun createTask(draft: ContextTaskDraft): Long = taskRepository.add(
        TaskEntity(
            title = draft.title,
            dueAt = draft.dueAt,
            status = if (draft.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED
        )
    )

    override suspend fun createNote(draft: ContextNoteDraft): Long = noteRepository.add(
        NoteEntity(title = draft.title)
    )

    override suspend fun delete(entityType: ContextActionEntityType, entityId: Long) {
        when (entityType) {
            ContextActionEntityType.TASK -> taskRepository.deletePermanently(entityId)
            ContextActionEntityType.NOTE -> noteRepository.deletePermanently(entityId)
        }
    }
}

class WorkManagerContextActionReminderScheduler(
    private val scheduler: ReminderScheduler
) : ContextActionReminderScheduler {
    override fun schedule(taskId: Long, triggerAt: Long) {
        scheduler.scheduleAt(taskId, triggerAt)
    }
}

/**
 * Guarda solo IDs, tipo y estado del recordatorio. No persiste titulo ni texto
 * de la sugerencia. Los recibos caducan para no crecer indefinidamente.
 */
class SharedPreferencesContextActionReceiptStore(context: Context) : ContextActionReceiptStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun get(suggestionId: String): ContextActionReceipt? = synchronized(lock) {
        prefs.getString(keyFor(suggestionId), null)?.let(::decode)
            ?.takeIf { it.suggestionId == suggestionId }
    }

    override fun save(receipt: ContextActionReceipt): Boolean = synchronized(lock) {
        runCatching {
            val now = System.currentTimeMillis()
            val current = prefs.all.mapNotNull { (key, value) ->
                (value as? String)?.let(::decode)?.let { key to it }
            }
            val staleKeys = current
                .filter { (_, stored) -> now - stored.createdAt > MAX_RECEIPT_AGE_MS }
                .map { it.first }
                .toMutableSet()
            val survivors = current
                .filterNot { it.first in staleKeys || it.first == keyFor(receipt.suggestionId) }
                .sortedBy { it.second.createdAt }
            survivors
                .take((survivors.size - (MAX_RECEIPTS - 1)).coerceAtLeast(0))
                .forEach { staleKeys += it.first }

            prefs.edit().apply {
                staleKeys.forEach { remove(it) }
                putString(keyFor(receipt.suggestionId), encode(receipt))
            }.commit()
        }.getOrDefault(false)
    }

    private fun encode(receipt: ContextActionReceipt): String = JSONObject()
        .put("suggestionId", receipt.suggestionId)
        .put("entityType", receipt.entityType.name)
        .put("entityId", receipt.entityId)
        .put("reminderAt", receipt.reminderAt ?: JSONObject.NULL)
        .put("reminderScheduled", receipt.reminderScheduled)
        .put("createdAt", receipt.createdAt)
        .toString()

    private fun decode(raw: String): ContextActionReceipt? = runCatching {
        val json = JSONObject(raw)
        val suggestionId = json.getString("suggestionId")
        val entityId = json.getLong("entityId")
        val createdAt = json.getLong("createdAt")
        require(suggestionId.isNotBlank() && entityId > 0L && createdAt >= 0L)
        ContextActionReceipt(
            suggestionId = suggestionId,
            entityType = ContextActionEntityType.valueOf(json.getString("entityType")),
            entityId = entityId,
            reminderAt = if (json.isNull("reminderAt")) null else json.getLong("reminderAt").takeIf { it > 0L },
            reminderScheduled = json.getBoolean("reminderScheduled"),
            createdAt = createdAt
        )
    }.getOrNull()

    private fun keyFor(suggestionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(suggestionId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS_NAME = "ordia_context_action_receipts"
        const val MAX_RECEIPTS = 100
        const val MAX_RECEIPT_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
    }
}
