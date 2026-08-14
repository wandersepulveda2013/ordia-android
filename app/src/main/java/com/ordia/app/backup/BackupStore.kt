package com.ordia.app.backup

/**
 * Contrato de almacenamiento para el reemplazo atómico de datos durante una
 * restauración.
 *
 * [replaceAll] debe ejecutarse como UNA única transacción: o reemplaza todas
 * las colecciones o no aplica ningún cambio (rollback total). En producción lo
 * implementa [RoomBackupStore] con `withTransaction` de Room; en pruebas se
 * usa una implementación en memoria con el mismo contrato.
 *
 * La interfaz se aisla de su implementación Room ([RoomBackupStore]) para que
 * [BackupManager] y sus tests sean verificables en JVM pura sin Android SDK:
 * el contrato no depende de Room, solo la implementación de producción sí.
 */
interface BackupStore {
    /** Borra todos los datos existentes e inserta los del backup, atómicamente. */
    suspend fun replaceAll(data: RestoreData)

    /** Lee el estado persistido actual (para verificación posterior). */
    suspend fun readAll(): RestoreData
}
