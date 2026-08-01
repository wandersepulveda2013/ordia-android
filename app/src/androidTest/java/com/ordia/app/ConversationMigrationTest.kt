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
class ConversationMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrdiaDatabase::class.java
    )

    @Test
    fun migration4To5_preservesDataAndCreatesConversationGraph() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO captures
                    (id, content, source, requestedTarget, resolvedTarget, status, attachmentUri, mimeType,
                     fingerprint, resultType, resultId, errorCode, createdAt, updatedAt)
                VALUES
                    (9, 'Captura existente', 'COMPOSER', 'AUTO', 'INBOX', 'PENDING', '', '',
                     '${"a".repeat(64)}', '', NULL, '', 1000, 1000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            OrdiaDatabase.MIGRATION_4_5
        )

        migrated.query("SELECT content FROM captures WHERE id = 9").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Captura existente", cursor.getString(0))
        }
        migrated.execSQL(
            """
            INSERT INTO conversations
                (id, sourceType, sourcePackage, title, participants, summary, rawContent,
                 retainsOriginal, contentHash, messageCount, createdAt, updatedAt)
            VALUES
                (11, 'IMPORTED', '', 'Chat', 'Ana', 'Resumen privado', '', 0,
                 '${"b".repeat(64)}', 2, 2000, 2000)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO commitments
                (conversationId, kind, owner, actor, action, location, dueAt, confidence,
                 suggestedReminderAt, reviewStatus, fingerprint, resultTaskId, createdAt, updatedAt)
            VALUES
                (11, 'REQUEST', 'SELF', 'Ana', 'Enviar informe', '', NULL, 0.9,
                 NULL, 'PENDING', '${"c".repeat(64)}', NULL, 2000, 2000)
            """.trimIndent()
        )
        migrated.query("SELECT action FROM commitments WHERE conversationId = 11").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Enviar informe", cursor.getString(0))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "conversation-migration-test"
    }
}
