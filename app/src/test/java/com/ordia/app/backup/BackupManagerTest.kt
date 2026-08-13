package com.ordia.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ordia.app.data.local.OrdiaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupManagerTest {

    private lateinit var database: OrdiaDatabase
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrdiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupManager = BackupManager(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportJson_createsValidBackupWithData() = runBlocking {
        val projectId = database.projectDao().insert(com.ordia.app.data.local.ProjectEntity(name = "Test Project"))
        database.taskDao().insert(com.ordia.app.data.local.TaskEntity(title = "Test Task", projectId = projectId))

        val jsonString = backupManager.exportJson()

        val jsonObject = JSONObject(jsonString)
        assertEquals("ordia-backup", jsonObject.getString("format"))
        assertEquals(2, jsonObject.getInt("version"))
        assertTrue(jsonObject.has("createdAt"))

        val projectsArray = jsonObject.getJSONArray("projects")
        assertEquals(1, projectsArray.length())
        assertEquals("Test Project", projectsArray.getJSONObject(0).getString("name"))

        val tasksArray = jsonObject.getJSONArray("tasks")
        assertEquals(1, tasksArray.length())
        assertEquals("Test Task", tasksArray.getJSONObject(0).getString("title"))
    }

    @Test
    fun importJson_withValidData_restoresDatabase() = runBlocking {
        val validJson = """
            {
                "format": "ordia-backup",
                "version": 2,
                "createdAt": 1700000000000,
                "projects": [
                    {
                        "id": 1,
                        "name": "Restored Project",
                        "description": "",
                        "colorHex": "#C9A86A",
                        "icon": "folder",
                        "status": "ACTIVE",
                        "archived": false,
                        "createdAt": 1700000000000,
                        "updatedAt": 1700000000000
                    }
                ],
                "tasks": [
                    {
                        "id": 1,
                        "title": "Restored Task",
                        "details": "",
                        "projectId": 1,
                        "durationMinutes": 25,
                        "priority": "NORMAL",
                        "status": "INBOX",
                        "completed": false,
                        "recurrence": "NONE",
                        "recurrenceInterval": 1,
                        "recurrenceDays": "",
                        "sortOrder": 0,
                        "flagged": false,
                        "archived": false,
                        "createdAt": 1700000000000,
                        "updatedAt": 1700000000000
                    }
                ]
            }
        """.trimIndent()

        val result = backupManager.importJson(validJson)

        assertTrue(result.success)

        val projects = database.projectDao().getAllNow()
        assertEquals(1, projects.size)
        assertEquals("Restored Project", projects[0].name)

        val tasks = database.taskDao().getAllNow()
        assertEquals(1, tasks.size)
        assertEquals("Restored Task", tasks[0].title)
    }

    @Test
    fun importJson_withInvalidFormat_returnsFailure() = runBlocking {
        val invalidJson = """
            {
                "format": "wrong-format",
                "version": 2,
                "projects": []
            }
        """.trimIndent()

        val result = backupManager.importJson(invalidJson)

        assertFalse(result.success)
        assertTrue(result.message.contains("no es una copia de seguridad"))
    }
}
