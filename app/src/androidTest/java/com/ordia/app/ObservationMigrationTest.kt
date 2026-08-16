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
class ObservationMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrdiaDatabase::class.java
    )

    @Test
    fun migration5To6_preservesConversationsAndCreatesConsentTables() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL(
                """
                INSERT INTO conversations
                    (id, sourceType, sourcePackage, title, participants, summary, rawContent,
                     retainsOriginal, contentHash, messageCount, createdAt, updatedAt)
                VALUES
                    (17, 'IMPORTED', '', 'Chat existente', '', 'Resumen', '', 0,
                     '${"a".repeat(64)}', 1, 1000, 1000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            6,
            true,
            OrdiaDatabase.MIGRATION_5_6
        )

        migrated.query("SELECT title FROM conversations WHERE id = 17").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Chat existente", cursor.getString(0))
        }
        migrated.execSQL(
            """
            INSERT INTO observed_sources
                (packageName, displayName, enabled, onlyCommitments, createdAt, updatedAt)
            VALUES ('com.whatsapp', 'WhatsApp', 1, 1, 2000, 2000)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO consent_events (eventType, sourcePackage, occurredAt)
            VALUES ('SOURCE_ENABLED', 'com.whatsapp', 2000)
            """.trimIndent()
        )
        migrated.query("SELECT enabled, onlyCommitments FROM observed_sources WHERE packageName = 'com.whatsapp'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    private companion object {
        const val DATABASE_NAME = "observation-migration-test"
    }
}
