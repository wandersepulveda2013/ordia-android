package com.ordia.app.domain

import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTaskConverterTest {

    @Test
    fun noteToTask_preservesTitleDetailsAndProject() {
        val note = NoteEntity(id = 9, title = "Idea para el informe", body = "Revisar cifras y redactar conclusiones.", projectId = 3)

        val task = NoteTaskConverter.noteToTask(note)

        assertEquals("Idea para el informe", task.title)
        assertEquals("Revisar cifras y redactar conclusiones.", task.details)
        assertEquals(3L, task.projectId)
        assertEquals(TaskStatus.INBOX, task.status)
    }

    @Test
    fun noteToTask_landsInInboxBecauseItHasNoDates() {
        val task = NoteTaskConverter.noteToTask(NoteEntity(title = "Solo texto"))

        assertEquals(TaskStatus.INBOX, task.status)
        assertEquals(null, task.dueAt)
    }

    @Test
    fun taskToNote_preservesTitleDetailsAndProject() {
        val task = TaskEntity(id = 5, title = "Llamar al cliente", details = "Confirmar presupuesto final.", projectId = 2)

        val note = NoteTaskConverter.taskToNote(task)

        assertEquals("Llamar al cliente", note.title)
        assertEquals("Confirmar presupuesto final.", note.body)
        assertEquals(2L, note.projectId)
    }

    @Test
    fun roundTripKeepsTitleAndText() {
        val note = NoteEntity(title = "Ronda", body = "Texto estable.", projectId = 1)

        val task = NoteTaskConverter.noteToTask(note)
        val back = NoteTaskConverter.taskToNote(task)

        assertEquals("Ronda", back.title)
        assertEquals("Texto estable.", back.body)
        assertEquals(1L, back.projectId)
    }

    @Test
    fun blankTitleGetsFallback() {
        assertEquals("Sin título", NoteTaskConverter.noteToTask(NoteEntity(title = "   ")).title)
        assertEquals("Sin título", NoteTaskConverter.taskToNote(TaskEntity(title = "  ")).title)
    }
}
