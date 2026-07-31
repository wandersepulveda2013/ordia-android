package com.ordia.app.backup

import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskTagCrossRef
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
    private fun rewrap(root: JSONObject, version: Int = 4): String {
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
        // Solo la tarea futura abierta se reprograma (no la completada ni las pasadas).
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
    fun verificationFailureReportsPreventiveJournal() = runBlocking {
        val origin = newManager(FakeBackupStore(sampleData()))
        val backup = origin.exportJson()

        val store = FakeBackupStore(otherData())
        store.corruptReadAfterReplace = true
        val journal = journalFile()
        val result = newManager(store, journal = journal).importBackup(backup)

        assertFalse(result.success)
        assertTrue(result.message.contains("no pudo verificarse"))
        assertTrue(result.message.contains(journal.name))
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
