package com.ordia.app.backup

import com.ordia.app.data.preferences.UserPreferences
import org.json.JSONObject

/**
 * Operaciones de preferencias que el flujo de respaldo necesita.
 *
 * Se aisla para que [BackupManager] sea testeable en JVM sin DataStore:
 * [com.ordia.app.data.preferences.PreferencesRepository] la implementa.
 */
interface BackupPreferences {
    suspend fun exportJson(): JSONObject

    fun decodeBackupJson(json: JSONObject): UserPreferences

    suspend fun snapshot(): UserPreferences

    suspend fun restoreSnapshot(value: UserPreferences, allowGuardianEnabled: Boolean = false)
}
