package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentReminderSyncTest {

    private fun commitment(
        id: Long,
        reviewStatus: CommitmentReviewStatus = CommitmentReviewStatus.PENDING,
        dueAt: Long? = null
    ) = CommitmentEntity(
        id = id,
        conversationId = 1L,
        kind = CommitmentKind.REMINDER,
        owner = CommitmentOwner.OTHER,
        action = "Compromiso $id",
        dueAt = dueAt,
        confidence = 0.9f,
        fingerprint = "fp-$id",
        reviewStatus = reviewStatus
    )

    @Test
    fun enqueuesPendingWithFutureDueAt() {
        val now = 1_000L
        val dueAt = 5_000L
        val commitments = listOf(commitment(1, dueAt = dueAt))

        val triggers = CommitmentReminderSync.triggers(commitments, now)

        assertEquals(listOf(1L to 5_000L), triggers)
    }

    @Test
    fun ignoresPastTriggersToAvoidDuplicates() {
        val now = 10_000L
        val commitments = listOf(
            commitment(1, dueAt = 5_000L),
            commitment(2, dueAt = 9_999L),
            commitment(3, dueAt = 11_000L)
        )

        val triggers = CommitmentReminderSync.triggers(commitments, now)

        assertEquals(listOf(3L to 11_000L), triggers)
    }

    @Test
    fun ignoresConvertedAndDismissed() {
        val now = 1_000L
        val commitments = listOf(
            commitment(1, CommitmentReviewStatus.CONVERTED, dueAt = 5_000L),
            commitment(2, CommitmentReviewStatus.DISMISSED, dueAt = 5_000L),
            commitment(3, CommitmentReviewStatus.PENDING, dueAt = 5_000L)
        )

        val triggers = CommitmentReminderSync.triggers(commitments, now)

        assertEquals(listOf(3L to 5_000L), triggers)
    }

    @Test
    fun ignoresPendingWithoutDueAt() {
        val commitments = listOf(commitment(1))

        assertTrue(CommitmentReminderSync.triggers(commitments, now = 1_000L).isEmpty())
        assertTrue(CommitmentReminderSync.triggers(emptyList(), now = 1_000L).isEmpty())
    }

    @Test
    fun overdueNowReturnsPendingWhoseDueAtHasPassed() {
        val now = 10_000L
        val commitments = listOf(
            commitment(1, dueAt = 5_000L),
            commitment(2, dueAt = 10_000L),
            commitment(3, dueAt = 11_000L),
            commitment(4, dueAt = null)
        )

        val overdue = CommitmentReminderSync.overdueNow(commitments, now)

        assertEquals(listOf(1L, 2L), overdue)
    }

    @Test
    fun overdueNowIgnoresConvertedAndDismissedAndNullDueAt() {
        val now = 10_000L
        val commitments = listOf(
            commitment(1, CommitmentReviewStatus.CONVERTED, dueAt = 5_000L),
            commitment(2, CommitmentReviewStatus.DISMISSED, dueAt = 5_000L),
            commitment(3, dueAt = null),
            commitment(4, dueAt = 5_000L)
        )

        val overdue = CommitmentReminderSync.overdueNow(commitments, now)

        assertEquals(listOf(4L), overdue)
    }
}
