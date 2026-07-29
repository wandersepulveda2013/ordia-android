package com.ordia.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSmokeTest {
    private lateinit var database: OrdiaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrdiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun relatedData_roundTripsThroughRoom() = runBlocking {
        val projectId = database.projectDao().insert(ProjectEntity(name = "Proyecto de prueba"))
        val taskId = database.taskDao().insert(TaskEntity(title = "Tarea de prueba", projectId = projectId))
        val noteId = database.noteDao().insert(NoteEntity(title = "Nota de prueba", projectId = projectId))
        database.attachmentDao().insert(
            AttachmentEntity(
                ownerType = AttachmentOwnerType.NOTE,
                ownerId = noteId,
                uri = "content://ordia.test/document/1",
                displayName = "archivo.txt",
                mimeType = "text/plain",
                sizeBytes = 12
            )
        )

        assertNotNull(database.taskDao().getById(taskId))
        assertEquals(projectId, database.taskDao().getById(taskId)?.projectId)
        assertEquals(1, database.noteDao().observeByProject(projectId).first().size)
        assertEquals(1, database.attachmentDao().observeForOwner(AttachmentOwnerType.NOTE, noteId).first().size)
    }
}
