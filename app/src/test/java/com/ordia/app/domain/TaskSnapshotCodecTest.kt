package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSnapshotCodecTest {

    private fun sampleTask(id: Long = 7): TaskEntity = TaskEntity(
        id = id,
        title = "Preparar informe",
        details = "Con datos del mes",
        projectId = 3L,
        parentTaskId = null,
        startAt = 1_800_000_000_000L,
        dueAt = 1_800_000_360_000L,
        reminderAt = 1_800_000_000_000L - 600_000L,
        durationMinutes = 60,
        priority = TaskPriority.HIGH,
        status = TaskStatus.PLANNED,
        completed = false,
        completedAt = null,
        recurrence = RecurrenceFrequency.WEEKLY,
        recurrenceInterval = 1,
        recurrenceDays = "2,5",
        sortOrder = 4,
        flagged = true,
        archived = false,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L
    )

    @Test
    fun encodeMapDecodeMapRoundTripPreservesAllFields() {
        val original = mapOf(
            7L to sampleTask(7),
            9L to sampleTask(9).copy(title = "Otra", priority = TaskPriority.NORMAL, status = TaskStatus.INBOX)
        )

        val decoded = TaskSnapshotCodec.decodeMap(TaskSnapshotCodec.encodeMap(original))

        assertEquals(original, decoded)
    }

    @Test
    fun decodeMapHandlesNullFieldsAndEmptyInput() {
        val plain = sampleTask().copy(
            projectId = null,
            parentTaskId = null,
            startAt = null,
            dueAt = null,
            reminderAt = null,
            completedAt = null,
            recurrence = RecurrenceFrequency.NONE,
            recurrenceInterval = 0,
            recurrenceDays = "",
            sortOrder = 0,
            flagged = false,
            archived = false,
            status = TaskStatus.INBOX,
            priority = TaskPriority.NORMAL
        )

        val decoded = TaskSnapshotCodec.decodeMap(TaskSnapshotCodec.encodeMap(mapOf(7L to plain)))[7L]

        assertEquals(plain, decoded)
        assertNull(decoded?.startAt)
        assertNull(decoded?.reminderAt)
        assertTrue(TaskSnapshotCodec.decodeMap("").isEmpty())
        assertTrue(TaskSnapshotCodec.decodeMap("no es json").isEmpty())
    }

    @Test
    fun encodeIdsDecodeIdsRoundTrip() {
        val ids = listOf(1L, 2L, 3L)

        assertEquals(ids, TaskSnapshotCodec.decodeIds(TaskSnapshotCodec.encodeIds(ids)))
        assertTrue(TaskSnapshotCodec.decodeIds("").isEmpty())
        assertTrue(TaskSnapshotCodec.decodeIds("[]").isEmpty())
    }

    /**
     * Protege la ruta de deshacer/restaurar: un snapshot que omite un campo de
     * fecha opcional (backup de versión anterior, JSON truncado o campo añadido
     * después) debe decodificar a `null`, NUNCA a `0` (época 1970). Antes,
     * `optLong` devolvía 0 para claves ausentes y restaurar la tarea escribía
     * timestamps de 1970 (vencimiento/recordatorio/inicio fantasma).
     */
    @Test
    fun decodeMapAbsentNullableFieldsResolveToNullNotEpochZero() {
        // Snapshot mínimo: faltan startAt/dueAt/reminderAt/completedAt/projectId/parentTaskId
        // (p.ej. backup de una versión anterior o JSON truncado restaurado).
        val minimal = """{"1":{"id":1,"title":"x","createdAt":1700000000000,"updatedAt":1700000000000}}"""

        val decoded = TaskSnapshotCodec.decodeMap(minimal)[1L]!!

        assertNull(decoded.startAt)
        assertNull(decoded.dueAt)
        assertNull(decoded.reminderAt)
        assertNull(decoded.completedAt)
        assertNull(decoded.projectId)
        assertNull(decoded.parentTaskId)
    }

    @Test
    fun decodeMapPresentNullStillResolvesToNull() {
        // Clave presente con valor null explícito (round-trip normal): también null.
        val plain = sampleTask(1).copy(startAt = null, dueAt = null, reminderAt = null, completedAt = null)
        val decoded = TaskSnapshotCodec.decodeMap(TaskSnapshotCodec.encodeMap(mapOf(1L to plain)))[1L]!!
        assertNull(decoded.startAt)
        assertNull(decoded.dueAt)
        assertNull(decoded.reminderAt)
        assertNull(decoded.completedAt)
    }

    /**
     * Protege la identidad en la ruta de deshacer: la CLAVE del mapa es el id
     * autoritativo (es el que `decodeMap` usa para `result[id]` y el que
     * `undoLastAutomation` pasa a `taskRepository.get(id)` y a
     * `taskRepository.update(snapshot...)`). Si el objeto embebido OMITE "id"
     * (snapshot de versión antigua, JSON truncado, campo añadido después),
     * `optLong("id")` cae a 0; sin alineación, la entidad restaurada tendría
     * id=0 y `update` apuntaría a la fila equivocada (o a ninguna). El id de la
     * entidad debe seguir siempre la clave.
     */
    @Test
    fun decodeMapEntityIdFollowsKeyWhenEmbeddedIdAbsent() {
        val missingId = """{"7":{"title":"x","createdAt":1700000000000,"updatedAt":1700000000000}}"""

        val decoded = TaskSnapshotCodec.decodeMap(missingId)[7L]!!

        assertEquals(7L, decoded.id)
    }

    @Test
    fun decodeMapEntityIdFollowsKeyWhenEmbeddedIdDiverges() {
        // Defensa en profundidad: aunque hoy el lado de codificación siempre usa
        // task.id como clave, un snapshot cuyo "id" embebido difiera de la clave
        // (edición externa, migración, bug futuro) no debe desalinear la entidad
        // respecto de la clave bajo la que se almacenó.
        val divergent = """{"7":{"id":99,"title":"x","createdAt":1700000000000,"updatedAt":1700000000000}}"""

        val decoded = TaskSnapshotCodec.decodeMap(divergent)[7L]!!

        assertEquals(7L, decoded.id)
    }
}
