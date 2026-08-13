package com.ordia.app.backup

import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.CaptureDraftEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CaptureSource
import com.ordia.app.data.local.CaptureStatus
import com.ordia.app.data.local.CaptureTarget
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
import com.ordia.app.data.local.ConsentEventEntity
import com.ordia.app.data.local.ConsentEventType
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ObservedSourceEntity
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
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationRuleResult
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
        root.put("captures", JSONArray().apply { current.captures.forEach { put(it.toJson()) } })
        root.put("captureDrafts", JSONArray().apply { current.captureDrafts.forEach { put(it.toJson()) } })
        root.put("conversations", JSONArray().apply { current.conversations.forEach { put(it.toJson()) } })
        root.put("commitments", JSONArray().apply { current.commitments.forEach { put(it.toJson()) } })
        root.put("observedSources", JSONArray().apply { current.observedSources.forEach { put(it.toJson()) } })
        root.put("consentEvents", JSONArray().apply { current.consentEvents.forEach { put(it.toJson()) } })
        root.put("automationRules", JSONArray().apply { current.automationRules.forEach { put(it.toJson()) } })
        root.put("automationLogs", JSONArray().apply { current.automationLogs.forEach { put(it.toJson()) } })
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
     *   cada valor persistido. Un fallo posterior al commit intenta restaurar
     *   automáticamente el estado previo antes de devolver el control.
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

                val oldData = runCatching { backupStore.readAll() }.getOrElse {
                    return@withContext ImportResult(
                        false,
                        "No se pudo leer el estado actual. La restauración se canceló y tus datos no se modificaron."
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

                onPhase(RestorePhase.VERIFYING)
                val verified = runCatching { verifyRestored(validated) }.getOrDefault(false)
                if (!verified) {
                    val dataRollback = runCatching { backupStore.replaceAll(oldData) }.exceptionOrNull()
                    val preferencesRollback = if (preferencesApplied) {
                        oldPreferences?.let { previous ->
                            runCatching { preferences.restoreSnapshot(previous, allowGuardianEnabled = true) }.exceptionOrNull()
                        }
                    } else null
                    if (dataRollback == null && preferencesRollback == null) {
                        return@withContext ImportResult(
                            false,
                            "La restauración no superó la verificación. Ordía revirtió automáticamente los cambios y conservó tus datos anteriores."
                        )
                    }
                    return@withContext ImportResult(
                        false,
                        "La restauración no pudo verificarse y no se confirma que haya terminado correctamente. " +
                            "Tus datos anteriores están en el respaldo preventivo ${preRestoreBackupFile.name} del almacenamiento privado."
                    )
                }

                // Los efectos externos se reconstruyen únicamente después de
                // verificar el commit. Así un rollback nunca deja recordatorios
                // correspondientes a datos descartados.
                val reminderWarning = runCatching {
                    reminderScheduler.cancelAllAndAwait()
                    val now = System.currentTimeMillis()
                    validated.data.tasks.asSequence()
                        .filter { !it.completed && !it.archived }
                        .filter { (it.reminderAt ?: it.dueAt)?.let { trigger -> trigger > now } == true }
                        .forEach(reminderScheduler::schedule)
                }.exceptionOrNull()

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
        require(raw.isNotBlank()) {
            "El archivo del manifiesto de copia está vacío o no contiene datos."
        }
        val rawBytes = raw.toByteArray(Charsets.UTF_8)
        require(BackupSecurityRules.inputSizeAllowed(rawBytes.size)) {
            "La copia está vacía o supera el límite de 10 MB."
        }
        BackupSecurityRules.validateJsonEnvelope(raw)?.let { throw IllegalArgumentException(it) }
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("El archivo no contiene JSON válido.") }
        val format = runCatching { root.get("format") }.getOrNull()
        if (format !is String || format != "ordia-backup") {
            throw IllegalArgumentException("El archivo no es una copia de seguridad de Ordía.")
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

        val requiredForVersion = BackupSecurityRules.requiredCollectionsFor(version)
        val presentCollections = requiredForVersion.filterTo(mutableSetOf()) { root.has(it) }
        if (!BackupSecurityRules.hasAllCollections(presentCollections, version)) {
            val missing = (requiredForVersion - presentCollections).sorted().joinToString()
            throw IllegalArgumentException("La copia está incompleta. Faltan: $missing.")
        }
        if (version >= 3 && root.optJSONObject("preferences") == null) {
            throw IllegalArgumentException("La copia no contiene los ajustes de Ordía.")
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
            attachments = root.requiredArray("attachments").validatedMap("attachments") { it.toAttachment() },
            captures = if (version >= 5) {
                root.requiredArray("captures").validatedMap("captures") { it.toCapture() }
            } else emptyList(),
            captureDrafts = if (version >= 5) {
                root.requiredArray("captureDrafts").validatedMap("captureDrafts") { it.toCaptureDraft() }
            } else emptyList(),
            conversations = if (version >= 6) {
                root.requiredArray("conversations").validatedMap("conversations") { it.toConversation() }
            } else emptyList(),
            commitments = if (version >= 6) {
                root.requiredArray("commitments").validatedMap("commitments") { it.toCommitment() }
            } else emptyList(),
            observedSources = if (version >= 7) {
                root.requiredArray("observedSources").validatedMap("observedSources") {
                    it.toObservedSource().copy(enabled = false, onlyCommitments = true)
                }
            } else emptyList(),
            consentEvents = if (version >= 7) {
                root.requiredArray("consentEvents").validatedMap("consentEvents") { it.toConsentEvent() }
            } else emptyList(),
            automationRules = if (version >= 8) {
                root.requiredArray("automationRules").validatedMap("automationRules") {
                    it.toAutomationRule().copy(enabled = false)
                }
            } else emptyList(),
            automationLogs = if (version >= 8) {
                root.requiredArray("automationLogs").validatedMap("automationLogs") { it.toAutomationLog() }
            } else emptyList()
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
     * Verificación posterior al commit: relee lo persistido y confirma cada
     * valor (ignorando solo el orden SQL) y todas las relaciones.
     */
    private suspend fun verifyRestored(validated: ValidatedBackup): Boolean {
        val stored = backupStore.readAll()
        if (!validated.data.contentMatches(stored)) return false
        return runCatching { validateRelationships(stored) }.isSuccess
    }

    data class ImportResult(val success: Boolean, val message: String)

    companion object {
        private const val CURRENT_VERSION = BackupSecurityRules.CURRENT_EXPORT_VERSION

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
    requirePositiveUnique("Capturas", data.captures.map { it.id })
    val conversationIds = requirePositiveUnique("Conversaciones", data.conversations.map { it.id })
    requirePositiveUnique("Compromisos", data.commitments.map { it.id })
    requirePositiveUnique("Eventos de consentimiento", data.consentEvents.map { it.id })
    requirePositiveUnique("Reglas de automatización", data.automationRules.map { it.id })
    requirePositiveUnique("Historial de automatizaciones", data.automationLogs.map { it.id })
    val observedPackages = data.observedSources.map { it.packageName }.toSet()
    require(observedPackages.size == data.observedSources.size) {
        "La copia contiene fuentes observadas duplicadas."
    }
    require(data.tags.map { it.name }.toSet().size == data.tags.size) { "La copia contiene etiquetas duplicadas." }
    require(data.captureDrafts.map { it.slot }.toSet().size == data.captureDrafts.size) {
        "La copia contiene borradores de captura duplicados."
    }
    require(data.conversations.map { it.contentHash }.toSet().size == data.conversations.size) {
        "La copia contiene conversaciones duplicadas."
    }
    require(data.commitments.map { it.fingerprint }.toSet().size == data.commitments.size) {
        "La copia contiene compromisos duplicados."
    }

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
    data.captures.forEach { capture ->
        require(capture.createdAt <= capture.updatedAt) { "Una captura fue actualizada antes de crearse." }
        require(capture.content.isNotBlank() || capture.attachmentUri.isNotBlank()) { "Una captura está vacía." }
        require(Regex("^[0-9a-f]{64}$").matches(capture.fingerprint)) { "Una captura tiene una huella inválida." }
        if (capture.status == CaptureStatus.PROCESSED) {
            val resultId = requireNotNull(capture.resultId) { "Una captura procesada no contiene resultado." }
            val validResult = when (capture.resultType) {
                "TASK" -> resultId in taskIds
                "NOTE" -> resultId in noteIds
                else -> false
            }
            require(validResult) { "Una captura procesada referencia un resultado inexistente." }
        }
    }
    data.captureDrafts.forEach { draft ->
        require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(draft.slot)) { "Un borrador tiene un identificador inválido." }
        require(draft.updatedAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS) { "Un borrador contiene una fecha inválida." }
    }
    data.conversations.forEach { conversation ->
        require(conversation.createdAt <= conversation.updatedAt) {
            "Una conversación fue actualizada antes de crearse."
        }
        require(Regex("^[0-9a-f]{64}$").matches(conversation.contentHash)) {
            "Una conversación tiene una huella inválida."
        }
        require(conversation.messageCount in 1..20_000) {
            "Una conversación contiene una cantidad de mensajes inválida."
        }
        require(conversation.retainsOriginal || conversation.rawContent.isBlank()) {
            "Una conversación conserva contenido original sin consentimiento."
        }
        require(!conversation.retainsOriginal || conversation.rawContent.isNotBlank()) {
            "Una conversación marcada para conservar el original está vacía."
        }
        require(
            conversation.sourcePackage.isBlank() ||
                Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(conversation.sourcePackage)
        ) { "Una conversación contiene un paquete de origen inválido." }
    }
    data.commitments.forEach { commitment ->
        require(commitment.conversationId in conversationIds) {
            "Un compromiso referencia una conversación inexistente."
        }
        require(commitment.createdAt <= commitment.updatedAt) {
            "Un compromiso fue actualizado antes de crearse."
        }
        require(commitment.confidence.isFinite() && commitment.confidence in 0f..1f) {
            "Un compromiso tiene una confianza inválida."
        }
        require(Regex("^[0-9a-f]{64}$").matches(commitment.fingerprint)) {
            "Un compromiso tiene una huella inválida."
        }
        require(commitment.dueAt == null || commitment.dueAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS) {
            "Un compromiso contiene una fecha inválida."
        }
        require(
            commitment.suggestedReminderAt == null ||
                commitment.suggestedReminderAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS
        ) { "Un compromiso contiene un recordatorio inválido." }
        require(commitment.reviewStatus == CommitmentReviewStatus.CONVERTED || commitment.resultTaskId == null) {
            "Un compromiso no convertido referencia una tarea."
        }
        commitment.resultTaskId?.let { taskId ->
            require(taskId in taskIds) { "Un compromiso referencia una tarea inexistente." }
        }
    }
    data.observedSources.forEach { source ->
        require(Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(source.packageName)) {
            "Una fuente observada contiene un paquete inválido."
        }
        require(source.displayName.isNotBlank()) { "Una fuente observada no contiene nombre." }
        require(source.createdAt <= source.updatedAt) { "Una fuente observada fue actualizada antes de crearse." }
        require(source.onlyCommitments) { "Una fuente observada intenta restaurar un modo de retención no permitido." }
        require(!source.enabled) { "Las fuentes restauradas deben quedar desactivadas." }
    }
    require(data.consentEvents.size <= 1_000) { "La copia contiene demasiados eventos de consentimiento." }
    data.consentEvents.forEach { event ->
        require(event.sourcePackage.isBlank() || event.sourcePackage in observedPackages) {
            "Un evento de consentimiento referencia una fuente inexistente."
        }
        require(event.occurredAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS) {
            "Un evento de consentimiento contiene una fecha inválida."
        }
    }
    require(data.automationRules.map { it.definitionHash }.toSet().size == data.automationRules.size) {
        "La copia contiene automatizaciones duplicadas."
    }
    data.automationRules.forEach { rule ->
        require(!rule.enabled) { "Las automatizaciones restauradas deben quedar desactivadas." }
        require(rule.name.isNotBlank() && rule.explanation.isNotBlank()) { "Una automatización está incompleta." }
        require(rule.frequencyMinutes in 15..10_080 && rule.maxRunsPerDay in 1..20) {
            "Una automatización contiene límites inválidos."
        }
        require(Regex("^[0-9a-f]{64}$").matches(rule.definitionHash)) { "Una automatización tiene una huella inválida." }
        require(rule.createdAt <= rule.updatedAt) { "Una automatización fue actualizada antes de crearse." }
    }
    data.automationLogs.forEach { log ->
        require(log.type.length <= 100 && log.description.length <= 2_000) { "Un registro de automatización es demasiado grande." }
        require(log.affectedTaskIdsJson.length <= 100_000 && log.undoPayloadJson.length <= 2_000_000) {
            "Un registro de automatización contiene un estado de deshacer demasiado grande."
        }
        require(log.createdAt in 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS) {
            "Un registro de automatización contiene una fecha inválida."
        }
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
    "attachments" to setOf("id", "ownerType", "ownerId", "uri", "displayName", "mimeType", "sizeBytes", "createdAt"),
    "captures" to setOf("id", "content", "source", "requestedTarget", "resolvedTarget", "status", "attachmentUri", "mimeType", "fingerprint", "resultType", "resultId", "errorCode", "createdAt", "updatedAt"),
    "captureDrafts" to setOf("slot", "content", "target", "attachmentUri", "mimeType", "updatedAt"),
    "conversations" to setOf("id", "sourceType", "sourcePackage", "title", "participants", "summary", "rawContent", "retainsOriginal", "contentHash", "messageCount", "createdAt", "updatedAt"),
    "commitments" to setOf("id", "conversationId", "kind", "owner", "actor", "action", "location", "dueAt", "confidence", "suggestedReminderAt", "reviewStatus", "fingerprint", "resultTaskId", "createdAt", "updatedAt"),
    "observedSources" to setOf("packageName", "displayName", "enabled", "onlyCommitments", "createdAt", "updatedAt"),
    "consentEvents" to setOf("id", "eventType", "sourcePackage", "occurredAt"),
    "automationRules" to setOf("id", "name", "instruction", "trigger", "condition", "action", "explanation", "enabled", "frequencyMinutes", "maxRunsPerDay", "lastRunAt", "lastResult", "lastError", "definitionHash", "createdAt", "updatedAt"),
    "automationLogs" to setOf("id", "type", "description", "affectedTaskIdsJson", "undoPayloadJson", "undone", "createdAt")
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

private fun JSONObject.floatValue(name: String, fallback: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (!has(name) || isNull(name)) return fallback
    val value = get(name)
    require(value is Number) { "$name debe ser numérico." }
    val result = value.toFloat()
    require(result.isFinite() && result in range) { "$name está fuera de rango." }
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

private fun CaptureEntity.toJson() = JSONObject()
    .put("id", id)
    .put("content", content)
    .put("source", source.name)
    .put("requestedTarget", requestedTarget.name)
    .put("resolvedTarget", resolvedTarget.name)
    .put("status", status.name)
    .put("attachmentUri", attachmentUri)
    .put("mimeType", mimeType)
    .put("fingerprint", fingerprint)
    .put("resultType", resultType)
    .putNullable("resultId", resultId)
    .put("errorCode", errorCode)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

private fun JSONObject.toCapture() = CaptureEntity(
    id = requiredLong("id"),
    content = text("content", 100_000),
    source = enumValue("source", CaptureSource.COMPOSER),
    requestedTarget = enumValue("requestedTarget", CaptureTarget.AUTO),
    resolvedTarget = enumValue("resolvedTarget", CaptureTarget.INBOX),
    status = enumValue("status", CaptureStatus.PENDING),
    attachmentUri = optionalContentUri("attachmentUri"),
    mimeType = text("mimeType", 500),
    fingerprint = requiredText("fingerprint", 64).also {
        require(Regex("^[0-9a-f]{64}$").matches(it)) { "La huella de una captura no es válida." }
    },
    resultType = text("resultType", 32),
    resultId = longOrNull("resultId"),
    errorCode = text("errorCode", 80),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun CaptureDraftEntity.toJson() = JSONObject()
    .put("slot", slot)
    .put("content", content)
    .put("target", target.name)
    .put("attachmentUri", attachmentUri)
    .put("mimeType", mimeType)
    .put("updatedAt", updatedAt)

private fun JSONObject.toCaptureDraft() = CaptureDraftEntity(
    slot = requiredText("slot", 64),
    content = text("content", 100_000),
    target = enumValue("target", CaptureTarget.AUTO),
    attachmentUri = optionalContentUri("attachmentUri"),
    mimeType = text("mimeType", 500),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun ConversationEntity.toJson() = JSONObject()
    .put("id", id)
    .put("sourceType", sourceType.name)
    .put("sourcePackage", sourcePackage)
    .put("title", title)
    .put("participants", participants)
    .put("summary", summary)
    .put("rawContent", rawContent)
    .put("retainsOriginal", retainsOriginal)
    .put("contentHash", contentHash)
    .put("messageCount", messageCount)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

private fun JSONObject.toConversation() = ConversationEntity(
    id = requiredLong("id"),
    sourceType = enumValue("sourceType", ConversationSourceType.IMPORTED),
    sourcePackage = text("sourcePackage", 255),
    title = requiredText("title", 500),
    participants = text("participants", 10_000),
    summary = requiredText("summary", 100_000),
    rawContent = text("rawContent", 2_000_000),
    retainsOriginal = booleanValue("retainsOriginal", false),
    contentHash = requiredText("contentHash", 64).also {
        require(Regex("^[0-9a-f]{64}$").matches(it)) { "La huella de una conversación no es válida." }
    },
    messageCount = intValue("messageCount", 0, 1..20_000),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun CommitmentEntity.toJson() = JSONObject()
    .put("id", id)
    .put("conversationId", conversationId)
    .put("kind", kind.name)
    .put("owner", owner.name)
    .put("actor", actor)
    .put("action", action)
    .put("location", location)
    .putNullable("dueAt", dueAt)
    .put("confidence", confidence.toDouble())
    .putNullable("suggestedReminderAt", suggestedReminderAt)
    .put("reviewStatus", reviewStatus.name)
    .put("fingerprint", fingerprint)
    .putNullable("resultTaskId", resultTaskId)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

private fun JSONObject.toCommitment() = CommitmentEntity(
    id = requiredLong("id"),
    conversationId = requiredLong("conversationId"),
    kind = enumValue("kind", CommitmentKind.INFORMATION),
    owner = enumValue("owner", CommitmentOwner.UNKNOWN),
    actor = text("actor", 500),
    action = requiredText("action", 10_000),
    location = text("location", 500),
    dueAt = epochMillisOrNull("dueAt"),
    confidence = floatValue("confidence", 0f, 0f..1f),
    suggestedReminderAt = epochMillisOrNull("suggestedReminderAt"),
    reviewStatus = enumValue("reviewStatus", CommitmentReviewStatus.PENDING),
    fingerprint = requiredText("fingerprint", 64).also {
        require(Regex("^[0-9a-f]{64}$").matches(it)) { "La huella de un compromiso no es válida." }
    },
    resultTaskId = longOrNull("resultTaskId"),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun ObservedSourceEntity.toJson() = JSONObject()
    .put("packageName", packageName)
    .put("displayName", displayName)
    .put("enabled", enabled)
    .put("onlyCommitments", onlyCommitments)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

private fun JSONObject.toObservedSource() = ObservedSourceEntity(
    packageName = requiredText("packageName", 180),
    displayName = requiredText("displayName", 100),
    enabled = booleanValue("enabled", false),
    onlyCommitments = booleanValue("onlyCommitments", true),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun ConsentEventEntity.toJson() = JSONObject()
    .put("id", id)
    .put("eventType", eventType.name)
    .put("sourcePackage", sourcePackage)
    .put("occurredAt", occurredAt)

private fun JSONObject.toConsentEvent() = ConsentEventEntity(
    id = requiredLong("id"),
    eventType = enumValue("eventType", ConsentEventType.OBSERVATION_DISABLED),
    sourcePackage = text("sourcePackage", 180),
    occurredAt = longValue("occurredAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun AutomationRuleEntity.toJson() = JSONObject()
    .put("id", id).put("name", name).put("instruction", instruction)
    .put("trigger", trigger.name).put("condition", condition.name).put("action", action.name)
    .put("explanation", explanation).put("enabled", enabled)
    .put("frequencyMinutes", frequencyMinutes).put("maxRunsPerDay", maxRunsPerDay)
    .putNullable("lastRunAt", lastRunAt).put("lastResult", lastResult.name).put("lastError", lastError)
    .put("definitionHash", definitionHash).put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun JSONObject.toAutomationRule() = AutomationRuleEntity(
    id = requiredLong("id"),
    name = requiredText("name", 200),
    instruction = requiredText("instruction", 500),
    trigger = enumValue("trigger", AutomationTrigger.MANUAL),
    condition = enumValue("condition", AutomationCondition.ALWAYS),
    action = enumValue("action", AutomationAction.PLAN_DAY),
    explanation = requiredText("explanation", 1_000),
    enabled = booleanValue("enabled", false),
    frequencyMinutes = intValue("frequencyMinutes", 60, 15..10_080),
    maxRunsPerDay = intValue("maxRunsPerDay", 3, 1..20),
    lastRunAt = epochMillisOrNull("lastRunAt"),
    lastResult = enumValue("lastResult", AutomationRuleResult.NEVER),
    lastError = text("lastError", 500),
    definitionHash = requiredText("definitionHash", 64),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS),
    updatedAt = longValue("updatedAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun AutomationLogEntity.toJson() = JSONObject()
    .put("id", id).put("type", type).put("description", description)
    .put("affectedTaskIdsJson", affectedTaskIdsJson).put("undoPayloadJson", undoPayloadJson)
    .put("undone", undone).put("createdAt", createdAt)

private fun JSONObject.toAutomationLog() = AutomationLogEntity(
    id = requiredLong("id"),
    type = requiredText("type", 100),
    description = text("description", 2_000),
    affectedTaskIdsJson = text("affectedTaskIdsJson", 100_000, "[]"),
    undoPayloadJson = text("undoPayloadJson", 2_000_000, "{}"),
    undone = booleanValue("undone", false),
    createdAt = longValue("createdAt", System.currentTimeMillis(), 0L..BackupSecurityRules.MAX_SAFE_EPOCH_MILLIS)
)

private fun JSONObject.optionalContentUri(name: String): String {
    val value = text(name, 20_000)
    if (value.isBlank()) return ""
    val parsed = runCatching { URI(value) }.getOrNull() ?: error("El URI de una captura no es válido.")
    require(parsed.scheme?.lowercase() == "content" && !parsed.isOpaque && !parsed.authority.isNullOrBlank()) {
        "El URI de una captura no es un content URI jerárquico válido."
    }
    require(parsed.fragment == null) { "El URI de una captura contiene un fragmento no permitido." }
    return value
}

private inline fun <reified T : Enum<T>> JSONObject.enumValue(name: String, fallback: T): T {
    if (!has(name) || isNull(name)) return fallback
    val value = text(name, 64)
    return runCatching { enumValueOf<T>(value) }
        .getOrElse { error("$name contiene un valor desconocido.") }
}
