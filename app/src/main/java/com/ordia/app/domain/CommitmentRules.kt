package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus

/**
 * Reglas puras sobre el ciclo de vida de los compromisos extraídos de
 * conversaciones. Un compromiso nace [CommitmentReviewStatus.PENDING] y solo
 * deja de estarlo cuando el usuario lo convierte en tarea o lo descarta.
 *
 * Mientras está PENDING con `dueAt`, es una promesa agendada: si `dueAt` ya
 * pasó y sigue sin revisar, es un **olvido** (una promesa vencida sin
 * cumplir). Esta es la cuarta clase de olvido de Ordía —distinta de las tres
 * que viven en tareas (vencida / hueco incumplido / captura arrinconada)—
 * porque un compromiso no es una tarea hasta que se convierte: no se puede
 * reprogramar, solo convertir o descartar. [isOverduePending] la detecta de
 * forma determinista para que las superficies de recuperación (asistente) no
 * mientan por omisión frente a una promesa que se pasó de plazo.
 */
object CommitmentRules {

    /** Promesa pendiente de revisar cuyo plazo (`dueAt`) ya venció. */
    fun isOverduePending(commitment: CommitmentEntity, now: Long): Boolean =
        commitment.reviewStatus == CommitmentReviewStatus.PENDING &&
            commitment.dueAt != null &&
            commitment.dueAt < now

    /**
     * Promesas vencidas pendientes, de la más atrasada a la menos (por `dueAt`
     * ascendente). Orden determinista y estable: a igual plazo, conserva el
     * orden de entrada (por [createdAt] implícito en la lista).
     */
    fun overduePendingSorted(commitments: List<CommitmentEntity>, now: Long): List<CommitmentEntity> =
        commitments.asSequence()
            .filter { isOverduePending(it, now) }
            .sortedBy { it.dueAt!! }
            .toList()
}
