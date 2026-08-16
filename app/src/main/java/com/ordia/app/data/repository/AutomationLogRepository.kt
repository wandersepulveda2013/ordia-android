package com.ordia.app.data.repository

import com.ordia.app.data.local.AutomationLogDao
import com.ordia.app.data.local.AutomationLogEntity
import kotlinx.coroutines.flow.Flow

/** Historial de automatizaciones del asistente (para deshacer y auditar cambios). */
class AutomationLogRepository(private val dao: AutomationLogDao) {
    fun recent(limit: Int = 50): Flow<List<AutomationLogEntity>> = dao.observeRecent(limit)

    suspend fun latestNotUndone(): AutomationLogEntity? = dao.latestNotUndone()

    suspend fun getById(id: Long): AutomationLogEntity? = dao.getById(id)

    suspend fun insert(log: AutomationLogEntity): Long = dao.insert(log)

    suspend fun markUndone(id: Long) = dao.markUndone(id)

    suspend fun deleteAll() = dao.deleteAll()
}
