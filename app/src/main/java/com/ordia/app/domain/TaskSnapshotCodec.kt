package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializa [TaskEntity] a JSON (org.json, disponible en Android y en tests JVM).
 *
 * Usado por el historial de automatizaciones para guardar el estado previo de
 * las tareas y poder deshacer un cambio (plan del día, replanificación, etc.).
 */
object TaskSnapshotCodec {

    fun encode(task: TaskEntity): JSONObject = JSONObject()
        .put("id", task.id)
        .put("title", task.title)
        .put("details", task.details)
        .put("projectId", task.projectId ?: JSONObject.NULL)
        .put("parentTaskId", task.parentTaskId ?: JSONObject.NULL)
        .put("startAt", task.startAt ?: JSONObject.NULL)
        .put("dueAt", task.dueAt ?: JSONObject.NULL)
        .put("reminderAt", task.reminderAt ?: JSONObject.NULL)
        .put("durationMinutes", task.durationMinutes)
        .put("priority", task.priority.name)
        .put("status", task.status.name)
        .put("completed", task.completed)
        .put("completedAt", task.completedAt ?: JSONObject.NULL)
        .put("recurrence", task.recurrence.name)
        .put("recurrenceInterval", task.recurrenceInterval)
        .put("recurrenceDays", task.recurrenceDays)
        .put("sortOrder", task.sortOrder)
        .put("flagged", task.flagged)
        .put("archived", task.archived)
        .put("createdAt", task.createdAt)
        .put("updatedAt", task.updatedAt)

    fun encodeMap(tasks: Map<Long, TaskEntity>): String {
        val root = JSONObject()
        tasks.forEach { (id, task) -> root.put(id.toString(), encode(task)) }
        return root.toString()
    }

    fun decodeMap(json: String): Map<Long, TaskEntity> {
        if (json.isBlank()) return emptyMap()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<Long, TaskEntity>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toLongOrNull() ?: continue
            val obj = root.optJSONObject(key) ?: continue
            // La clave es el id autoritativo (es el que usa esta función para
            // result[id] y el que undoLastAutomation pasa al repositorio). Si el
            // "id" embebido falta o diverge (snapshot antiguo/truncado/migrado),
            // optLong caería a 0 o a un valor distinto y la restauración apuntaría
            // a la fila equivocada. Se impone la clave.
            val task = decodeTask(obj)?.copy(id = id) ?: continue
            result[id] = task
        }
        return result
    }

    fun encodeIds(ids: List<Long>): String {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeIds(json: String): List<Long> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optLong(i).takeIf { it > 0L }?.let { add(it) }
            }
        }
    }

    private fun decodeTask(obj: JSONObject): TaskEntity? = runCatching {
        TaskEntity(
            id = obj.optLong("id"),
            title = obj.optString("title"),
            details = obj.optString("details"),
            projectId = optNullableLong(obj, "projectId"),
            parentTaskId = optNullableLong(obj, "parentTaskId"),
            startAt = optNullableLong(obj, "startAt"),
            dueAt = optNullableLong(obj, "dueAt"),
            reminderAt = optNullableLong(obj, "reminderAt"),
            durationMinutes = obj.optInt("durationMinutes", 25),
            priority = enumOr(obj.optString("priority"), TaskPriority.NORMAL),
            status = enumOr(obj.optString("status"), TaskStatus.INBOX),
            completed = obj.optBoolean("completed"),
            completedAt = optNullableLong(obj, "completedAt"),
            recurrence = enumOr(obj.optString("recurrence"), RecurrenceFrequency.NONE),
            recurrenceInterval = obj.optInt("recurrenceInterval", 1),
            recurrenceDays = obj.optString("recurrenceDays"),
            sortOrder = obj.optInt("sortOrder"),
            flagged = obj.optBoolean("flagged"),
            archived = obj.optBoolean("archived"),
            createdAt = obj.optLong("createdAt"),
            updatedAt = obj.optLong("updatedAt")
        )
    }.getOrNull()

    private fun optNullableLong(obj: JSONObject, key: String): Long? =
        if (obj.isNull(key)) null else obj.optLong(key)

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
