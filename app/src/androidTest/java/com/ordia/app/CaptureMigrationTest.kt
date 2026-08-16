package com.ordia.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ordia.app.data.local.OrdiaDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrdiaDatabase::class.java
    )

    @Test
    fun migration3To4_preservesExistingDataAndCreatesCaptureTables() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO projects
                    (id, name, description, colorHex, icon, status, targetDate, archived, createdAt, updatedAt)
                VALUES
                    (7, 'Proyecto existente', '', '#C9A86A', 'folder', 'ACTIVE', NULL, 0, 1000, 1000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            4,
            true,
            OrdiaDatabase.MIGRATION_3_4
        )

        migrated.query("SELECT name FROM projects WHERE id = 7").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Proyecto existente", cursor.getString(0))
        }
        migrated.execSQL(
            """
            INSERT INTO captures
                (content, source, requestedTarget, resolvedTarget, status, attachmentUri, mimeType,
                 fingerprint, resultType, resultId, errorCode, createdAt, updatedAt)
            VALUES
                ('Texto intacto', 'SHARE', 'AUTO', 'INBOX', 'PENDING', '', '',
                 '${"a".repeat(64)}', '', NULL, '', 2000, 2000)
            """.trimIndent()
        )
        migrated.query("SELECT content FROM captures").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Texto intacto", cursor.getString(0))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "capture-migration-test"
    }
}
