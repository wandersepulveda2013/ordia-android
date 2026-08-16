package com.ordia.app.context.external

import com.ordia.app.context.ContextIntentKind

/** Entidad local que puede producir una confirmacion contextual externa. */
enum class ContextActionEntityType { TASK, NOTE }

data class ContextTaskDraft(
    val title: String,
    val dueAt: Long?
)

data class ContextNoteDraft(
    val title: String
)

/**
 * Recibo minimo para que un reintento no cree una segunda entidad si el
 * proceso fallo despues de persistir pero antes de retirar la sugerencia.
 */
data class ContextActionReceipt(
    val suggestionId: String,
    val entityType: ContextActionEntityType,
    val entityId: Long,
    val reminderAt: Long?,
    val reminderScheduled: Boolean,
    val createdAt: Long
)

interface ContextActionPersistence {
    suspend fun createTask(draft: ContextTaskDraft): Long
    suspend fun createNote(draft: ContextNoteDraft): Long
    suspend fun delete(entityType: ContextActionEntityType, entityId: Long)
}

fun interface ContextActionReminderScheduler {
    fun schedule(taskId: Long, triggerAt: Long)
}

interface ContextActionReceiptStore {
    fun get(suggestionId: String): ContextActionReceipt?

    /** Devuelve false si el recibo no pudo guardarse de forma durable. */
    fun save(receipt: ContextActionReceipt): Boolean
}

enum class ContextActionRejectionReason {
    EMPTY_TITLE,
    INVALID_DUE_DATE,
    UNSUPPORTED_KIND
}

enum class ContextActionFailureStage {
    NOT_INITIALIZED,
    PERSISTENCE,
    RECEIPT,
    REMINDER,
    RECEIPT_CONFLICT
}

sealed interface ContextActionConfirmationResult {
    data class Success(
        val entityType: ContextActionEntityType,
        val entityId: Long,
        val reminderScheduled: Boolean,
        val reusedPersistedEntity: Boolean
    ) : ContextActionConfirmationResult

    data class Rejected(
        val reason: ContextActionRejectionReason
    ) : ContextActionConfirmationResult

    /**
     * [persistedReceipt] no nulo significa que la entidad ya existe. El
     * llamador debe conservar la sugerencia: el siguiente intento reutilizara
     * ese recibo en lugar de crear un duplicado.
     */
    data class Failure(
        val stage: ContextActionFailureStage,
        val persistedReceipt: ContextActionReceipt? = null
    ) : ContextActionConfirmationResult
}

/**
 * Unico caso de uso para confirmar sugerencias procedentes del IME, overlay o
 * acciones de notificacion. No retira UI ni resuelve ContextEngine: primero
 * garantiza persistencia y, cuando aplica, programacion del recordatorio.
 */
class ConfirmExternalSuggestionUseCase(
    private val persistence: ContextActionPersistence,
    private val reminderScheduler: ContextActionReminderScheduler,
    private val receiptStore: ContextActionReceiptStore,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(suggestion: ExternalSuggestion): ContextActionConfirmationResult {
        val normalizedTitle = suggestion.title
            .trim()
            .filterNot(Char::isISOControl)
            .take(MAX_TITLE_LENGTH)
        if (normalizedTitle.isBlank()) {
            return ContextActionConfirmationResult.Rejected(ContextActionRejectionReason.EMPTY_TITLE)
        }
        if (suggestion.dueAt != null && suggestion.dueAt <= 0L) {
            return ContextActionConfirmationResult.Rejected(ContextActionRejectionReason.INVALID_DUE_DATE)
        }

        val entityType = suggestion.kind.toEntityType()
            ?: return ContextActionConfirmationResult.Rejected(ContextActionRejectionReason.UNSUPPORTED_KIND)
        val reminderAt = suggestion.dueAt.takeIf { entityType == ContextActionEntityType.TASK }

        val existingReceipt = try {
            receiptStore.get(suggestion.id)
        } catch (_: Exception) {
            return ContextActionConfirmationResult.Failure(ContextActionFailureStage.RECEIPT)
        }
        existingReceipt?.let { existing ->
            if (existing.entityType != entityType) {
                return ContextActionConfirmationResult.Failure(
                    stage = ContextActionFailureStage.RECEIPT_CONFLICT,
                    persistedReceipt = existing
                )
            }
            return finishExistingReceipt(existing, existing.reminderAt)
        }

        val entityId = try {
            when (entityType) {
                ContextActionEntityType.TASK -> persistence.createTask(
                    ContextTaskDraft(title = normalizedTitle, dueAt = suggestion.dueAt)
                )
                ContextActionEntityType.NOTE -> persistence.createNote(
                    ContextNoteDraft(title = normalizedTitle)
                )
            }
        } catch (_: Exception) {
            return ContextActionConfirmationResult.Failure(ContextActionFailureStage.PERSISTENCE)
        }
        if (entityId <= 0L) {
            return ContextActionConfirmationResult.Failure(ContextActionFailureStage.PERSISTENCE)
        }

        val initialReceipt = ContextActionReceipt(
            suggestionId = suggestion.id,
            entityType = entityType,
            entityId = entityId,
            reminderAt = reminderAt,
            reminderScheduled = reminderAt == null,
            createdAt = now()
        )
        if (!receiptStore.save(initialReceipt)) {
            val rollbackSucceeded = runCatching { persistence.delete(entityType, entityId) }.isSuccess
            return ContextActionConfirmationResult.Failure(
                stage = ContextActionFailureStage.RECEIPT,
                persistedReceipt = initialReceipt.takeUnless { rollbackSucceeded }
            )
        }

        return finishExistingReceipt(initialReceipt, reminderAt, reused = false)
    }

    private fun finishExistingReceipt(
        receipt: ContextActionReceipt,
        reminderAt: Long?,
        reused: Boolean = true
    ): ContextActionConfirmationResult {
        if (reminderAt != null && !receipt.reminderScheduled) {
            try {
                reminderScheduler.schedule(receipt.entityId, reminderAt)
            } catch (_: Exception) {
                return ContextActionConfirmationResult.Failure(
                    stage = ContextActionFailureStage.REMINDER,
                    persistedReceipt = receipt
                )
            }
            val completedReceipt = receipt.copy(reminderScheduled = true)
            if (!receiptStore.save(completedReceipt)) {
                return ContextActionConfirmationResult.Failure(
                    stage = ContextActionFailureStage.RECEIPT,
                    persistedReceipt = completedReceipt
                )
            }
            return ContextActionConfirmationResult.Success(
                entityType = completedReceipt.entityType,
                entityId = completedReceipt.entityId,
                reminderScheduled = true,
                reusedPersistedEntity = reused
            )
        }

        return ContextActionConfirmationResult.Success(
            entityType = receipt.entityType,
            entityId = receipt.entityId,
            reminderScheduled = reminderAt != null,
            reusedPersistedEntity = reused
        )
    }

    private fun ContextIntentKind.toEntityType(): ContextActionEntityType? = when (this) {
        ContextIntentKind.NOTE -> ContextActionEntityType.NOTE
        ContextIntentKind.UNKNOWN -> null
        else -> ContextActionEntityType.TASK
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 120
    }
}
