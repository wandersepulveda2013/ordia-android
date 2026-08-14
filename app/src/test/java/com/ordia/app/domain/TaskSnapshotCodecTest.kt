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

        val decoded = TaskSnapshotCodec.decodeMap(TaskSnapshotCodec.encodeMap(mapOf(1L to plain)))[1L]

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
}
