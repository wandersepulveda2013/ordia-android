package com.ordia.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ordia.app.data.local.OrdiaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migración 8 → 9: el modelo de notas se enriquece (folderId, favorite, locked,
 * colorHex, trashed, trashedAt) y se añaden carpetas, etiquetas, relación
 * nota-etiqueta e historial de versiones. Las notas existentes deben sobrevivir
 * con valores por defecto seguros (no archivadas, no en papelera, etc.).
 */
@RunWith(AndroidJUnit4::class)
class NotesMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrdiaDatabase::class.java
    )

    @Test
    fun migration8To9_preservesNotesAndAddsColumnsAndTables() {
        helper.createDatabase(DATABASE_NAME, 8).apply {
            execSQL(
                """
                INSERT INTO notes (id, title, body, blocksData, projectId, pinned, archived, createdAt, updatedAt)
                VALUES (1, 'Nota existente', 'Cuerpo previo', '', NULL, 1, 0, 1000, 2000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            9,
            true,
            OrdiaDatabase.MIGRATION_8_9
        )

        migrated.query("SELECT title, body, pinned, favorite, locked, trashed, folderId, colorHex FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Nota existente", c.getString(0))
            assertEquals("Cuerpo previo", c.getString(1))
            assertEquals(1, c.getInt(2))        // pinned conservado
            assertEquals(0, c.getInt(3))        // favorite = false por defecto
            assertEquals(0, c.getInt(4))        // locked = false por defecto
            assertEquals(0, c.getInt(5))        // trashed = false por defecto
            assertTrue(c.isNull(6))             // folderId NULL
            assertTrue(c.isNull(7) || c.getString(7).isNullOrEmpty()) // colorHex vacío
        }

        // Las nuevas tablas existen y aceptan inserciones.
        migrated.execSQL(
            """
            INSERT INTO note_folders (id, name, colorHex, parentFolderId, createdAt, updatedAt)
            VALUES (10, 'Personal', '', NULL, 3000, 3000)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO note_labels (id, name, colorHex)
            VALUES (20, 'ideas', '')
            """.trimIndent()
        )
        migrated.execSQL("INSERT INTO note_label_cross_ref (noteId, labelId) VALUES (1, 20)")
        migrated.execSQL(
            """
            INSERT INTO note_versions (id, noteId, title, blocksData, body, createdAt)
            VALUES (30, 1, 'Nota existente', '', 'Cuerpo previo', 1000)
            """.trimIndent()
        )

        migrated.query("SELECT COUNT(*) FROM note_label_cross_ref WHERE noteId = 1").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migration8To9_doesNotLoseArchivedFlagSemantics() {
        helper.createDatabase(DATABASE_NAME, 8).apply {
            execSQL(
                """
                INSERT INTO notes (id, title, body, blocksData, projectId, pinned, archived, createdAt, updatedAt)
                VALUES (2, 'Archivada vieja', 'x', '', NULL, 0, 1, 1000, 2000)
                """.trimIndent()
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME, 9, true, OrdiaDatabase.MIGRATION_8_9
        )
        migrated.query("SELECT archived, trashed FROM notes WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0)) // archived se conserva (no se destruye)
            assertFalse(c.getInt(1) == 1) // pero NO se convierte en trashed
        }
        migrated.close()
    }

    companion object {
        private const val DATABASE_NAME = "notes-migration-test"
    }
}
