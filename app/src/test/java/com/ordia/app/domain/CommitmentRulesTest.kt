package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentRulesTest {
    private val now = 1_000_000_000_000L

    private fun commitment(
        id: Long,
        dueAt: Long? = null,
        status: CommitmentReviewStatus = CommitmentReviewStatus.PENDING,
        createdAt: Long = now
    ) = CommitmentEntity(
        id = id,
        conversationId = 1,
        kind = CommitmentKind.SELF_COMMITMENT,
        owner = CommitmentOwner.SELF,
        actor = "yo",
        action = "te llamo",
        dueAt = dueAt,
        confidence = 0.8f,
        reviewStatus = status,
        fingerprint = "fp$id",
        createdAt = createdAt
    )

    @Test fun isOverduePending_trueWhenPendingAndDueBeforeNow() {
        assertTrue(CommitmentRules.isOverduePending(commitment(1, dueAt = now - 1), now))
        assertTrue(CommitmentRules.isOverduePending(commitment(1, dueAt = now - 86_400_000L), now))
    }

    @Test fun isOverduePending_falseWhenDueAtIsNull() {
        // Sin fecha no hay plazo que vencer: es una promesa sin fecha, no un olvido vencido.
        assertFalse(CommitmentRules.isOverduePending(commitment(1, dueAt = null), now))
    }

    @Test fun isOverduePending_falseWhenDueAtIsFuture() {
        assertFalse(CommitmentRules.isOverduePending(commitment(1, dueAt = now + 1), now))
        assertFalse(CommitmentRules.isOverduePending(commitment(1, dueAt = now + 86_400_000L), now))
    }

    @Test fun isOverduePending_falseWhenNotPending() {
        // Ya convertida en tarea o descartada: el usuario la revisó, no es un olvido.
        assertFalse(CommitmentRules.isOverduePending(commitment(1, dueAt = now - 1, status = CommitmentReviewStatus.CONVERTED), now))
        assertFalse(CommitmentRules.isOverduePending(commitment(1, dueAt = now - 1, status = CommitmentReviewStatus.DISMISSED), now))
    }

    @Test fun isOverduePending_falseWhenDueExactlyNow() {
        // dueAt == now aún no venció (estrictamente menor). Límite determinista.
        assertFalse(CommitmentRules.isOverduePending(commitment(1, dueAt = now), now))
    }

    @Test fun overduePendingSorted_filtersAndOrdersMostOverdueFirst() {
        val a = commitment(1, dueAt = now - 86_400_000L) // vencida hace 1 día
        val b = commitment(2, dueAt = now - 3 * 86_400_000L) // vencida hace 3 días (más atrasada)
        val c = commitment(3, dueAt = now + 86_400_000L) // futura, fuera
        val d = commitment(4, dueAt = null) // sin fecha, fuera
        val e = commitment(5, dueAt = now - 1, status = CommitmentReviewStatus.DISMISSED) // revisada, fuera
        val result = CommitmentRules.overduePendingSorted(listOf(a, b, c, d, e), now)
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test fun overduePendingSorted_emptyWhenNoneOverdue() {
        assertEquals(
            emptyList<CommitmentEntity>(),
            CommitmentRules.overduePendingSorted(
                listOf(
                    commitment(1, dueAt = now + 1),
                    commitment(2, dueAt = null),
                    commitment(3, dueAt = now - 1, status = CommitmentReviewStatus.CONVERTED)
                ),
                now
            )
        )
    }

    @Test fun overduePendingSorted_preservesInputOrderForSameDueAt() {
        // A igual plazo, orden estable (de entrada), no arbitrario.
        val first = commitment(1, dueAt = now - 1000, createdAt = now - 2000)
        val second = commitment(2, dueAt = now - 1000, createdAt = now - 1000)
        val result = CommitmentRules.overduePendingSorted(listOf(second, first), now)
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }
}
