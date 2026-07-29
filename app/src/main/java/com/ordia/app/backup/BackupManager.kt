package com.ordia.app.backup

import androidx.room.withTransaction
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.ProjectStatus
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.local.TaskTagCrossRef
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(private val database: OrdiaDatabase) {
    suspend fun exportJson(): String {
        val root = JSONObject()
            .put("format", "ordia-backup")
            .put("version", 2)
            .put("createdAt", System.currentTimeMillis())
        root.put("projects", JSONArray().apply { database.projectDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("tasks", JSONArray().apply { database.taskDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("notes", JSONArray().apply { database.noteDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("habits", JSONArray().apply { database.habitDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("habitLogs", JSONArray().apply { database.habitLogDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("focusSessions", JSONArray().apply { database.focusSessionDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("routines", JSONArray().apply { database.routineDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("routineSteps", JSONArray().apply { database.routineStepDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("tags", JSONArray().apply { database.tagDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("taskTags", JSONArray().apply { database.taskTagDao().getAllNow().forEach { put(it.toJson()) } })
        root.put("attachments", JSONArray().apply { database.attachmentDao().getAllNow().forEach { put(it.toJson()) } })
        return root.toString(2)
    }

    suspend fun importJson(raw: String): ImportResult {
        val root = runCatching { JSONObject(raw) }.getOrElse { return ImportResult(false, "El archivo no contiene JSON válido.") }
        if (root.optString("format") != "ordia-backup") return ImportResult(false, "El archivo no es una copia de seguridad de Ordia.")
        return runCatching {
            database.withTransaction {
                database.attachmentDao().deleteAll()
                database.taskTagDao().deleteAll()
                database.tagDao().deleteAll()
                database.routineStepDao().deleteAll()
                database.routineDao().deleteAll()
                database.focusSessionDao().deleteAll()
                database.habitLogDao().deleteAll()
                database.habitDao().deleteAll()
                database.noteDao().deleteAll()
                database.taskDao().deleteAll()
                database.projectDao().deleteAll()

                database.projectDao().insertAll(root.array("projects").mapObjects { it.toProject() })
                database.taskDao().insertAll(root.array("tasks").mapObjects { it.toTask() })
                database.noteDao().insertAll(root.array("notes").mapObjects { it.toNote() })
                database.habitDao().insertAll(root.array("habits").mapObjects { it.toHabit() })
                database.habitLogDao().insertAll(root.array("habitLogs").mapObjects { it.toHabitLog() })
                database.focusSessionDao().insertAll(root.array("focusSessions").mapObjects { it.toFocusSession() })
                database.routineDao().insertAll(root.array("routines").mapObjects { it.toRoutine() })
                database.routineStepDao().insertAll(root.array("routineSteps").mapObjects { it.toRoutineStep() })
                database.tagDao().insertAll(root.array("tags").mapObjects { it.toTag() })
                database.taskTagDao().insertAll(root.array("taskTags").mapObjects { it.toTaskTag() })
                database.attachmentDao().insertAll(root.array("attachments").mapObjects { it.toAttachment() })
            }
            ImportResult(true, "Copia restaurada correctamente.")
        }.getOrElse { ImportResult(false, "No se pudo restaurar la copia: ${it.message ?: "error desconocido"}") }
    }

    data class ImportResult(val success: Boolean, val message: String)
}

private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) add(transform(getJSONObject(index)))
}
private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = apply { if (value == null) put(name, JSONObject.NULL) else put(name, value) }
private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name) || !has(name)) null else optLong(name)

private fun ProjectEntity.toJson() = JSONObject()
    .put("id", id).put("name", name).put("description", description).put("colorHex", colorHex)
    .put("icon", icon).put("status", status.name).putNullable("targetDate", targetDate).put("archived", archived)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toProject() = ProjectEntity(
    id = optLong("id"), name = optString("name"), description = optString("description"),
    colorHex = optString("colorHex", "#C9A86A"), icon = optString("icon", "folder"),
    status = enumValue(optString("status"), ProjectStatus.ACTIVE), targetDate = longOrNull("targetDate"),
    archived = optBoolean("archived"), createdAt = optLong("createdAt", System.currentTimeMillis()),
    updatedAt = optLong("updatedAt", System.currentTimeMillis())
)

private fun TaskEntity.toJson() = JSONObject()
    .put("id", id).put("title", title).put("details", details).putNullable("projectId", projectId)
    .putNullable("parentTaskId", parentTaskId).putNullable("startAt", startAt).putNullable("dueAt", dueAt)
    .putNullable("reminderAt", reminderAt).put("durationMinutes", durationMinutes).put("priority", priority.name)
    .put("status", status.name).put("completed", completed).putNullable("completedAt", completedAt)
    .put("recurrence", recurrence.name).put("recurrenceInterval", recurrenceInterval).put("recurrenceDays", recurrenceDays)
    .put("sortOrder", sortOrder).put("flagged", flagged).put("archived", archived)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toTask() = TaskEntity(
    id = optLong("id"), title = optString("title"), details = optString("details"), projectId = longOrNull("projectId"),
    parentTaskId = longOrNull("parentTaskId"), startAt = longOrNull("startAt"), dueAt = longOrNull("dueAt"),
    reminderAt = longOrNull("reminderAt"), durationMinutes = optInt("durationMinutes", 25),
    priority = enumValue(optString("priority"), TaskPriority.NORMAL), status = enumValue(optString("status"), TaskStatus.INBOX),
    completed = optBoolean("completed"), completedAt = longOrNull("completedAt"),
    recurrence = enumValue(optString("recurrence"), RecurrenceFrequency.NONE), recurrenceInterval = optInt("recurrenceInterval", 1),
    recurrenceDays = optString("recurrenceDays"), sortOrder = optInt("sortOrder"), flagged = optBoolean("flagged"),
    archived = optBoolean("archived"), createdAt = optLong("createdAt", System.currentTimeMillis()),
    updatedAt = optLong("updatedAt", System.currentTimeMillis())
)

private fun NoteEntity.toJson() = JSONObject().put("id", id).put("title", title).put("body", body).put("blocksData", blocksData)
    .putNullable("projectId", projectId).put("pinned", pinned).put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toNote() = NoteEntity(
    id = optLong("id"), title = optString("title"), body = optString("body"), blocksData = optString("blocksData"),
    projectId = longOrNull("projectId"), pinned = optBoolean("pinned"), archived = optBoolean("archived"),
    createdAt = optLong("createdAt", System.currentTimeMillis()), updatedAt = optLong("updatedAt", System.currentTimeMillis())
)

private fun HabitEntity.toJson() = JSONObject().put("id", id).put("title", title).put("details", details).put("frequency", frequency.name)
    .put("activeDays", activeDays).put("targetPerPeriod", targetPerPeriod).putNullable("reminderMinutes", reminderMinutes)
    .put("colorHex", colorHex).put("icon", icon).put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toHabit() = HabitEntity(
    id = optLong("id"), title = optString("title"), details = optString("details"),
    frequency = enumValue(optString("frequency"), HabitFrequency.DAILY), activeDays = optString("activeDays"),
    targetPerPeriod = optInt("targetPerPeriod", 1), reminderMinutes = if (isNull("reminderMinutes")) null else optInt("reminderMinutes"),
    colorHex = optString("colorHex", "#8F9D78"), icon = optString("icon", "spark"), archived = optBoolean("archived"),
    createdAt = optLong("createdAt", System.currentTimeMillis()), updatedAt = optLong("updatedAt", System.currentTimeMillis())
)

private fun HabitLogEntity.toJson() = JSONObject().put("habitId", habitId).put("epochDay", epochDay).put("count", count).put("completedAt", completedAt)
private fun JSONObject.toHabitLog() = HabitLogEntity(optLong("habitId"), optLong("epochDay"), optInt("count", 1), optLong("completedAt", System.currentTimeMillis()))

private fun FocusSessionEntity.toJson() = JSONObject().put("id", id).putNullable("taskId", taskId).put("startedAt", startedAt)
    .putNullable("endedAt", endedAt).put("plannedMinutes", plannedMinutes).put("actualMinutes", actualMinutes).put("completed", completed).put("notes", notes)
private fun JSONObject.toFocusSession() = FocusSessionEntity(
    id = optLong("id"), taskId = longOrNull("taskId"), startedAt = optLong("startedAt"), endedAt = longOrNull("endedAt"),
    plannedMinutes = optInt("plannedMinutes", 25), actualMinutes = optInt("actualMinutes"), completed = optBoolean("completed"), notes = optString("notes")
)

private fun RoutineEntity.toJson() = JSONObject().put("id", id).put("name", name).put("description", description).put("colorHex", colorHex)
    .put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toRoutine() = RoutineEntity(
    id = optLong("id"), name = optString("name"), description = optString("description"), colorHex = optString("colorHex", "#A995C3"),
    archived = optBoolean("archived"), createdAt = optLong("createdAt", System.currentTimeMillis()), updatedAt = optLong("updatedAt", System.currentTimeMillis())
)

private fun RoutineStepEntity.toJson() = JSONObject().put("id", id).put("routineId", routineId).put("title", title).put("durationMinutes", durationMinutes).put("position", position)
private fun JSONObject.toRoutineStep() = RoutineStepEntity(optLong("id"), optLong("routineId"), optString("title"), optInt("durationMinutes", 5), optInt("position"))

private fun TagEntity.toJson() = JSONObject().put("id", id).put("name", name).put("colorHex", colorHex)
private fun JSONObject.toTag() = TagEntity(optLong("id"), optString("name"), optString("colorHex", "#9A8F7F"))
private fun TaskTagCrossRef.toJson() = JSONObject().put("taskId", taskId).put("tagId", tagId)
private fun JSONObject.toTaskTag() = TaskTagCrossRef(optLong("taskId"), optLong("tagId"))

private fun AttachmentEntity.toJson() = JSONObject().put("id", id).put("ownerType", ownerType.name).put("ownerId", ownerId)
    .put("uri", uri).put("displayName", displayName).put("mimeType", mimeType).put("sizeBytes", sizeBytes).put("createdAt", createdAt)
private fun JSONObject.toAttachment() = AttachmentEntity(
    id = optLong("id"), ownerType = enumValue(optString("ownerType"), AttachmentOwnerType.NOTE), ownerId = optLong("ownerId"),
    uri = optString("uri"), displayName = optString("displayName"), mimeType = optString("mimeType", "application/octet-stream"),
    sizeBytes = optLong("sizeBytes"), createdAt = optLong("createdAt", System.currentTimeMillis())
)

private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
