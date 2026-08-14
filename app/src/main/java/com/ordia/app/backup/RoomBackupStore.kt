package com.ordia.app.backup

import androidx.room.withTransaction
import com.ordia.app.data.local.OrdiaDatabase

/**
 * Implementación Room real de [BackupStore]: todo el reemplazo de colecciones
 * se ejecuta dentro de una única transacción para garantizar atomicidad (o se
 * aplican todos los cambios o ninguno, con rollback total).
 *
 * Extraída de [BackupStore] para mantener el contrato libre de dependencias
 * Android y permitir que [BackupManager] y sus tests se ejecuten en JVM pura.
 */
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
            database.noteDao().deleteAll()
            database.taskDao().deleteAll()
            database.projectDao().deleteAll()

            database.projectDao().insertAll(data.projects)
            database.taskDao().insertAll(data.tasks)
            database.noteDao().insertAll(data.notes)
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
