package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // --- reminderForConvertedTask: past-safe al convertir un compromiso (c.304) ---
    // El cableado (OrdiaViewModel.convertCommitmentToTask) antes hacía
    // `reminderAt = commitment.suggestedReminderAt` a ciegas y luego
    // `reminderScheduler.schedule(task)` cuyo trigger = reminderAt ?: dueAt. Para un
    // compromiso VENCIDO (4º olvido) ambos son pasados → el worker disparaba con
    // delay 0 = notificación espuria al convertir, justo la familia past-safe
    // (c.164/183/187/188/189) que se cerró en TODAS las demás superficies fallaba
    // aquí, en la conversión del propio 4º olvido (c.286-c.303).

    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    private fun commitmentWithReminder(
        id: Long,
        dueAt: Long? = null,
        suggestedReminderAt: Long? = null,
        status: CommitmentReviewStatus = CommitmentReviewStatus.PENDING
    ) = commitment(id, dueAt = dueAt, status = status).copy(suggestedReminderAt = suggestedReminderAt)

    @Test fun reminderForConvertedTask_overdueCommitment_returnsNullNoSpuriousPastTrigger() {
        // 4º olvido vencido: dueAt hace 1 día, aviso sugerido (dueAt-30min) también
        // pasado. La tarea nace vencida; las 5 superficies de recuperación ya la
        // señalan. Armar un aviso pasado = disparar con delay 0 al convertir (ruido).
        val overdue = commitmentWithReminder(
            1,
            dueAt = now - day,
            suggestedReminderAt = now - day - 30 * min
        )
        assertEquals(null, CommitmentRules.reminderForConvertedTask(overdue, now))
    }

    @Test fun reminderForConvertedTask_overdueCommitmentWithNullSuggested_returnsNull() {
        val overdue = commitmentWithReminder(1, dueAt = now - 1, suggestedReminderAt = null)
        assertEquals(null, CommitmentRules.reminderForConvertedTask(overdue, now))
    }

    @Test fun reminderForConvertedTask_futureCommitmentPreservesFutureSuggestedReminder() {
        // Vencimiento futuro + aviso sugerido futuro (offset explícito del usuario):
        // se conserva, igual que el editor preserva el offset (c.183 rama prevDue==dueAt).
        val future = commitmentWithReminder(
            1,
            dueAt = now + day,
            suggestedReminderAt = now + day - 30 * min
        )
        assertEquals(
            now + day - 30 * min,
            CommitmentRules.reminderForConvertedTask(future, now)
        )
    }

    @Test fun reminderForConvertedTask_futureCommitmentWithPastSuggestedFallsToNeverPastDefault() {
        // Compromiso creado hace tiempo cuyo vencimiento aún es futuro pero cuyo aviso
        // sugerido ya pasó: no se conserva un aviso inútil/pasado; cae al default
        // adaptativo nunca-pasado (fuente única ReminderRules.defaultReminderAt),
        // simétrico con la rama "translated pasado" del editor (c.183).
        val future = commitmentWithReminder(
            1,
            dueAt = now + hour,
            suggestedReminderAt = now - hour
        )
        val result = CommitmentRules.reminderForConvertedTask(future, now)
        assertNotNull(result)
        assertTrue("el aviso nunca es pasado", result!! > now)
        assertTrue("el aviso precede al vencimiento futuro", result < now + hour)
    }

    @Test fun reminderForConvertedTask_futureCommitmentNullSuggestedUsesNeverPastDefault() {
        // Sin aviso sugerido: default "30 min antes" (o recortado si el vencimiento está
        // cerca), nunca en el pasado. Mejor que el cableado viejo, que agendaba al
        // `dueAt` mismo (aviso AL vencer, sin margen) y podía caer en el pasado.
        val future = commitmentWithReminder(1, dueAt = now + day, suggestedReminderAt = null)
        val result = CommitmentRules.reminderForConvertedTask(future, now)
        assertEquals(ReminderRules.defaultReminderAt(now + day, now), result)
        assertTrue(result!! in (now + 1) until (now + day))
    }

    @Test fun reminderForConvertedTask_undatedCommitmentPreservesFutureSuggested() {
        // Sin vencimiento (la tarea nace INBOX): un aviso sugerido futuro sigue
        // siendo útil y se conserva.
        val undated = commitmentWithReminder(1, dueAt = null, suggestedReminderAt = now + hour)
        assertEquals(now + hour, CommitmentRules.reminderForConvertedTask(undated, now))
    }

    @Test fun reminderForConvertedTask_undatedCommitmentPastSuggestedReturnsNull() {
        val undated = commitmentWithReminder(1, dueAt = null, suggestedReminderAt = now - hour)
        assertEquals(null, CommitmentRules.reminderForConvertedTask(undated, now))
    }

    @Test fun reminderForConvertedTask_undatedCommitmentNullSuggestedReturnsNull() {
        val undated = commitmentWithReminder(1, dueAt = null, suggestedReminderAt = null)
        assertEquals(null, CommitmentRules.reminderForConvertedTask(undated, now))
    }
}
