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
import com.ordia.app.data.preferences.PreferencesRepository
import com.ordia.app.data.preferences.UserPreferences
import com.ordia.app.reminders.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupManager(
    private val database: OrdiaDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val reminderScheduler: ReminderScheduler
) {
    private val operationMutex = Mutex()

    /**
     * Construye el JSON de la copia (sin adquirir el mutex).
     *
     * ORD-031: desde la versión 4 el JSON incluye un campo "checksum" con el
     * SHA-256 del contenido SIN el propio campo. Se añade en último lugar
     * para que al verificar (parsear, quitar "checksum" y reserializar) el
     * resto de claves conserve el orden original.
     */
    private suspend fun buildExportJson(): String {
        val root = JSONObject()
            .put("format", "ordia-backup")
            .put("version", CURRENT_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("preferences", preferencesRepository.exportJson())
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
        val contentJson = root.toString(2)
        require(BackupSecurityRules.inputSizeAllowed(contentJson.toByteArray(Charsets.UTF_8).size)) {
            "La copia supera el límite seguro de 10 MB. Reduce notas o adjuntos registrados antes de exportar."
        }
        // Checksum del contenido sin el propio campo (ORD-031).
        root.put("checksum", BackupSecurityRules.sha256Hex(contentJson.toByteArray(Charsets.UTF_8)))
        val final = root.toString(2)
        require(BackupSecurityRules.inputSizeAllowed(final.toByteArray(Charsets.UTF_8).size)) {
            "La copia supera el límite seguro de 10 MB. Reduce notas o adjuntos registrados antes de exportar."
        }
        return final
    }

    suspend fun exportJson(): String = operationMutex.withLock {
        withContext(Dispatchers.IO) { buildExportJson() }
    }

    suspend fun importJson(raw: String): ImportResult = operationMutex.withLock {
        withContext(Dispatchers.IO) {
        val rawBytes = raw.toByteArray(Charsets.UTF_8)
        if (!BackupSecurityRules.inputSizeAllowed(rawBytes.size)) {
            return@withContext ImportResult(false, "La copia está vacía o supera el límite de 10 MB.")
        }
        BackupSecurityRules.validateJsonEnvelope(raw)?.let { return@withContext ImportResult(false, it) }
        val root = runCatching { JSONObject(raw) }
            .getOrElse { return@withContext ImportResult(false, "El archivo no contiene JSON válido.") }
        val format = runCatching { root.get("format") }.getOrNull()
        if (format !is String || format != "ordia-backup") {
            return@withContext ImportResult(false, "El archivo no es una copia de seguridad de Ordia.")
        }
        val rawVersion = runCatching { root.get("version") }.getOrNull()
        if (rawVersion !is Number || rawVersion.toDouble() != rawVersion.toInt().toDouble()) {
            return@withContext ImportResult(false, "La versión de la copia no es un entero válido.")
        }
        val version = rawVersion.toInt()
        if (!BackupSecurityRules.supportsVersion(version)) {
            return@withContext ImportResult(false, "La versión de la copia ($version) no es compatible.")
        }
        val allowedTopLevel = BackupSecurityRules.requiredCollections + setOf("format", "version", "createdAt", "preferences", "checksum")
        val topLevelKeys = buildSet {
            val iterator = root.keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        val unexpectedTopLevel = topLevelKeys - allowedTopLevel
        if (unexpectedTopLevel.isNotEmpty()) {
            return@withContext ImportResult(false, "La copia contiene secciones desconocidas: ${unexpectedTopLevel.sorted().joinToString()}.")
        }
        if (!root.has("createdAt") || root.isNull("createdAt")) {
            return@withContext ImportResult(false, "La copia no contiene su fecha de creación.")
        }
        runCatching {
            val createdAt = root.requiredLong("createdAt")
            require(createdAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
        }.getOrElse { return@withContext ImportResult(false, "La fecha de creación de la copia no es válida.") }

        // ORD-031: desde la versión 4 el checksum es obligatorio. Se verifica
        // sobre el contenido sin el propio campo, detectando corrupción o
        // modificación del archivo. Las versiones 2-3 (sin checksum) se
        // aceptan por compatibilidad como formato heredado.
        if (version >= BackupSecurityRules.CHECKSUM_VERSION) {
            val checksum = runCatching { root.getString("checksum") }.getOrNull()
            if (checksum == null || !BackupSecurityRules.isValidChecksumFormat(checksum)) {
                return@withContext ImportResult(false, "La copia no contiene un checksum SHA-256 válido. El archivo está dañado o fue modificado.")
            }
            root.remove("checksum")
            val reserialized = runCatching { root.toString(2) }
                .getOrElse { return@withContext ImportResult(false, "La copia no pudo verificarse (JSON inválido).") }
            val actualChecksum = BackupSecurityRules.sha256Hex(reserialized.toByteArray(Charsets.UTF_8))
            if (actualChecksum != checksum) {
                return@withContext ImportResult(false, "La copia no supera la verificación de integridad. El archivo está dañado o fue modificado.")
            }
        }

        val presentCollections = BackupSecurityRules.requiredCollections.filterTo(mutableSetOf()) { root.has(it) }
        if (!BackupSecurityRules.hasAllCollections(presentCollections)) {
            val missing = (BackupSecurityRules.requiredCollections - presentCollections).sorted().joinToString()
            return@withContext ImportResult(false, "La copia está incompleta. Faltan: $missing.")
        }
        if (version >= 3 && root.optJSONObject("preferences") == null) {
            return@withContext ImportResult(false, "La copia no contiene los ajustes de Ordia.")
        }

        var oldPreferences: UserPreferences? = null
        var preferencesApplied = false
        try {
            // Parse and validate every value before changing DataStore or Room.
            val projects = root.requiredArray("projects").validatedMap("projects") { it.toProject() }
            val tasks = root.requiredArray("tasks").validatedMap("tasks") { it.toTask() }
            val notes = root.requiredArray("notes").validatedMap("notes") { it.toNote() }
            val habits = root.requiredArray("habits").validatedMap("habits") { it.toHabit() }
            val habitLogs = root.requiredArray("habitLogs").validatedMap("habitLogs") { it.toHabitLog() }
            val focusSessions = root.requiredArray("focusSessions").validatedMap("focusSessions") { it.toFocusSession() }
            val routines = root.requiredArray("routines").validatedMap("routines") { it.toRoutine() }
            val routineSteps = root.requiredArray("routineSteps").validatedMap("routineSteps") { it.toRoutineStep() }
            val tags = root.requiredArray("tags").validatedMap("tags") { it.toTag() }
            val taskTags = root.requiredArray("taskTags").validatedMap("taskTags") { it.toTaskTag() }
            val attachments = root.requiredArray("attachments").validatedMap("attachments") { it.toAttachment() }
            val total = listOf(
                projects.size, tasks.size, notes.size, habits.size, habitLogs.size,
                focusSessions.size, routines.size, routineSteps.size, tags.size,
                taskTags.size, attachments.size
            ).sum()
            require(BackupSecurityRules.totalSizeAllowed(total)) { "La copia contiene demasiados registros." }
            validateRelationships(
                projects, tasks, notes, habits, habitLogs, focusSessions,
                routines, routineSteps, tags, taskTags, attachments
            )
            val restoredPreferences = if (version >= 3) {
                preferencesRepository.decodeBackupJson(root.getJSONObject("preferences"))
            } else null

            // DataStore and Room cannot share one transaction. Apply preferences first and compensate
            // with the previous snapshot if Room rejects any row.
            if (restoredPreferences != null) {
                oldPreferences = preferencesRepository.snapshot()
                preferencesRepository.restoreSnapshot(restoredPreferences, allowGuardianEnabled = false)
                preferencesApplied = true
            }

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

                database.projectDao().insertAll(projects)
                database.taskDao().insertAll(tasks)
                database.noteDao().insertAll(notes)
                database.habitDao().insertAll(habits)
                database.habitLogDao().insertAll(habitLogs)
                database.focusSessionDao().insertAll(focusSessions)
                database.routineDao().insertAll(routines)
                database.routineStepDao().insertAll(routineSteps)
                database.tagDao().insertAll(tags)
                database.taskTagDao().insertAll(taskTags)
                database.attachmentDao().insertAll(attachments)
            }

            val reminderWarning = runCatching {
                reminderScheduler.cancelAllAndAwait()
                val now = System.currentTimeMillis()
                tasks.asSequence()
                    .filter { !it.completed && !it.archived }
                    .filter { (it.reminderAt ?: it.dueAt)?.let { trigger -> trigger > now } == true }
                    .forEach(reminderScheduler::schedule)
            }.exceptionOrNull()

            ImportResult(
                true,
                if (reminderWarning == null) {
                    "Copia restaurada correctamente. Se reconstruyeron los recordatorios futuros; abre Ajustes para reactivar el guardián flotante."
                } else {
                    "Copia restaurada, pero Android no pudo reconstruir todos los recordatorios. Revisa las tareas programadas."
                }
            )
        } catch (error: Exception) {
            val rollbackFailure = if (preferencesApplied) {
                oldPreferences?.let { previous ->
                    runCatching { preferencesRepository.restoreSnapshot(previous, allowGuardianEnabled = true) }.exceptionOrNull()
                }
            } else null
            val suffix = if (rollbackFailure == null) "" else " Los datos no cambiaron, pero revisa los ajustes de la aplicación."
            ImportResult(false, "No se pudo restaurar la copia: ${error.message ?: "error desconocido"}.$suffix")
        }
        }
    }

    data class ImportResult(val success: Boolean, val message: String)

    companion object {
        private const val CURRENT_VERSION = 4
    }
}

private fun validateRelationships(
    projects: List<ProjectEntity>,
    tasks: List<TaskEntity>,
    notes: List<NoteEntity>,
    habits: List<HabitEntity>,
    habitLogs: List<HabitLogEntity>,
    focusSessions: List<FocusSessionEntity>,
    routines: List<RoutineEntity>,
    routineSteps: List<RoutineStepEntity>,
    tags: List<TagEntity>,
    taskTags: List<TaskTagCrossRef>,
    attachments: List<AttachmentEntity>
) {
    fun requirePositiveUnique(label: String, ids: List<Long>): Set<Long> {
        require(ids.all { it > 0L }) { "$label contiene identificadores inválidos." }
        require(ids.toSet().size == ids.size) { "$label contiene identificadores duplicados." }
        return ids.toSet()
    }
    val projectIds = requirePositiveUnique("Proyectos", projects.map { it.id })
    val taskIds = requirePositiveUnique("Tareas", tasks.map { it.id })
    val noteIds = requirePositiveUnique("Notas", notes.map { it.id })
    val habitIds = requirePositiveUnique("Hábitos", habits.map { it.id })
    requirePositiveUnique("Sesiones", focusSessions.map { it.id })
    val routineIds = requirePositiveUnique("Rutinas", routines.map { it.id })
    requirePositiveUnique("Pasos de rutina", routineSteps.map { it.id })
    val tagIds = requirePositiveUnique("Etiquetas", tags.map { it.id })
    requirePositiveUnique("Adjuntos", attachments.map { it.id })
    require(tags.map { it.name }.toSet().size == tags.size) { "La copia contiene etiquetas duplicadas." }

    tasks.forEach { task ->
        require(task.projectId == null || task.projectId in projectIds) { "Una tarea referencia un proyecto inexistente." }
        require(task.parentTaskId == null || (task.parentTaskId in taskIds && task.parentTaskId != task.id)) {
            "Una tarea tiene una relación padre inválida."
        }
        require(task.startAt == null || task.dueAt == null || task.startAt <= task.dueAt) {
            "Una tarea comienza después de su vencimiento."
        }
        require(task.completed == (task.status == TaskStatus.COMPLETED)) {
            "Una tarea tiene un estado de finalización incoherente."
        }
        require(task.completed || task.completedAt == null) { "Una tarea pendiente contiene una fecha de finalización." }
        require(!task.completed || task.completedAt != null) { "Una tarea completada no contiene fecha de finalización." }
        require(task.createdAt <= task.updatedAt) { "Una tarea fue actualizada antes de ser creada." }
        require(task.completedAt == null || task.completedAt >= task.createdAt) { "Una tarea se completó antes de ser creada." }
        if (task.recurrence == RecurrenceFrequency.WEEKLY) {
            require(BackupSecurityRules.parseUniqueDayList(task.recurrenceDays, 1..7) != null) {
                "Una tarea semanal contiene días de recurrencia inválidos."
            }
        } else {
            require(task.recurrenceDays.isBlank()) { "Una tarea no semanal contiene días de recurrencia inesperados." }
        }
    }
    require(!BackupSecurityRules.hasParentCycle(tasks.associate { it.id to it.parentTaskId })) { "La copia contiene un ciclo entre subtareas." }
    projects.forEach { project -> require(project.createdAt <= project.updatedAt) { "Un proyecto fue actualizado antes de ser creado." } }
    notes.forEach { note ->
        require(note.projectId == null || note.projectId in projectIds) { "Una nota referencia un proyecto inexistente." }
        require(note.createdAt <= note.updatedAt) { "Una nota fue actualizada antes de ser creada." }
    }
    habits.forEach { habit ->
        require(habit.createdAt <= habit.updatedAt) { "Un hábito fue actualizado antes de ser creado." }
        val allowed = if (habit.frequency == HabitFrequency.MONTHLY) 1..31 else 1..7
        require(BackupSecurityRules.parseUniqueDayList(habit.activeDays, allowed) != null) {
            "Un hábito contiene días activos inválidos."
        }
    }
    habitLogs.forEach { log ->
        require(log.habitId in habitIds) { "Un registro referencia un hábito inexistente." }
    }
    require(!BackupSecurityRules.hasDuplicatePairs(habitLogs.map { it.habitId to it.epochDay })) {
        "La copia contiene registros de hábitos duplicados."
    }
    focusSessions.forEach { session ->
        require(session.taskId == null || session.taskId in taskIds) { "Una sesión referencia una tarea inexistente." }
        require(session.startedAt >= 0L) { "Una sesión contiene una fecha inicial inválida." }
        require(session.endedAt == null || session.endedAt >= session.startedAt) { "Una sesión termina antes de comenzar." }
        require(!session.completed || session.endedAt != null) { "Una sesión completada no contiene hora de cierre." }
        if (session.endedAt == null) {
            require(session.actualMinutes == 0) { "Una sesión abierta contiene tiempo realizado." }
        } else {
            val measured = ((session.endedAt - session.startedAt) / 60_000L).toInt().coerceAtLeast(0)
            require(session.actualMinutes == measured) { "El tiempo registrado de una sesión no coincide con su duración real." }
        }
    }
    routines.forEach { routine -> require(routine.createdAt <= routine.updatedAt) { "Una rutina fue actualizada antes de ser creada." } }
    routineSteps.forEach { step -> require(step.routineId in routineIds) { "Un paso referencia una rutina inexistente." } }
    require(routineSteps.groupBy { it.routineId }.values.all { steps ->
        steps.map { it.position }.toSet().size == steps.size
    }) { "Una rutina contiene posiciones de pasos duplicadas." }
    taskTags.forEach { link ->
        require(link.taskId in taskIds && link.tagId in tagIds) { "Una relación de etiqueta es inválida." }
    }
    require(!BackupSecurityRules.hasDuplicatePairs(taskTags.map { it.taskId to it.tagId })) {
        "La copia contiene relaciones de etiquetas duplicadas."
    }
    attachments.forEach { attachment ->
        val ownerExists = when (attachment.ownerType) {
            AttachmentOwnerType.TASK -> attachment.ownerId in taskIds
            AttachmentOwnerType.NOTE -> attachment.ownerId in noteIds
            AttachmentOwnerType.PROJECT -> attachment.ownerId in projectIds
        }
        require(ownerExists) { "Un adjunto referencia un elemento inexistente." }
    }
}



private val REQUIRED_ITEM_FIELDS = mapOf(
    "projects" to setOf("id", "name", "description", "colorHex", "icon", "status", "targetDate", "archived", "createdAt", "updatedAt"),
    "tasks" to setOf("id", "title", "details", "projectId", "parentTaskId", "startAt", "dueAt", "reminderAt", "durationMinutes", "priority", "status", "completed", "completedAt", "recurrence", "recurrenceInterval", "recurrenceDays", "sortOrder", "flagged", "archived", "createdAt", "updatedAt"),
    "notes" to setOf("id", "title", "body", "blocksData", "projectId", "pinned", "archived", "createdAt", "updatedAt"),
    "habits" to setOf("id", "title", "details", "frequency", "activeDays", "targetPerPeriod", "reminderMinutes", "colorHex", "icon", "archived", "createdAt", "updatedAt"),
    "habitLogs" to setOf("habitId", "epochDay", "count", "completedAt"),
    "focusSessions" to setOf("id", "taskId", "startedAt", "endedAt", "plannedMinutes", "actualMinutes", "completed", "notes"),
    "routines" to setOf("id", "name", "description", "colorHex", "archived", "createdAt", "updatedAt"),
    "routineSteps" to setOf("id", "routineId", "title", "durationMinutes", "position"),
    "tags" to setOf("id", "name", "colorHex"),
    "taskTags" to setOf("taskId", "tagId"),
    "attachments" to setOf("id", "ownerType", "ownerId", "uri", "displayName", "mimeType", "sizeBytes", "createdAt")
)

private fun JSONObject.requiredArray(name: String): JSONArray =
    optJSONArray(name) ?: error("La colección $name no es una lista válida.")

private inline fun <T> JSONArray.validatedMap(name: String, transform: (JSONObject) -> T): List<T> {
    require(BackupSecurityRules.collectionSizeAllowed(length())) { "La colección $name es demasiado grande." }
    return buildList(length()) {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: error("$name[$index] no es un objeto válido.")
            val required = REQUIRED_ITEM_FIELDS[name].orEmpty()
            val missing = required.filterNot(item::has)
            require(missing.isEmpty()) { "$name[$index] está incompleto. Faltan: ${missing.sorted().joinToString()}." }
            add(transform(item))
        }
    }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = apply {
    if (value == null) put(name, JSONObject.NULL) else put(name, value)
}
private fun JSONObject.longOrNull(name: String): Long? =
    if (isNull(name) || !has(name)) null else requiredLong(name)

private fun JSONObject.epochMillisOrNull(name: String): Long? =
    longOrNull(name)?.also {
        require(it in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS) { "$name contiene una fecha inválida." }
    }

private fun JSONObject.requiredLong(name: String): Long {
    require(has(name) && !isNull(name)) { "Falta el valor $name." }
    val value = get(name)
    require(value is Number) { "$name debe ser numérico." }
    val asDouble = value.toDouble()
    val asLong = value.toLong()
    require(asDouble.isFinite() && asDouble == asLong.toDouble()) { "$name debe ser un entero." }
    return asLong
}

private fun JSONObject.longValue(name: String, fallback: Long, range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Long {
    val value = if (!has(name) || isNull(name)) fallback else requiredLong(name)
    require(value in range) { "$name está fuera de rango." }
    return value
}

private fun JSONObject.intValue(name: String, fallback: Int, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Int {
    val value = longValue(name, fallback.toLong())
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$name está fuera de rango." }
    val result = value.toInt()
    require(result in range) { "$name está fuera de rango." }
    return result
}

private fun JSONObject.booleanValue(name: String, fallback: Boolean): Boolean {
    if (!has(name) || isNull(name)) return fallback
    val value = get(name)
    require(value is Boolean) { "$name debe ser verdadero o falso." }
    return value
}

private fun JSONObject.text(name: String, max: Int, fallback: String = ""): String {
    if (!has(name) || isNull(name)) return fallback
    val value = get(name)
    require(value is String) { "$name debe ser texto." }
    require(value.length <= max) { "$name supera el tamaño permitido." }
    require(BackupSecurityRules.hasValidUnicodeScalars(value)) { "$name contiene Unicode inválido." }
    return value
}

private fun JSONObject.requiredText(name: String, max: Int): String =
    text(name, max).also {
        require(it == it.trim() && it.isNotBlank() && it.none(Char::isISOControl)) {
            "$name contiene espacios exteriores, controles o está vacío."
        }
    }

private fun JSONObject.color(name: String, fallback: String): String {
    val value = text(name, 9, fallback)
    require(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(value)) { "$name no contiene un color válido." }
    return value
}

private fun JSONObject.attachmentUri(): String {
    val value = requiredText("uri", 20_000)
    val parsed = runCatching { URI(value) }.getOrNull() ?: error("El URI de un adjunto no es válido.")
    require(parsed.scheme?.lowercase() == "content" && !parsed.isOpaque && !parsed.authority.isNullOrBlank()) {
        "El URI de un adjunto no es un content URI jerárquico válido."
    }
    require(parsed.fragment == null) { "El URI de un adjunto contiene un fragmento no permitido." }
    return value
}

private fun ProjectEntity.toJson() = JSONObject()
    .put("id", id).put("name", name).put("description", description).put("colorHex", colorHex)
    .put("icon", icon).put("status", status.name).putNullable("targetDate", targetDate).put("archived", archived)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toProject() = ProjectEntity(
    id = requiredLong("id"), name = requiredText("name", 500), description = text("description", 50_000),
    colorHex = color("colorHex", "#C9A86A"), icon = text("icon", 64, "folder"),
    status = enumValue("status", ProjectStatus.ACTIVE), targetDate = epochMillisOrNull("targetDate"),
    archived = booleanValue("archived", false), createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
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
    id = requiredLong("id"), title = requiredText("title", 2_000), details = text("details", 100_000),
    projectId = longOrNull("projectId"), parentTaskId = longOrNull("parentTaskId"),
    startAt = epochMillisOrNull("startAt"), dueAt = epochMillisOrNull("dueAt"), reminderAt = epochMillisOrNull("reminderAt"),
    durationMinutes = intValue("durationMinutes", 25, 0..100_000),
    priority = enumValue("priority", TaskPriority.NORMAL),
    status = enumValue("status", TaskStatus.INBOX), completed = booleanValue("completed", false),
    completedAt = epochMillisOrNull("completedAt"), recurrence = enumValue("recurrence", RecurrenceFrequency.NONE),
    recurrenceInterval = intValue("recurrenceInterval", 1, 1..10_000),
    recurrenceDays = text("recurrenceDays", 128), sortOrder = intValue("sortOrder", 0),
    flagged = booleanValue("flagged", false), archived = booleanValue("archived", false),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS), updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun NoteEntity.toJson() = JSONObject().put("id", id).put("title", title).put("body", body).put("blocksData", blocksData)
    .putNullable("projectId", projectId).put("pinned", pinned).put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toNote() = NoteEntity(
    id = requiredLong("id"), title = requiredText("title", 2_000), body = text("body", 500_000),
    blocksData = text("blocksData", 1_000_000), projectId = longOrNull("projectId"),
    pinned = booleanValue("pinned", false), archived = booleanValue("archived", false),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS), updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun HabitEntity.toJson() = JSONObject().put("id", id).put("title", title).put("details", details).put("frequency", frequency.name)
    .put("activeDays", activeDays).put("targetPerPeriod", targetPerPeriod).putNullable("reminderMinutes", reminderMinutes)
    .put("colorHex", colorHex).put("icon", icon).put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toHabit() = HabitEntity(
    id = requiredLong("id"), title = requiredText("title", 2_000), details = text("details", 50_000),
    frequency = enumValue("frequency", HabitFrequency.DAILY), activeDays = text("activeDays", 128),
    targetPerPeriod = intValue("targetPerPeriod", 1, 1..100_000),
    reminderMinutes = if (!has("reminderMinutes") || isNull("reminderMinutes")) null else intValue("reminderMinutes", 0, 0..1439),
    colorHex = color("colorHex", "#8F9D78"), icon = text("icon", 64, "spark"),
    archived = booleanValue("archived", false), createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun HabitLogEntity.toJson() = JSONObject().put("habitId", habitId).put("epochDay", epochDay).put("count", count).put("completedAt", completedAt)
private fun JSONObject.toHabitLog() = HabitLogEntity(
    habitId = requiredLong("habitId"), epochDay = longValue("epochDay", 0L, -1_000_000L..1_000_000L), count = intValue("count", 1, 1..100_000),
    completedAt = longValue("completedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun FocusSessionEntity.toJson() = JSONObject().put("id", id).putNullable("taskId", taskId).put("startedAt", startedAt)
    .putNullable("endedAt", endedAt).put("plannedMinutes", plannedMinutes).put("actualMinutes", actualMinutes).put("completed", completed).put("notes", notes)
private fun JSONObject.toFocusSession() = FocusSessionEntity(
    id = requiredLong("id"), taskId = longOrNull("taskId"), startedAt = longValue("startedAt", 0L, 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS), endedAt = epochMillisOrNull("endedAt"),
    plannedMinutes = intValue("plannedMinutes", 25, 0..100_000),
    actualMinutes = intValue("actualMinutes", 0, 0..100_000), completed = booleanValue("completed", false),
    notes = text("notes", 50_000)
)

private fun RoutineEntity.toJson() = JSONObject().put("id", id).put("name", name).put("description", description).put("colorHex", colorHex)
    .put("archived", archived).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toRoutine() = RoutineEntity(
    id = requiredLong("id"), name = requiredText("name", 2_000), description = text("description", 50_000),
    colorHex = color("colorHex", "#A995C3"), archived = booleanValue("archived", false),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS), updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun RoutineStepEntity.toJson() = JSONObject().put("id", id).put("routineId", routineId).put("title", title).put("durationMinutes", durationMinutes).put("position", position)
private fun JSONObject.toRoutineStep() = RoutineStepEntity(
    id = requiredLong("id"), routineId = requiredLong("routineId"), title = requiredText("title", 2_000),
    durationMinutes = intValue("durationMinutes", 5, 0..100_000), position = intValue("position", 0, 0..100_000)
)

private fun TagEntity.toJson() = JSONObject().put("id", id).put("name", name).put("colorHex", colorHex)
private fun JSONObject.toTag() = TagEntity(requiredLong("id"), requiredText("name", 500), color("colorHex", "#9A8F7F"))
private fun TaskTagCrossRef.toJson() = JSONObject().put("taskId", taskId).put("tagId", tagId)
private fun JSONObject.toTaskTag() = TaskTagCrossRef(requiredLong("taskId"), requiredLong("tagId"))

private fun AttachmentEntity.toJson() = JSONObject().put("id", id).put("ownerType", ownerType.name).put("ownerId", ownerId)
    .put("uri", uri).put("displayName", displayName).put("mimeType", mimeType).put("sizeBytes", sizeBytes).put("createdAt", createdAt)
private fun JSONObject.toAttachment() = AttachmentEntity(
    id = requiredLong("id"), ownerType = enumValue("ownerType", AttachmentOwnerType.NOTE), ownerId = requiredLong("ownerId"),
    uri = attachmentUri(), displayName = requiredText("displayName", 2_000),
    mimeType = text("mimeType", 500, "application/octet-stream").also {
        require(Regex("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+*-]+$").matches(it)) { "El tipo MIME no es válido." }
    },
    sizeBytes = longValue("sizeBytes", 0L, 0L..Long.MAX_VALUE), createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private inline fun <reified T : Enum<T>> JSONObject.enumValue(name: String, fallback: T): T {
    if (!has(name) || isNull(name)) return fallback
    val value = text(name, 64)
    return runCatching { enumValueOf<T>(value) }
        .getOrElse { error("$name contiene un valor desconocido.") }
}
