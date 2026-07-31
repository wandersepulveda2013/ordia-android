package com.ordia.app.backup

import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
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
import com.ordia.app.data.preferences.UserPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Fases observables del flujo de restauración para la UI. */
enum class RestorePhase { VALIDATING, CREATING_SAFETY_BACKUP, RESTORING, VERIFYING }

/** Resultado de la validación previa: datos parseados sin tocar la base. */
data class ValidatedBackup(
    val data: RestoreData,
    val preferences: UserPreferences?,
    val version: Int
)

/**
 * Coordina exportación, validación y restauración atómica de las copias.
 *
 * Contrato de seguridad del flujo de restore:
 *  1. [validateAndParse] valida TODO el archivo (formato, versión, checksum,
 *     campos, límites, identificadores duplicados y relaciones) antes de
 *     tocar datos.
 *  2. [writePreRestoreBackup] crea y VERIFICA el journal preventivo; si falla,
 *     [importBackup] se cancela y no modifica la base.
 *  3. [BackupStore.replaceAll] reemplaza los datos en UNA única transacción
 *     Room: cualquier fallo produce rollback total.
 *  4. [verifyRestored] relee lo persistido y solo entonces se informa éxito.
 */
class BackupManager(
    private val backupStore: BackupStore,
    private val preferences: BackupPreferences,
    private val reminderScheduler: ReminderSchedulerPort,
    private val preRestoreBackupFile: java.io.File
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
        val current = backupStore.readAll()
        val root = JSONObject()
            .put("format", "ordia-backup")
            .put("version", CURRENT_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("preferences", preferences.exportJson())
        root.put("projects", JSONArray().apply { current.projects.forEach { put(it.toJson()) } })
        root.put("tasks", JSONArray().apply { current.tasks.forEach { put(it.toJson()) } })
        root.put("notes", JSONArray().apply { current.notes.forEach { put(it.toJson()) } })
        root.put("habits", JSONArray().apply { current.habits.forEach { put(it.toJson()) } })
        root.put("habitLogs", JSONArray().apply { current.habitLogs.forEach { put(it.toJson()) } })
        root.put("focusSessions", JSONArray().apply { current.focusSessions.forEach { put(it.toJson()) } })
        root.put("routines", JSONArray().apply { current.routines.forEach { put(it.toJson()) } })
        root.put("routineSteps", JSONArray().apply { current.routineSteps.forEach { put(it.toJson()) } })
        root.put("tags", JSONArray().apply { current.tags.forEach { put(it.toJson()) } })
        root.put("taskTags", JSONArray().apply { current.taskTags.forEach { put(it.toJson()) } })
        root.put("attachments", JSONArray().apply { current.attachments.forEach { put(it.toJson()) } })
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

    /**
     * Flujo completo de restauración.
     *
     * @param raw contenido íntegro del archivo de copia
     * @param onPhase callback de progreso (se invoca en el orden
     *   VALIDATING → CREATING_SAFETY_BACKUP → RESTORING → VERIFYING)
     * @return [ImportResult.success] solo si la verificación posterior confirmó
     *   la persistencia; cualquier fallo deja la base sin cambios o, si el
     *   fallo ocurre tras el commit, se informa que no pudo verificarse.
     */
    suspend fun importBackup(raw: String, onPhase: (RestorePhase) -> Unit = {}): ImportResult =
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                onPhase(RestorePhase.VALIDATING)
                val validated = runCatching { validateAndParse(raw) }
                    .getOrElse {
                        return@withContext ImportResult(false, it.message ?: "La copia no es válida.")
                    }

                onPhase(RestorePhase.CREATING_SAFETY_BACKUP)
                if (!writePreRestoreBackup()) {
                    return@withContext ImportResult(
                        false,
                        "No se pudo crear el respaldo preventivo (${preRestoreBackupFile.name}) en el almacenamiento privado. " +
                            "La restauración se canceló y tus datos no se modificaron."
                    )
                }

                onPhase(RestorePhase.RESTORING)
                var oldPreferences: UserPreferences? = null
                var preferencesApplied = false
                try {
                    // DataStore y Room no comparten transacción: se aplican las
                    // preferencias primero y se compensan si Room rechaza algo.
                    if (validated.preferences != null) {
                        oldPreferences = preferences.snapshot()
                        preferences.restoreSnapshot(validated.preferences, allowGuardianEnabled = false)
                        preferencesApplied = true
                    }
                    // Reemplazo atómico: borrado + inserción en una transacción Room.
                    backupStore.replaceAll(validated.data)
                } catch (error: Exception) {
                    val rollbackFailure = if (preferencesApplied) {
                        oldPreferences?.let { previous ->
                            runCatching { preferences.restoreSnapshot(previous, allowGuardianEnabled = true) }.exceptionOrNull()
                        }
                    } else null
                    val suffix = if (rollbackFailure == null) "" else " Revisa los ajustes de la aplicación."
                    return@withContext ImportResult(
                        false,
                        "No se pudo aplicar la restauración. La base de datos conservó su estado anterior.$suffix"
                    )
                }

                val reminderWarning = runCatching {
                    reminderScheduler.cancelAllAndAwait()
                    val now = System.currentTimeMillis()
                    validated.data.tasks.asSequence()
                        .filter { !it.completed && !it.archived }
                        .filter { (it.reminderAt ?: it.dueAt)?.let { trigger -> trigger > now } == true }
                        .forEach(reminderScheduler::schedule)
                }.exceptionOrNull()

                onPhase(RestorePhase.VERIFYING)
                val verified = runCatching { verifyRestored(validated) }.getOrDefault(false)
                if (!verified) {
                    return@withContext ImportResult(
                        false,
                        "La restauración no pudo verificarse y no se confirma que haya terminado correctamente. " +
                            "Tus datos anteriores están en el respaldo preventivo ${preRestoreBackupFile.name} del almacenamiento privado."
                    )
                }

                val journalNote = "Se guardó un respaldo preventivo de tus datos anteriores (${preRestoreBackupFile.name}) en el almacenamiento privado."
                ImportResult(
                    true,
                    if (reminderWarning == null) {
                        "Copia restaurada correctamente. Se reconstruyeron los recordatorios futuros; abre Ajustes para reactivar el guardián flotante. $journalNote"
                    } else {
                        "Copia restaurada, pero Android no pudo reconstruir todos los recordatorios. Revisa las tareas programadas. $journalNote"
                    }
                )
            }
        }

    /**
     * Valida TODO el contenido de la copia sin modificar datos.
     * Lanza [IllegalArgumentException] con un mensaje claro y diferenciado.
     */
    private fun validateAndParse(raw: String): ValidatedBackup {
        val rawBytes = raw.toByteArray(Charsets.UTF_8)
        require(BackupSecurityRules.inputSizeAllowed(rawBytes.size)) {
            "La copia está vacía o supera el límite de 10 MB."
        }
        BackupSecurityRules.validateJsonEnvelope(raw)?.let { throw IllegalArgumentException(it) }
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("El archivo no contiene JSON válido.") }
        val format = runCatching { root.get("format") }.getOrNull()
        if (format !is String || format != "ordia-backup") {
            throw IllegalArgumentException("El archivo no es una copia de seguridad de Ordia.")
        }
        val rawVersion = runCatching { root.get("version") }.getOrNull()
        if (rawVersion !is Number || rawVersion.toDouble() != rawVersion.toInt().toDouble()) {
            throw IllegalArgumentException("La versión de la copia no es un entero válido.")
        }
        val version = rawVersion.toInt()
        if (!BackupSecurityRules.supportsVersion(version)) {
            throw IllegalArgumentException("La versión de la copia ($version) no es compatible.")
        }
        val allowedTopLevel = BackupSecurityRules.requiredCollections + setOf("format", "version", "createdAt", "preferences", "checksum")
        val topLevelKeys = buildSet {
            val iterator = root.keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        val unexpectedTopLevel = topLevelKeys - allowedTopLevel
        if (unexpectedTopLevel.isNotEmpty()) {
            throw IllegalArgumentException("La copia contiene secciones desconocidas: ${unexpectedTopLevel.sorted().joinToString()}.")
        }
        if (!root.has("createdAt") || root.isNull("createdAt")) {
            throw IllegalArgumentException("La copia no contiene su fecha de creación.")
        }
        runCatching {
            val createdAt = root.requiredLong("createdAt")
            require(createdAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
        }.getOrElse { throw IllegalArgumentException("La fecha de creación de la copia no es válida.") }

        // ORD-031: desde la versión 4 el checksum es obligatorio. Se verifica
        // sobre el contenido sin el propio campo, detectando corrupción o
        // modificación del archivo. Las versiones 2-3 (sin checksum) se
        // aceptan por compatibilidad como formato heredado.
        if (version >= BackupSecurityRules.CHECKSUM_VERSION) {
            val checksum = runCatching { root.getString("checksum") }.getOrNull()
            if (checksum == null || !BackupSecurityRules.isValidChecksumFormat(checksum)) {
                throw IllegalArgumentException("La copia no contiene un checksum SHA-256 válido. El archivo está dañado o fue modificado.")
            }
            root.remove("checksum")
            val reserialized = runCatching { root.toString(2) }
                .getOrElse { throw IllegalArgumentException("La copia no pudo verificarse (JSON inválido).") }
            val actualChecksum = BackupSecurityRules.sha256Hex(reserialized.toByteArray(Charsets.UTF_8))
            if (actualChecksum != checksum) {
                throw IllegalArgumentException("La copia no supera la verificación de integridad. El archivo está dañado o fue modificado.")
            }
        }

        val presentCollections = BackupSecurityRules.requiredCollections.filterTo(mutableSetOf()) { root.has(it) }
        if (!BackupSecurityRules.hasAllCollections(presentCollections)) {
            val missing = (BackupSecurityRules.requiredCollections - presentCollections).sorted().joinToString()
            throw IllegalArgumentException("La copia está incompleta. Faltan: $missing.")
        }
        if (version >= 3 && root.optJSONObject("preferences") == null) {
            throw IllegalArgumentException("La copia no contiene los ajustes de Ordia.")
        }

        // Parse y validación de cada valor ANTES de cambiar DataStore o Room.
        val data = RestoreData(
            projects = root.requiredArray("projects").validatedMap("projects") { it.toProject() },
            tasks = root.requiredArray("tasks").validatedMap("tasks") { it.toTask() },
            notes = root.requiredArray("notes").validatedMap("notes") { it.toNote() },
            habits = root.requiredArray("habits").validatedMap("habits") { it.toHabit() },
            habitLogs = root.requiredArray("habitLogs").validatedMap("habitLogs") { it.toHabitLog() },
            focusSessions = root.requiredArray("focusSessions").validatedMap("focusSessions") { it.toFocusSession() },
            routines = root.requiredArray("routines").validatedMap("routines") { it.toRoutine() },
            routineSteps = root.requiredArray("routineSteps").validatedMap("routineSteps") { it.toRoutineStep() },
            tags = root.requiredArray("tags").validatedMap("tags") { it.toTag() },
            taskTags = root.requiredArray("taskTags").validatedMap("taskTags") { it.toTaskTag() },
            attachments = root.requiredArray("attachments").validatedMap("attachments") { it.toAttachment() }
        )
        val total = data.totalCount
        require(BackupSecurityRules.totalSizeAllowed(total)) { "La copia contiene demasiados registros." }
        validateRelationships(data)
        val restoredPreferences = if (version >= 3) {
            preferences.decodeBackupJson(root.getJSONObject("preferences"))
        } else null
        return ValidatedBackup(data, restoredPreferences, version)
    }

    /**
     * Crea el journal preventivo del estado actual y lo verifica:
     * el archivo debe existir, no estar vacío y contener JSON válido.
     */
    private suspend fun writePreRestoreBackup(): Boolean = runCatching {
        val snapshot = buildExportJson()
        preRestoreBackupFile.parentFile?.mkdirs()
        withContext(Dispatchers.IO) { preRestoreBackupFile.writeText(snapshot, Charsets.UTF_8) }
        val existsAndNotEmpty = preRestoreBackupFile.exists() && preRestoreBackupFile.length() > 2L
        val parses = existsAndNotEmpty && runCatching { JSONObject(preRestoreBackupFile.readText()) }.isSuccess
        parses
    }.getOrDefault(false)

    /**
     * Verificación posterior al commit: relee lo persistido y confirma que
     * las cantidades coinciden con el backup y que las relaciones siguen
     * siendo válidas (sin referencias huérfanas).
     */
    private suspend fun verifyRestored(validated: ValidatedBackup): Boolean {
        val stored = backupStore.readAll()
        if (!validated.data.countsMatch(stored)) return false
        return runCatching { validateRelationships(stored) }.isSuccess
    }

    data class ImportResult(val success: Boolean, val message: String)

    companion object {
        private const val CURRENT_VERSION = 4

        /** Nombre del journal preventivo en almacenamiento privado (ORD-022). */
        const val PRE_RESTORE_BACKUP_FILENAME = "ordia_pre_restore_backup.json"
    }
}

private fun validateRelationships(data: RestoreData) {
    fun requirePositiveUnique(label: String, ids: List<Long>): Set<Long> {
        require(ids.all { it > 0L }) { "$label contiene identificadores inválidos." }
        require(ids.toSet().size == ids.size) { "$label contiene identificadores duplicados." }
        return ids.toSet()
    }
    val projectIds = requirePositiveUnique("Proyectos", data.projects.map { it.id })
    val taskIds = requirePositiveUnique("Tareas", data.tasks.map { it.id })
    val noteIds = requirePositiveUnique("Notas", data.notes.map { it.id })
    val habitIds = requirePositiveUnique("Hábitos", data.habits.map { it.id })
    requirePositiveUnique("Sesiones", data.focusSessions.map { it.id })
    val routineIds = requirePositiveUnique("Rutinas", data.routines.map { it.id })
    requirePositiveUnique("Pasos de rutina", data.routineSteps.map { it.id })
    val tagIds = requirePositiveUnique("Etiquetas", data.tags.map { it.id })
    requirePositiveUnique("Adjuntos", data.attachments.map { it.id })
    require(data.tags.map { it.name }.toSet().size == data.tags.size) { "La copia contiene etiquetas duplicadas." }

    data.tasks.forEach { task ->
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
    require(!BackupSecurityRules.hasParentCycle(data.tasks.associate { it.id to it.parentTaskId })) { "La copia contiene un ciclo entre subtareas." }
    data.projects.forEach { project -> require(project.createdAt <= project.updatedAt) { "Un proyecto fue actualizado antes de ser creado." } }
    data.notes.forEach { note ->
        require(note.projectId == null || note.projectId in projectIds) { "Una nota referencia un proyecto inexistente." }
        require(note.createdAt <= note.updatedAt) { "Una nota fue actualizada antes de ser creada." }
    }
    data.habits.forEach { habit ->
        require(habit.createdAt <= habit.updatedAt) { "Un hábito fue actualizado antes de ser creado." }
        val allowed = if (habit.frequency == HabitFrequency.MONTHLY) 1..31 else 1..7
        require(BackupSecurityRules.parseUniqueDayList(habit.activeDays, allowed) != null) {
            "Un hábito contiene días activos inválidos."
        }
    }
    data.habitLogs.forEach { log ->
        require(log.habitId in habitIds) { "Un registro referencia un hábito inexistente." }
    }
    require(!BackupSecurityRules.hasDuplicatePairs(data.habitLogs.map { it.habitId to it.epochDay })) {
        "La copia contiene registros de hábitos duplicados."
    }
    data.focusSessions.forEach { session ->
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
    data.routines.forEach { routine -> require(routine.createdAt <= routine.updatedAt) { "Una rutina fue actualizada antes de ser creada." } }
    data.routineSteps.forEach { step -> require(step.routineId in routineIds) { "Un paso referencia una rutina inexistente." } }
    require(data.routineSteps.groupBy { it.routineId }.values.all { steps ->
        steps.map { it.position }.toSet().size == steps.size
    }) { "Una rutina contiene posiciones de pasos duplicadas." }
    data.taskTags.forEach { link ->
        require(link.taskId in taskIds && link.tagId in tagIds) { "Una relación de etiqueta es inválida." }
    }
    require(!BackupSecurityRules.hasDuplicatePairs(data.taskTags.map { it.taskId to it.tagId })) {
        "La copia contiene relaciones de etiquetas duplicadas."
    }
    data.attachments.forEach { attachment ->
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
