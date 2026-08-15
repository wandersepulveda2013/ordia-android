package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus

/**
 * Lógica pura de sincronización de recordatorios de compromisos. Es el espejo
 * de [ReminderSync] para promesas extraídas de conversaciones: un compromiso
 * PENDING con `dueAt` es una promesa agendada y, al vencer, debe avisar al
 * usuario para que la convierta en tarea o la descarte (evita el olvido de una
 * promesa vencida — la cuarta clase de olvido de Ordía, ver [CommitmentRules]).
 *
 * La re-sincronización solo re-encola disparos futuros, idéntico a
 * [ReminderSync.triggers]: los pasados ya fueron atendidos (o se atenderán con
 * retraso) por WorkManager, y re-encolarlos provocaría notificaciones
 * duplicadas. Solo las promesas aún no revisadas (PENDING) se avisan; las
 * CONVERTED/DISMISSED dejaron de ser un olvido.
 */
object CommitmentReminderSync {

    fun triggers(commitments: List<CommitmentEntity>, now: Long): List<Pair<Long, Long>> =
        commitments.asSequence()
            .filter { it.reviewStatus == CommitmentReviewStatus.PENDING }
            .mapNotNull { commitment ->
                val trigger = commitment.dueAt ?: return@mapNotNull null
                if (trigger <= now) null else commitment.id to trigger
            }
            .toList()

    /**
     * Compromisos PENDING cuyo plazo YA venció: promesas olvidadas desde el
     * instante de su detección. Se avisan de inmediato (delay 0) en lugar de
     * quedar invisibles hasta que el usuario abra conversaciones manualmente.
     */
    fun overdueNow(commitments: List<CommitmentEntity>, now: Long): List<Long> =
        commitments.asSequence()
            .filter { it.reviewStatus == CommitmentReviewStatus.PENDING }
            .filter { (it.dueAt ?: Long.MAX_VALUE) <= now }
            .map { it.id }
            .toList()
}
