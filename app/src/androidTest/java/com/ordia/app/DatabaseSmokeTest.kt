package com.ordia.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.CaptureDraftEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
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
        database.captureDao().insert(CaptureEntity(content = "Texto de captura", fingerprint = "b".repeat(64)))
        database.captureDao().upsertDraft(CaptureDraftEntity(content = "Borrador recuperable"))
        val conversationId = database.conversationDao().insertGraph(
            conversation = ConversationEntity(
                sourceType = ConversationSourceType.SHARED,
                title = "Chat de prueba",
                summary = "Una solicitud pendiente.",
                contentHash = "c".repeat(64),
                messageCount = 1
            ),
            commitments = listOf(
                CommitmentEntity(
                    conversationId = 0,
                    kind = CommitmentKind.REQUEST,
                    owner = CommitmentOwner.SELF,
                    action = "Enviar informe",
                    confidence = 0.9f,
                    fingerprint = "d".repeat(64)
                )
            )
        )
        val observedConversationId = database.conversationDao().insertGraph(
            conversation = ConversationEntity(
                sourceType = ConversationSourceType.NOTIFICATION,
                sourcePackage = "com.whatsapp",
                title = "Compromiso observado",
                summary = "Una propuesta pendiente.",
                contentHash = "e".repeat(64),
                messageCount = 1
            ),
            commitments = listOf(
                CommitmentEntity(
                    conversationId = 0,
                    kind = CommitmentKind.REQUEST,
                    owner = CommitmentOwner.SELF,
                    action = "Responder mañana",
                    confidence = 0.9f,
                    fingerprint = "f".repeat(64)
                )
            )
        )
        database.observationDao().configureSource(
            packageName = "com.whatsapp",
            displayName = "WhatsApp",
            enabled = true,
            onlyCommitments = true,
            now = 1000L
        )

        assertNotNull(database.taskDao().getById(taskId))
        assertEquals(projectId, database.taskDao().getById(taskId)?.projectId)
        assertEquals(1, database.noteDao().observeByProject(projectId).first().size)
        assertEquals(1, database.attachmentDao().observeForOwner(AttachmentOwnerType.NOTE, noteId).first().size)
        assertEquals("Texto de captura", database.captureDao().getAllNow().single().content)
        assertEquals("Borrador recuperable", database.captureDao().getDraftsNow().single().content)
        assertEquals(conversationId, database.conversationDao().getCommitmentsNow().single().conversationId)
        assertEquals("Chat de prueba", database.conversationDao().getConversation(conversationId)?.title)
        assertEquals(true, database.observationDao().getSource("com.whatsapp")?.enabled)
        assertEquals("com.whatsapp", database.observationDao().getConsentEventsNow().single().sourcePackage)

        database.conversationDao().deleteConversationsBySource(ConversationSourceType.NOTIFICATION)
        assertEquals(null, database.conversationDao().getConversation(observedConversationId))
        assertNotNull(database.conversationDao().getConversation(conversationId))
        assertEquals(true, database.observationDao().getSource("com.whatsapp")?.enabled)
    }
}
