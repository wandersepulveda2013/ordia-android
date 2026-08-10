package com.ordia.app.context.external

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextIntentKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmExternalSuggestionUseCaseTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `una tarea se persiste y programa antes de informar exito`() = runTest {
        val persistence = FakePersistence()
        val reminders = FakeReminders()
        val receipts = FakeReceipts()
        val useCase = useCase(persistence, reminders, receipts)

        val result = useCase(suggestion(dueAt = now + 60_000L))

        assertTrue(result is ContextActionConfirmationResult.Success)
        result as ContextActionConfirmationResult.Success
        assertEquals(ContextActionEntityType.TASK, result.entityType)
        assertEquals(41L, result.entityId)
        assertTrue(result.reminderScheduled)
        assertEquals(listOf(ContextTaskDraft("Comprar leche", now + 60_000L)), persistence.tasks)
        assertEquals(listOf(41L to (now + 60_000L)), reminders.scheduled)
        assertTrue(receipts.values.getValue("suggestion-1").reminderScheduled)
    }

    @Test
    fun `una nota real no crea tarea ni recordatorio`() = runTest {
        val persistence = FakePersistence()
        val reminders = FakeReminders()
        val useCase = useCase(persistence, reminders, FakeReceipts())

        val result = useCase(suggestion(kind = ContextIntentKind.NOTE, title = "Idea del proyecto"))

        assertTrue(result is ContextActionConfirmationResult.Success)
        assertEquals(listOf(ContextNoteDraft("Idea del proyecto")), persistence.notes)
        assertTrue(persistence.tasks.isEmpty())
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test
    fun `tipo desconocido se rechaza sin tocar persistencia`() = runTest {
        val persistence = FakePersistence()
        val result = useCase(persistence)(suggestion(kind = ContextIntentKind.UNKNOWN))

        assertEquals(
            ContextActionConfirmationResult.Rejected(ContextActionRejectionReason.UNSUPPORTED_KIND),
            result
        )
        assertTrue(persistence.tasks.isEmpty())
        assertTrue(persistence.notes.isEmpty())
    }

    @Test
    fun `titulo se normaliza y limita antes de persistir`() = runTest {
        val persistence = FakePersistence()
        val longTitle = "  Comprar\u0000 " + "x".repeat(150)

        val result = useCase(persistence)(suggestion(title = longTitle))

        assertTrue(result is ContextActionConfirmationResult.Success)
        val stored = persistence.tasks.single().title
        assertFalse(stored.any(Char::isISOControl))
        assertEquals(120, stored.length)
        assertEquals("Comprar ", stored.take(8))
    }

    @Test
    fun `fallo de recordatorio conserva recibo y reintento no duplica tarea`() = runTest {
        val persistence = FakePersistence()
        val reminders = FakeReminders(fail = true)
        val receipts = FakeReceipts()
        val useCase = useCase(persistence, reminders, receipts)
        val suggestion = suggestion(dueAt = now + 60_000L)

        val first = useCase(suggestion)

        assertTrue(first is ContextActionConfirmationResult.Failure)
        first as ContextActionConfirmationResult.Failure
        assertEquals(ContextActionFailureStage.REMINDER, first.stage)
        assertNotNull(first.persistedReceipt)
        assertEquals(1, persistence.tasks.size)

        reminders.fail = false
        val second = useCase(suggestion)

        assertTrue(second is ContextActionConfirmationResult.Success)
        second as ContextActionConfirmationResult.Success
        assertTrue(second.reusedPersistedEntity)
        assertEquals(1, persistence.tasks.size)
        assertEquals(listOf(41L to (now + 60_000L)), reminders.scheduled)
    }

    @Test
    fun `fallo al guardar recibo compensa la entidad creada`() = runTest {
        val persistence = FakePersistence()
        val receipts = FakeReceipts(failSaves = 1)

        val result = useCase(persistence, receipts = receipts)(suggestion())

        assertTrue(result is ContextActionConfirmationResult.Failure)
        result as ContextActionConfirmationResult.Failure
        assertEquals(ContextActionFailureStage.RECEIPT, result.stage)
        assertEquals(listOf(ContextActionEntityType.TASK to 41L), persistence.deleted)
        assertEquals(null, result.persistedReceipt)
    }

    @Test
    fun `recibo incompatible se reporta sin crear otra entidad`() = runTest {
        val persistence = FakePersistence()
        val receipts = FakeReceipts().apply {
            values["suggestion-1"] = ContextActionReceipt(
                suggestionId = "suggestion-1",
                entityType = ContextActionEntityType.NOTE,
                entityId = 9L,
                reminderAt = null,
                reminderScheduled = true,
                createdAt = now
            )
        }

        val result = useCase(persistence, receipts = receipts)(suggestion())

        assertTrue(result is ContextActionConfirmationResult.Failure)
        assertEquals(ContextActionFailureStage.RECEIPT_CONFLICT, (result as ContextActionConfirmationResult.Failure).stage)
        assertTrue(persistence.tasks.isEmpty())
    }

    @Test
    fun `fecha invalida se rechaza explicitamente`() = runTest {
        val result = useCase(FakePersistence())(suggestion(dueAt = -1L))

        assertEquals(
            ContextActionConfirmationResult.Rejected(ContextActionRejectionReason.INVALID_DUE_DATE),
            result
        )
    }

    private fun useCase(
        persistence: FakePersistence,
        reminders: FakeReminders = FakeReminders(),
        receipts: FakeReceipts = FakeReceipts()
    ) = ConfirmExternalSuggestionUseCase(
        persistence = persistence,
        reminderScheduler = reminders,
        receiptStore = receipts,
        now = { now }
    )

    private fun suggestion(
        kind: ContextIntentKind = ContextIntentKind.TASK,
        title: String = "Comprar leche",
        dueAt: Long? = null
    ) = ExternalSuggestion(
        id = "suggestion-1",
        confirmationId = "confirmation-1",
        kind = kind,
        title = title,
        dueAt = dueAt,
        source = ContextCaptureSource.KEYBOARD,
        confidence = 0.9f,
        createdAt = now,
        expiresAt = now + 300_000L
    )

    private class FakePersistence : ContextActionPersistence {
        val tasks = mutableListOf<ContextTaskDraft>()
        val notes = mutableListOf<ContextNoteDraft>()
        val deleted = mutableListOf<Pair<ContextActionEntityType, Long>>()
        var failCreate = false

        override suspend fun createTask(draft: ContextTaskDraft): Long {
            if (failCreate) error("fallo")
            tasks += draft
            return 41L
        }

        override suspend fun createNote(draft: ContextNoteDraft): Long {
            if (failCreate) error("fallo")
            notes += draft
            return 42L
        }

        override suspend fun delete(entityType: ContextActionEntityType, entityId: Long) {
            deleted += entityType to entityId
        }
    }

    private class FakeReminders(var fail: Boolean = false) : ContextActionReminderScheduler {
        val scheduled = mutableListOf<Pair<Long, Long>>()

        override fun schedule(taskId: Long, triggerAt: Long) {
            if (fail) error("fallo")
            scheduled += taskId to triggerAt
        }
    }

    private class FakeReceipts(var failSaves: Int = 0) : ContextActionReceiptStore {
        val values = mutableMapOf<String, ContextActionReceipt>()

        override fun get(suggestionId: String): ContextActionReceipt? = values[suggestionId]

        override fun save(receipt: ContextActionReceipt): Boolean {
            if (failSaves > 0) {
                failSaves--
                return false
            }
            values[receipt.suggestionId] = receipt
            return true
        }
    }
}
