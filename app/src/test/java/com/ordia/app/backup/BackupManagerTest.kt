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
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
import com.ordia.app.data.local.ConsentEventEntity
import com.ordia.app.data.local.ConsentEventType
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ObservedSourceEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskTagCrossRef
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationTrigger
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.preferences.GuardianMode
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.data.preferences.ThemeMode
import com.ordia.app.data.preferences.UserPreferences
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del flujo de restauración (Fase 5, ORD-022/ORD-031).
 *
 * [BackupManager] se prueba en JVM con implementaciones en memoria de
 * [BackupStore], [BackupPreferences] y [ReminderSchedulerPort], más un
 * fichero temporal para el journal preventivo. Esto permite forzar fallos
 * intermedios (escritura del journal, reemplazo, verificación) que en Room
 * serían difíciles de simular.
 */
class BackupManagerTest {

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private val now: Long = System.currentTimeMillis()

    private fun sampleData(): RestoreData = RestoreData(
        projects = listOf(ProjectEntity(id = 1, name = "Proyecto", createdAt = 1000L, updatedAt = 1000L)),
        tasks = listOf(
            TaskEntity(id = 2, title = "Tarea", projectId = 1, createdAt = 1000L, updatedAt = 1000L),
            TaskEntity(
                id = 10, title = "Futura", projectId = 1, dueAt = now + 86_400_000L,
                createdAt = now - 10_000L, updatedAt = now - 5_000L
            ),
            TaskEntity(
                id = 11, title = "Completada", projectId = 1, dueAt = now + 86_400_000L,
                status = com.ordia.app.data.local.TaskStatus.COMPLETED, completed = true,
                completedAt = now - 5_000L, createdAt = now - 10_000L, updatedAt = now - 5_000L
            ),
            // Cancelada con recordatorio futuro: la fuente de verdad (ReminderSync)
            // excluye CANCELLED, así que el restore NO debe re-encolarla.
            TaskEntity(
                id = 18, title = "Cancelada", projectId = 1, dueAt = now + 86_400_000L,
                status = com.ordia.app.data.local.TaskStatus.CANCELLED,
                createdAt = now - 10_000L, updatedAt = now - 5_000L
            )
        ),
        notes = listOf(NoteEntity(id = 3, title = "Nota", createdAt = 1000L, updatedAt = 1000L)),
        habits = listOf(HabitEntity(id = 4, title = "Hábito", createdAt = 1000L, updatedAt = 1000L)),
        habitLogs = listOf(HabitLogEntity(habitId = 4, epochDay = 20_000L, completedAt = 1000L)),
        focusSessions = listOf(
            FocusSessionEntity(
                id = 5, taskId = 2, startedAt = 1000L, endedAt = 1000L + 25 * 60_000L,
                plannedMinutes = 25, actualMinutes = 25, completed = true
            )
        ),
        routines = listOf(RoutineEntity(id = 6, name = "Rutina", createdAt = 1000L, updatedAt = 1000L)),
        routineSteps = listOf(RoutineStepEntity(id = 7, routineId = 6, title = "Paso")),
        tags = listOf(TagEntity(id = 8, name = "etiqueta")),
        taskTags = listOf(TaskTagCrossRef(taskId = 2, tagId = 8)),
        attachments = listOf(
            AttachmentEntity(
                id = 9, ownerType = AttachmentOwnerType.TASK, ownerId = 2,
                uri = "content://com.ordia.app/adjunto/1", displayName = "archivo.txt",
                mimeType = "text/plain", sizeBytes = 100, createdAt = 1000L
            )
        ),
        captures = listOf(
            CaptureEntity(
                id = 12,
                content = "Tarea",
                source = CaptureSource.COMPOSER,
                requestedTarget = CaptureTarget.TASK,
                resolvedTarget = CaptureTarget.TASK,
                status = CaptureStatus.PROCESSED,
                fingerprint = "a".repeat(64),
                resultType = "TASK",
                resultId = 2,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        ),
        captureDrafts = listOf(
            CaptureDraftEntity(content = "Borrador", updatedAt = 1000L)
        ),
        conversations = listOf(
            ConversationEntity(
                id = 13,
                sourceType = ConversationSourceType.IMPORTED,
                title = "Chat de proyecto",
                participants = "Ana\nYo",
                summary = "Dos mensajes con una solicitud pendiente.",
                contentHash = "b".repeat(64),
                messageCount = 2,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        ),
        commitments = listOf(
            CommitmentEntity(
                id = 14,
                conversationId = 13,
                kind = CommitmentKind.REQUEST,
                owner = CommitmentOwner.SELF,
                actor = "Ana",
                action = "Envíame el informe mañana",
                confidence = 0.9f,
                fingerprint = "c".repeat(64),
                createdAt = 1000L,
                updatedAt = 1000L
            )
        ),
        observedSources = listOf(
            ObservedSourceEntity(
                packageName = "com.whatsapp",
                displayName = "WhatsApp",
                enabled = true,
                onlyCommitments = true,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        ),
        consentEvents = listOf(
            ConsentEventEntity(
                id = 15,
                eventType = ConsentEventType.SOURCE_ENABLED,
                sourcePackage = "com.whatsapp",
                occurredAt = 1000L
            )
        ),
        automationRules = listOf(
            AutomationRuleEntity(
                id = 16, name = "Preparar día", instruction = "Cada mañana prepara mi día",
                trigger = AutomationTrigger.DAILY_MORNING,
                condition = AutomationCondition.HAS_INBOX_TASKS,
                action = AutomationAction.PLAN_DAY,
                explanation = "Plan local reversible", enabled = true,
                definitionHash = "d".repeat(64), createdAt = 1000L, updatedAt = 1000L
            )
        ),
        automationLogs = listOf(
            AutomationLogEntity(id = 17, type = "rule:16", description = "Plan preparado", createdAt = 1000L)
        )
    )

    private fun otherData(): RestoreData = RestoreData(
        projects = listOf(ProjectEntity(id = 1, name = "Viejo", createdAt = 1000L, updatedAt = 1000L))
    )

    private fun journalFile(): File {
        val file = File(System.getProperty("java.io.tmpdir"), "ordia_test_journal_${System.nanoTime()}.json")
        file.deleteOnExit()
        return file
    }

    private fun newManager(
        store: BackupStore,
        prefs: FakeBackupPreferences = FakeBackupPreferences(),
        scheduler: FakeReminderScheduler = FakeReminderScheduler(),
        journal: File = journalFile()
    ) = BackupManager(store, prefs, scheduler, journal)

    /** Recalcula el checksum después de modificar el JSON (como haría la app). */
    private fun rewrap(root: JSONObject, version: Int = 8): String {
        root.put("version", version)
        root.remove("checksum")
        val content = root.toString(2)
        root.put("checksum", BackupSecurityRules.sha256Hex(content.toByteArray(Charsets.UTF_8)))
        return root.toString(2)
    }

    // ---------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------

    private class FakeBackupStore(initial: RestoreData = RestoreData()) : BackupStore {
        var current: RestoreData = initial
        var throwOnReplace: Exception? = null
        var corruptReadAfterReplace = false
        var mutateValueAfterReplace = false
        private var replaced = false

        override suspend fun replaceAll(data: RestoreData) {
            throwOnReplace?.let { throw it }
            current = data
            replaced = true
        }

        override suspend fun readAll(): RestoreData {
            if (replaced && corruptReadAfterReplace) {
                // Simula un commit que no persistió todo: la verificación debe fallar.
                replaced = false
                return current.copy(tasks = current.tasks.drop(1))
            }
            if (replaced && mutateValueAfterReplace) {
                replaced = false
                return current.copy(
                    projects = current.projects.mapIndexed { index, project ->
                        if (index == 0) project.copy(name = "Valor alterado") else project
                    }
                )
            }
            return current
        }
    }

    private class FakeBackupPreferences(initial: UserPreferences = UserPreferences()) : BackupPreferences {
        var value: UserPreferences = initial
        var guardianBlocked = false
        var throwOnRestore: Exception? = null

        override suspend fun exportJson(): JSONObject = value.toTestJson()

        override fun decodeBackupJson(json: JSONObject): UserPreferences {
            val species = runCatching {
                GuardianSpecies.valueOf(json.optString("guardianSpecies", GuardianSpecies.LUMI.name))
            }.getOrDefault(GuardianSpecies.LUMI)
            return UserPreferences(
                themeMode = runCatching { ThemeMode.valueOf(json.optString("themeMode", ThemeMode.SYSTEM.name)) }
                    .getOrDefault(ThemeMode.SYSTEM),
                interfaceMode = runCatching { InterfaceMode.valueOf(json.optString("interfaceMode", InterfaceMode.ORGANIZED.name)) }
                    .getOrDefault(InterfaceMode.ORGANIZED),
                guardianEnabled = json.optBoolean("guardianEnabled", false),
                guardianMode = runCatching { GuardianMode.valueOf(json.optString("guardianMode", GuardianMode.DISCREET.name)) }
                    .getOrDefault(GuardianMode.DISCREET),
                guardianName = json.optString("guardianName", GuardianSpecies.LUMI.defaultName),
                guardianSpecies = species,
                guardianBond = json.optInt("guardianBond", 0),
                guardianExperience = json.optInt("guardianExperience", 0),
                guardianLastInteraction = json.optLong("guardianLastInteraction", 0L),
                guardianLastEvent = json.optString("guardianLastEvent", "welcome"),
                guardianAnimations = json.optBoolean("guardianAnimations", true),
                guardianInteractionEpochDay = json.optLong("guardianInteractionEpochDay", 0L),
                guardianInteractionsToday = json.optInt("guardianInteractionsToday", 0),
                autoUpdateEnabled = json.optBoolean("autoUpdateEnabled", true),
                autoDownloadUpdates = json.optBoolean("autoDownloadUpdates", true),
                quietStartMinutes = json.optInt("quietStartMinutes", 22 * 60),
                quietEndMinutes = json.optInt("quietEndMinutes", 7 * 60),
                onboardingComplete = json.optBoolean("onboardingComplete", false),
                weekStartsMonday = json.optBoolean("weekStartsMonday", true),
                defaultFocusMinutes = json.optInt("defaultFocusMinutes", 25),
                reduceMotion = json.optBoolean("reduceMotion", false),
                compactNavigation = json.optBoolean("compactNavigation", false)
            )
        }

        override suspend fun snapshot(): UserPreferences = value

        override suspend fun restoreSnapshot(restored: UserPreferences, allowGuardianEnabled: Boolean) {
            throwOnRestore?.let { throw it }
            value = if (allowGuardianEnabled) restored else restored.copy(guardianEnabled = false)
            guardianBlocked = restored.guardianEnabled && !allowGuardianEnabled
        }
    }

    private class FakeReminderScheduler : ReminderSchedulerPort {
        val scheduled = mutableListOf<Long>()
        var cancelCalls = 0
        var throwOnCancel: Exception? = null

        override suspend fun cancelAllAndAwait() {
            throwOnCancel?.let { throw it }
            cancelCalls++
        }

        override fun schedule(task: TaskEntity) {
            scheduled += task.id
        }
    }

    // ---------------------------------------------------------------------
    // Export / round-trip
    // ---------------------------------------------------------------------

    @Test
    fun exportAndImportRoundTripRestoresEverything() = runBlocking {
        val originStore = FakeBackupStore(sampleData())
        val origin = newManager(originStore)
        val backup = origin.exportJson()

        val destinationStore = FakeBackupStore(otherData())
        val scheduler = FakeReminderScheduler()
        val journal = journalFile()
        val destination = newManager(destinationStore, FakeBackupPreferences(), scheduler, journal)

        val phases = mutableListOf<RestorePhase>()
        val result = destination.importBackup(backup) { phases += it }

        assertTrue(result.message, result.success)
        assertEquals(
            listOf(RestorePhase.VALIDATING, RestorePhase.CREATING_SAFETY_BACKUP, RestorePhase.RESTORING, RestorePhase.VERIFYING),
            phases
        )
        assertTrue(destinationStore.current.countsMatch(sampleData()))
        assertEquals("Proyecto", destinationStore.current.projects.first().name)
        assertFalse(destinationStore.current.observedSources.single().enabled)
        assertFalse(destinationStore.current.automationRules.single().enabled)
        // Solo la tarea futura abierta se reprograma (no la completada, la
        // cancelada ni las pasadas). CANCELLED debe excluirse como en ReminderSync.
        assertEquals(listOf(10L), scheduler.scheduled)
        assertEquals(1, scheduler.cancelCalls)
        assertTrue(journal.exists() && journal.length() > 2L)
    }

    @Test
    fun emptyExportAndImportRoundTripSucceeds() = runBlocking {
        val origin = newManager(FakeBackupStore())
        val backup = origin.exportJson()

        val destinationStore = FakeBackupStore(otherData())
        val result = newManager(destinationStore).importBackup(backup)

        assertTrue(result.message, result.success)
        assertTrue(destinationStore.current.totalCount == 0)
    }

    @Test
    fun version4BackupWithoutCaptureCollectionsRemainsCompatible() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val legacy = JSONObject(origin.exportJson()).apply {
            remove("captures")
            remove("captureDrafts")
        }
        val destinationStore = FakeBackupStore(otherData())

        val result = newManager(destinationStore).importBackup(rewrap(legacy, version = 4))

        assertTrue(result.message, result.success)
        assertTrue(destinationStore.current.captures.isEmpty())
        assertTrue(destinationStore.current.captureDrafts.isEmpty())
    }

    @Test
    fun version5BackupWithoutConversationCollectionsRemainsCompatible() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val legacy = JSONObject(origin.exportJson()).apply {
            remove("conversations")
            remove("commitments")
        }
        val destinationStore = FakeBackupStore(otherData())

        val result = newManager(destinationStore).importBackup(rewrap(legacy, version = 5))

        assertTrue(result.message, result.success)
        assertTrue(destinationStore.current.conversations.isEmpty())
        assertTrue(destinationStore.current.commitments.isEmpty())
        assertEquals(1, destinationStore.current.captures.size)
    }

    @Test
    fun version6RejectsCommitmentWhoseConversationDoesNotExist() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val tampered = JSONObject(origin.exportJson()).apply {
            getJSONArray("commitments").getJSONObject(0).put("conversationId", 999L)
        }
        val destinationStore = FakeBackupStore(otherData())

        val result = newManager(destinationStore).importBackup(rewrap(tampered))

        assertFalse(result.success)
        assertTrue(result.message.contains("conversación inexistente"))
        assertEquals("Viejo", destinationStore.current.projects.single().name)
    }

    @Test
    fun version6RejectsRawConversationWithoutRetentionConsent() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val tampered = JSONObject(origin.exportJson()).apply {
            getJSONArray("conversations").getJSONObject(0)
                .put("rawContent", "Contenido que no debía conservarse")
                .put("retainsOriginal", false)
        }
        val destinationStore = FakeBackupStore(otherData())

        val result = newManager(destinationStore).importBackup(rewrap(tampered))

        assertFalse(result.success)
        assertTrue(result.message.contains("sin consentimiento"))
        assertEquals("Viejo", destinationStore.current.projects.single().name)
    }

    @Test
    fun version6BackupWithoutObservationCollectionsRemainsCompatible() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val legacy = JSONObject(origin.exportJson()).apply {
            remove("observedSources")
            remove("consentEvents")
        }
        val destinationStore = FakeBackupStore(otherData())

        val result = newManager(destinationStore).importBackup(rewrap(legacy, version = 6))

        assertTrue(result.message, result.success)
        assertTrue(destinationStore.current.observedSources.isEmpty())
        assertTrue(destinationStore.current.consentEvents.isEmpty())
        assertEquals(1, destinationStore.current.conversations.size)
    }

    @Test
    fun version7BackupWithoutAutomationCollectionsRemainsCompatible() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val legacy = JSONObject(origin.exportJson()).apply {
            remove("automationRules")
            remove("automationLogs")
        }
        val destinationStore = FakeBackupStore(otherData())

        val result = newManager(destinationStore).importBackup(rewrap(legacy, version = 7))

        assertTrue(result.message, result.success)
        assertTrue(destinationStore.current.automationRules.isEmpty())
        assertTrue(destinationStore.current.automationLogs.isEmpty())
        assertEquals(1, destinationStore.current.observedSources.size)
    }

    @Test
    fun journalCapturesThePreviousStateBeforeReplace() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        val destinationStore = FakeBackupStore(otherData())
        val journal = journalFile()
        val result = newManager(destinationStore, journal = journal).importBackup(backup)

        assertTrue(result.message, result.success)
        val journalRoot = JSONObject(journal.readText())
        assertEquals("Viejo", journalRoot.getJSONArray("projects").getJSONObject(0).getString("name"))
        assertEquals("ordia-backup", journalRoot.getString("format"))
    }

    @Test
    fun concurrentExportsAreSerializedByTheMutex() = runBlocking {
        val manager = newManager(FakeBackupStore(sampleData()))
        val first = async { manager.exportJson() }
        val second = async { manager.exportJson() }
        val a = first.await()
        val b = second.await()
        // El mutex serializa; cada export es un JSON v4 válido (aunque createdAt difiere).
        assertEquals("ordia-backup", JSONObject(a).getString("format"))
        assertEquals("ordia-backup", JSONObject(b).getString("format"))
        assertTrue(a.isNotBlank() && b.isNotBlank())
    }

    // ---------------------------------------------------------------------
    // Validación previa (sin tocar la base ni el journal)
    // ---------------------------------------------------------------------

    @Test
    fun invalidJsonIsRejectedWithoutTouchingData() = runBlocking {
        val store = FakeBackupStore(otherData())
        val journal = journalFile()
        val result = newManager(store, journal = journal).importBackup("esto no es json")

        assertFalse(result.success)
        assertEquals("Viejo", store.current.projects.first().name)
        assertFalse(journal.exists())
    }

    @Test
    fun unsupportedVersionIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val tampered = rewrap(JSONObject(origin.exportJson()), version = 1)

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(tampered)

        assertFalse(result.success)
        assertTrue(result.message.contains("versión"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun missingCollectionsAreRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        root.remove("attachments")

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(rewrap(root))

        assertFalse(result.success)
        assertTrue(result.message.contains("incompleta"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun missingCreatedAtIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        root.remove("createdAt")

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(rewrap(root))

        assertFalse(result.success)
        assertTrue(result.message.contains("fecha de creación"))
    }

    @Test
    fun corruptedChecksumIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        // Modificación sin recalcular el checksum: el archivo está "dañado".
        root.getJSONArray("tasks").getJSONObject(0).put("title", "Modificada")

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(root.toString(2))

        assertFalse(result.success)
        assertTrue(result.message.contains("integridad"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun orphanProjectReferenceIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        root.getJSONArray("tasks").getJSONObject(0).put("projectId", 999L)

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(rewrap(root))

        assertFalse(result.success)
        assertTrue(result.message.contains("proyecto inexistente"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun parentCycleBetweenTasksIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        val tasks = root.getJSONArray("tasks")
        tasks.getJSONObject(0).put("parentTaskId", tasks.getJSONObject(1).getLong("id"))
        tasks.getJSONObject(1).put("parentTaskId", tasks.getJSONObject(0).getLong("id"))

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(rewrap(root))

        assertFalse(result.success)
        assertTrue(result.message.contains("ciclo"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun duplicateTaskTagRelationIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        val links = root.getJSONArray("taskTags")
        val first = links.getJSONObject(0)
        links.put(JSONObject().put("taskId", first.getLong("taskId")).put("tagId", first.getLong("tagId")))

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(rewrap(root))

        assertFalse(result.success)
        assertTrue(result.message.contains("duplicadas"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun incompleteItemInsideCollectionIsRejected() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val root = JSONObject(origin.exportJson())
        root.getJSONArray("tasks").getJSONObject(0).remove("title")

        val store = FakeBackupStore(otherData())
        val result = newManager(store).importBackup(rewrap(root))

        assertFalse(result.success)
        assertTrue(result.message.contains("incompleto"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    // ---------------------------------------------------------------------
    // Salvaguardas del flujo (journal, rollback, verificación)
    // ---------------------------------------------------------------------

    @Test
    fun journalFailureCancelsRestoreWithoutModifyingData() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        // El "directorio padre" es un archivo: mkdirs y escritura fallan.
        val blocker = File.createTempFile("ordia_blocker", ".tmp")
        val badJournal = File(blocker, "hijo.json")

        val store = FakeBackupStore(otherData())
        val result = newManager(store, journal = badJournal).importBackup(backup)

        assertFalse(result.success)
        assertTrue(result.message.contains("respaldo preventivo"))
        assertTrue(result.message.contains("no se modificaron"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun storeFailureRollsBackPreferences() = runBlocking {
        val originStore = FakeBackupStore(sampleData())
        val originPrefs = FakeBackupPreferences(
            UserPreferences(guardianName = "DelBackup", guardianBond = 42, guardianEnabled = true)
        )
        val backup = newManager(originStore, originPrefs).exportJson()

        val store = FakeBackupStore(otherData())
        store.throwOnReplace = RuntimeException("fallo de Room simulado")
        val prefs = FakeBackupPreferences(
            UserPreferences(guardianName = "Local", guardianBond = 7)
        )
        val result = newManager(store, prefs).importBackup(backup)

        assertFalse(result.success)
        assertTrue(result.message.contains("conservó su estado anterior"))
        // Las preferencias se compensan: vuelven a las locales.
        assertEquals("Local", prefs.value.guardianName)
        assertEquals(7, prefs.value.guardianBond)
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun verificationFailureRollsBackDataAutomatically() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        val store = FakeBackupStore(otherData())
        store.corruptReadAfterReplace = true
        val journal = journalFile()
        val result = newManager(store, journal = journal).importBackup(backup)

        assertFalse(result.success)
        assertTrue(result.message.contains("revirtió automáticamente"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun verificationDetectsChangedValuesEvenWhenCountsMatch() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        val store = FakeBackupStore(otherData()).apply { mutateValueAfterReplace = true }
        val result = newManager(store).importBackup(backup)

        assertFalse(result.success)
        assertTrue(result.message.contains("revirtió automáticamente"))
        assertEquals("Viejo", store.current.projects.first().name)
    }

    @Test
    fun reminderCancellationFailureStillReportsRestoredData() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        val scheduler = FakeReminderScheduler().apply { throwOnCancel = RuntimeException("WorkManager roto") }
        val store = FakeBackupStore(otherData())
        val result = newManager(store, scheduler = scheduler).importBackup(backup)

        assertTrue(result.message, result.success)
        assertTrue(result.message.contains("recordatorios"))
        assertTrue(store.current.countsMatch(sampleData()))
    }

    @Test
    fun guardianPreferenceIsBlockedDuringRestore() = runBlocking {
        val originStore = FakeBackupStore(sampleData())
        val originPrefs = FakeBackupPreferences(UserPreferences(guardianEnabled = true, guardianName = "Nova"))
        val backup = newManager(originStore, originPrefs).exportJson()

        val prefs = FakeBackupPreferences(UserPreferences(guardianEnabled = true, guardianName = "Lumi"))
        val store = FakeBackupStore(otherData())
        val result = newManager(store, prefs).importBackup(backup)

        assertTrue(result.message, result.success)
        // La preferencia del guardián del backup se bloquea deliberadamente.
        assertFalse(prefs.value.guardianEnabled)
        assertTrue(prefs.guardianBlocked)
        assertEquals("Nova", prefs.value.guardianName)
    }

    @Test
    fun messagesMentionThePreventiveJournalOnSuccess() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        val journal = File.createTempFile("ordia_test_journal", ".json").apply { deleteOnExit() }
        val result = newManager(FakeBackupStore(otherData()), journal = journal).importBackup(backup)

        assertTrue(result.message, result.success)
        assertTrue(result.message.contains(journal.name))
    }

    @Test
    fun restoreRechazaTareaQueEmpiezaDespuesDeSuVencimiento() = runBlocking {
        // El invariante `startAt <= dueAt` es exactamente lo que [BackupManager]
        // valida al restaurar (l. 431, "Una tarea comienza después de su
        // vencimiento"). Una planificación que deje startAt > dueAt produce un
        // backup IRRESTAURABLE. Esta prueba ancla el contrato de la fuente de
        // verdad y motiva el fix simétrico en las 3 superficies de planificación
        // (applyBlocks, PLAN_DAY, BATCH_QUICK_TASKS) vía TaskRules.dueAtForPlannedSlot.
        val bad = sampleData().copy(
            tasks = listOf(
                TaskEntity(
                    id = 2, title = "Plan roto", projectId = 1,
                    startAt = 200L, dueAt = 100L, // startAt (200) > dueAt (100)
                    createdAt = 1000L, updatedAt = 1000L
                )
            )
        )
        val origin = newManager(FakeBackupStore(bad))
        val backup = origin.exportJson()
        val result = newManager(FakeBackupStore(otherData())).importBackup(backup)
        assertFalse("Un backup con startAt>dueAt NO debe ser restaurable", result.success)
    }

    @Test
    fun restoreAceptaTareaMensualOrdinalAncladaPorDiaDeSemana() = runBlocking {
        // c.215: "primer lunes de cada mes" codifica `recurrence=MONTHLY,
        // recurrenceDays="1:1"`. La validación de seguridad de restore (c.215 en
        // `validateRelationships`) debe ACEPTAR esta codificación; sin el fix,
        // caía a la rama "Una tarea no semanal contiene días de recurrencia
        // inesperados" → backup IRRESTAURABLE → el usuario PERDÍA la recurrencia
        // ordinal al restaurar (pérdida de datos silenciosa). Esta prueba ancla
        // que el backup/restore preserva el anclaje anti-deriva ordinal.
        val ordinal = sampleData().copy(
            tasks = listOf(
                TaskEntity(
                    id = 2, title = "Pago primer lunes", projectId = 1,
                    recurrence = RecurrenceFrequency.MONTHLY,
                    recurrenceInterval = 1,
                    recurrenceDays = "1:1", // 1er lunes de cada mes
                    dueAt = now + 86_400_000L,
                    createdAt = 1000L, updatedAt = 1000L
                )
            )
        )
        val origin = newManager(FakeBackupStore(ordinal))
        val backup = origin.exportJson()
        val result = newManager(FakeBackupStore(otherData())).importBackup(backup)
        assertTrue("Una tarea mensual ordinal ('1:1') DEBE ser restaurable", result.success)
    }

    @Test
    fun restoreRechazaTareaMensualConCodificacionOrdinalCorrupta() = runBlocking {
        // c.215: la codificación ordinal "ord:weekday" admite `ord ∈ {1,2,3,4,-1}`
        // y `weekday ∈ 1..7`. Una codificación corrupta ("9:9", ord fuera de rango)
        // debe ser RECHAZADA al restaurar: si se aceptara, el motor no la
        // decodificaría y la recurrencia caería a día-del-mes (deriva silenciosa,
        // el bug mismo que c.215 corrige). Esta prueba protege la integridad de
        // los datos restaurados.
        val corrupted = sampleData().copy(
            tasks = listOf(
                TaskEntity(
                    id = 2, title = "Roto", projectId = 1,
                    recurrence = RecurrenceFrequency.MONTHLY,
                    recurrenceInterval = 1,
                    recurrenceDays = "9:9", // ord 9 y weekday 9 ambos fuera de rango
                    dueAt = now + 86_400_000L,
                    createdAt = 1000L, updatedAt = 1000L
                )
            )
        )
        val origin = newManager(FakeBackupStore(corrupted))
        val backup = origin.exportJson()
        val result = newManager(FakeBackupStore(otherData())).importBackup(backup)
        assertFalse("Una tarea mensual con codificación ordinal corrupta NO debe ser restaurable", result.success)
    }

    @Test
    fun restoreAceptaSesionAntiguaConActualMinutesCero() = runBlocking {
        // c.219: la migración 5→6 añadió `actualMinutes` con `DEFAULT 0` (sin
        // backfill). Las sesiones de enfoque COMPLETADAS que ya existían
        // quedaron con `actualMinutes = 0` aunque `endedAt - startedAt > 0`.
        // La validación de restore exigía `actualMinutes == (endedAt-startedAt)/60000`,
        // así que un backup que contuviera UNA de esas sesiones antiguas era
        // IRRESTAURABLE: el usuario perdía TODO (tareas, notas, hábitos…) al
        // migrar a un teléfono nuevo. Esta prueba ancla que una sesión legítima
        // con `actualMinutes` subcontado DEBE ser restaurable (datos sagrados).
        val withOldSession = sampleData().copy(
            focusSessions = listOf(
                FocusSessionEntity(
                    id = 5, taskId = 2,
                    startedAt = 1000L, endedAt = 1000L + 25 * 60_000L,
                    plannedMinutes = 25, actualMinutes = 0, // migración: default 0
                    completed = true
                )
            )
        )
        val origin = newManager(FakeBackupStore(withOldSession))
        val backup = origin.exportJson()
        val result = newManager(FakeBackupStore(otherData())).importBackup(backup)
        assertTrue("Una sesión antigua (actualMinutes=0) NO debe bloquear la restauración", result.success)
    }

    @Test
    fun restoreRechazaSesionConTiempoFabricado() = runBlocking {
        // c.219: el fix relaja el invariante a `actualMinutes in 0..measured`
        // para no rechazar sesiones antiguas/subcontadas, PERO debe seguir
        // rechazando tiempo FABRICADO: `actualMinutes` mayor que el wall-clock
        // transcurrido sería inflar minutos de enfoque (XP/guardián) de
        // fuentes no confiables. Esta prueba protege el invariante
        // anti-corrupción que la relajación preserva.
        val fabricated = sampleData().copy(
            focusSessions = listOf(
                FocusSessionEntity(
                    id = 5, taskId = 2,
                    startedAt = 1000L, endedAt = 1000L + 10 * 60_000L, // 10 min reales
                    plannedMinutes = 25, actualMinutes = 999, // fabricado: 999 > 10
                    completed = true
                )
            )
        )
        val origin = newManager(FakeBackupStore(fabricated))
        val backup = origin.exportJson()
        val result = newManager(FakeBackupStore(otherData())).importBackup(backup)
        assertFalse("Una sesión con actualMinutes > duración real NO debe ser restaurable", result.success)
    }
}

private fun UserPreferences.toTestJson(): JSONObject = JSONObject()
    .put("themeMode", themeMode.name)
    .put("interfaceMode", interfaceMode.name)
    .put("guardianEnabled", guardianEnabled)
    .put("guardianMode", guardianMode.name)
    .put("guardianName", guardianName)
    .put("guardianSpecies", guardianSpecies.name)
    .put("guardianBond", guardianBond)
    .put("guardianExperience", guardianExperience)
    .put("guardianLastInteraction", guardianLastInteraction)
    .put("guardianLastEvent", guardianLastEvent)
    .put("guardianAnimations", guardianAnimations)
    .put("guardianInteractionEpochDay", guardianInteractionEpochDay)
    .put("guardianInteractionsToday", guardianInteractionsToday)
    .put("autoUpdateEnabled", autoUpdateEnabled)
    .put("autoDownloadUpdates", autoDownloadUpdates)
    .put("quietStartMinutes", quietStartMinutes)
    .put("quietEndMinutes", quietEndMinutes)
    .put("onboardingComplete", onboardingComplete)
    .put("weekStartsMonday", weekStartsMonday)
    .put("defaultFocusMinutes", defaultFocusMinutes)
    .put("reduceMotion", reduceMotion)
    .put("compactNavigation", compactNavigation)
