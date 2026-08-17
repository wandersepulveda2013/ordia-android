package com.ordia.app.backup

import androidx.room.withTransaction
import com.ordia.app.data.local.OrdiaDatabase

/**
 * Contrato de almacenamiento para el reemplazo atómico de datos durante una
 * restauración.
 *
 * [replaceAll] debe ejecutarse como UNA única transacción: o reemplaza todas
 * las colecciones o no aplica ningún cambio (rollback total). En producción lo
 * implementa [RoomBackupStore] con `withTransaction` de Room; en pruebas se
 * usa una implementación en memoria con el mismo contrato.
 */
interface BackupStore {
    /** Borra todos los datos existentes e inserta los del backup, atómicamente. */
    suspend fun replaceAll(data: RestoreData)

    /** Lee el estado persistido actual (para verificación posterior). */
    suspend fun readAll(): RestoreData
}

/** Implementación Room real: todo el reemplazo dentro de una transacción. */
class RoomBackupStore(private val database: OrdiaDatabase) : BackupStore {
    override suspend fun replaceAll(data: RestoreData) {
        database.withTransaction {
            // Orden de borrado e inserción coherente con las relaciones FK:
            // se eliminan primero las tablas hijas y se insertan las padres antes.
            database.observationDao().deleteAllConsentEvents()
            database.observationDao().deleteAllSources()
            database.automationLogDao().deleteAll()
            database.automationRuleDao().deleteAll()
            database.conversationDao().deleteAllCommitments()
            database.conversationDao().deleteAllConversations()
            database.captureDao().deleteAllDrafts()
            database.captureDao().deleteAll()
            database.attachmentDao().deleteAll()
            database.taskTagDao().deleteAll()
            database.tagDao().deleteAll()
            database.routineStepDao().deleteAll()
            database.routineDao().deleteAll()
            database.focusSessionDao().deleteAll()
            database.habitLogDao().deleteAll()
            database.habitDao().deleteAll()
            database.noteVersionDao().deleteAll()
            database.noteLabelCrossRefDao().deleteAll()
            database.noteLabelDao().deleteAll()
            database.noteFolderDao().deleteAll()
            database.noteDao().deleteAll()
            database.taskDao().deleteAll()
            database.projectDao().deleteAll()

            database.projectDao().insertAll(data.projects)
            database.taskDao().insertAll(data.tasks)
            database.noteDao().insertAll(data.notes)
            database.noteFolderDao().insertAll(data.noteFolders)
            database.noteLabelDao().insertAll(data.noteLabels)
            database.noteLabelCrossRefDao().insertAll(data.noteLabelCrossRefs)
            database.noteVersionDao().insertAll(data.noteVersions)
            database.habitDao().insertAll(data.habits)
            database.habitLogDao().insertAll(data.habitLogs)
            database.focusSessionDao().insertAll(data.focusSessions)
            database.routineDao().insertAll(data.routines)
            database.routineStepDao().insertAll(data.routineSteps)
            database.tagDao().insertAll(data.tags)
            database.taskTagDao().insertAll(data.taskTags)
            database.attachmentDao().insertAll(data.attachments)
            database.captureDao().insertAll(data.captures)
            database.captureDao().insertDrafts(data.captureDrafts)
            database.conversationDao().insertConversations(data.conversations)
            database.conversationDao().restoreCommitments(data.commitments)
            database.observationDao().restoreSources(data.observedSources)
            database.observationDao().restoreConsentEvents(data.consentEvents)
            database.automationRuleDao().insertAll(data.automationRules)
            database.automationLogDao().insertAll(data.automationLogs)
        }
    }

    override suspend fun readAll(): RestoreData = RestoreData(
        projects = database.projectDao().getAllNow(),
        tasks = database.taskDao().getAllNow(),
        notes = database.noteDao().getAllNow(),
        noteFolders = database.noteFolderDao().getAllNow(),
        noteLabels = database.noteLabelDao().getAllNow(),
        noteLabelCrossRefs = database.noteLabelCrossRefDao().getAllNow(),
        noteVersions = database.noteVersionDao().getAllNow(),
        habits = database.habitDao().getAllNow(),
        habitLogs = database.habitLogDao().getAllNow(),
        focusSessions = database.focusSessionDao().getAllNow(),
        routines = database.routineDao().getAllNow(),
        routineSteps = database.routineStepDao().getAllNow(),
        tags = database.tagDao().getAllNow(),
        taskTags = database.taskTagDao().getAllNow(),
        attachments = database.attachmentDao().getAllNow(),
        captures = database.captureDao().getAllNow(),
        captureDrafts = database.captureDao().getDraftsNow(),
        conversations = database.conversationDao().getConversationsNow(),
        commitments = database.conversationDao().getCommitmentsNow(),
        observedSources = database.observationDao().getSourcesNow(),
        consentEvents = database.observationDao().getConsentEventsNow(),
        automationRules = database.automationRuleDao().getAllNow(),
        automationLogs = database.automationLogDao().getAllNow()
    )
}
