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
class AutomationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OrdiaDatabase::class.java)

    @Test fun migration6To7PreservesExistingLogsAndCreatesRules() {
        helper.createDatabase("automation-migration-test", 6).apply {
            execSQL("INSERT INTO automation_log (id,type,description,affectedTaskIdsJson,undoPayloadJson,undone,createdAt) VALUES (9,'day_plan','Existente','[]','{}',0,1000)")
            close()
        }
        val db = helper.runMigrationsAndValidate("automation-migration-test", 7, true, OrdiaDatabase.MIGRATION_6_7)
        db.query("SELECT description FROM automation_log WHERE id=9").use { it.moveToFirst(); assertEquals("Existente", it.getString(0)) }
        db.execSQL("INSERT INTO automation_rules (name,instruction,trigger,condition,action,explanation,enabled,frequencyMinutes,maxRunsPerDay,lastRunAt,lastResult,lastError,definitionHash,createdAt,updatedAt) VALUES ('Día','Cada mañana','DAILY_MORNING','HAS_INBOX_TASKS','PLAN_DAY','Explica',0,60,1,NULL,'NEVER','', '${"a".repeat(64)}',1000,1000)")
        db.query("SELECT enabled FROM automation_rules").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }
}
