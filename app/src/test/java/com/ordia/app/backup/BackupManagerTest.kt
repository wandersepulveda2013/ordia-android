package com.ordia.app.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ordia.app.data.local.OrdiaDatabase
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.mock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupManagerTest {
    @Test
    fun testImportJsonInvalidFormat() = runTest {
        val database = mock<OrdiaDatabase>()
        val manager = BackupManager(database)

        val json = JSONObject().put("format", "other-format").toString()
        val result = manager.importJson(json)

        assertFalse(result.success)
        assertEquals("El archivo no es una copia de seguridad de Ordia.", result.message)
    }

    @Test
    fun testImportJsonInvalidJson() = runTest {
        val database = mock<OrdiaDatabase>()
        val manager = BackupManager(database)

        val json = "not a json string"
        val result = manager.importJson(json)

        assertFalse(result.success)
        assertEquals("El archivo no contiene JSON válido.", result.message)
    }

    @Test
    fun testImportJsonDatabaseError() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OrdiaDatabase::class.java
        ).build()

        val manager = BackupManager(database)

        // Make the database read-only to force a SQLiteException when trying to write during import.
        database.openHelper.writableDatabase.execSQL("PRAGMA query_only = ON;")

        val json = JSONObject().put("format", "ordia-backup").toString()
        val result = manager.importJson(json)

        assertFalse(result.success)
        assertTrue(result.message.startsWith("No se pudo restaurar la copia:"))
        assertTrue(result.message.contains("readonly database"))

        database.close()
    }
}
