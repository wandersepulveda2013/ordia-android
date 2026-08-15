package com.ordia.app.domain

import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus

/**
 * Puente entre notas y tareas: convierte una nota en tarea y una tarea en nota
 * conservando título, detalle y proyecto. La operación es reversible porque la
 * fuente se archiva (no se elimina) y el destino conserva la información.
 */
object NoteTaskConverter {

    /** Una nota sin fecha se convierte en tarea de la Bandeja, lista para planificar. */
    fun noteToTask(note: NoteEntity): TaskEntity = TaskEntity(
        title = note.title.trim().ifBlank { DEFAULT_TITLE },
        details = NoteBlockCodec.toPlainText(NoteBlockCodec.decode(note.blocksData, note.body)),
        projectId = note.projectId,
        status = TaskStatus.INBOX
    )

    /** Una tarea sin estructura de subtareas se convierte en nota con su detalle. */
    fun taskToNote(task: TaskEntity): NoteEntity = NoteEntity(
        title = task.title.trim().ifBlank { DEFAULT_TITLE },
        body = task.details,
        projectId = task.projectId
    )

    private const val DEFAULT_TITLE = "Sin título"
}
