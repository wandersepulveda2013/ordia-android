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

    /**
     * Recordatorio past-safe para la tarea creada al convertir un compromiso
     * ([com.ordia.app.ui.OrdiaViewModel.convertCommitmentToTask]).
     *
     * El cableado viejo hacía `reminderAt = commitment.suggestedReminderAt` a
     * ciegas y luego `reminderScheduler.schedule(task)`, cuyo disparador es
     * `reminderAt ?: dueAt`. Para un compromiso **vencido** (el 4.º olvido de
     * Ordía) ambos son pasados → [ReminderScheduler] agenda con `delay 0` y la
     * notificación disparaba AL CONVERTIR, sin margen y sin sentido: la tarea
     * nace vencida. Esa familia past-safe se cerró en TODAS las demás
     * superficies (c.164 default, c.183 editor, c.187/c.188 automatización,
     * c.189 recurrencia) MENOS aquí, en la conversión del propio 4.º olvido
     * (c.286-c.303 endurecieron 5 superficies de recuperación; ésta faltaba).
     *
     * Reglas (deterministas, sin IA, fuente única [ReminderRules.defaultReminderAt]):
     * - `dueAt` pasado o == `now` → `null`. La tarea nace vencida; las 5
     *   superficies de recuperación (asistente, nudge, insight, resumen,
     *   planificador) ya la señalan. Armar un aviso pasado es ruido (se dispara
     *   al instante) y `ReminderSync.triggers` lo descartaría de todos modos.
     * - `dueAt` futuro + `suggestedReminderAt` futuro Y anterior al vencimiento → se
     *   conserva (respeta el offset explícito que extrajo el parser de la conversación,
     *   igual que el editor conserva el offset cuando el vencimiento no cambia, c.183).
     *   Si el aviso sugerido es posterior al vencimiento (offset mal inferido o fecha
     *   acortada), se descarta: un recordatorio después del plazo es absurdo y rompe el
     *   invariante `reminder < dueAt` de [ReminderRules], cayendo al default nunca-pasado.
     * - `dueAt` futuro + `suggestedReminderAt` pasado o ausente →
     *   [ReminderRules.defaultReminderAt] ("30 min antes" o recortado nunca-pasado):
     *   no se conserva un aviso inútil/pasado; simétrico con la rama "translated
     *   pasado" del editor. Mejora además al cableado viejo, que sin aviso
     *   sugerido agendaba al `dueAt` mismo (aviso AL vencer, sin margen).
     * - Sin `dueAt` (la tarea nace INBOX) + `suggestedReminderAt` futuro → se
     *   conserva (un aviso sin vencimiento pero con hora concreta sigue siendo
     *   útil). Pasado o ausente → `null`.
     *
     * Regla pura; el llamador persiste y agenda. No muta [commitment].
     */
    fun reminderForConvertedTask(commitment: CommitmentEntity, now: Long): Long? {
        val dueAt = commitment.dueAt
        val suggested = commitment.suggestedReminderAt
        if (dueAt == null) {
            // Sin vencimiento: un aviso sugerido futuro es útil; pasado o ausente, no.
            return if (suggested != null && suggested > now) suggested else null
        }
        // Vencido (o justo ahora): la tarea nace atrasada, no se arma un aviso pasado.
        if (dueAt <= now) return null
        // Vencimiento futuro: conserva el offset del usuario si es futuro Y ANTERIOR al
        // vencimiento. Un recordatorio posterior al plazo es absurdo (avisa después de
        // vencida) y rompe el contrato de [ReminderRules], donde reminder < dueAt es un
        // invariante (ver [defaultReminderAt]/[ReminderRules.resolveReminderAt], ambas
        // garantizan reminder < dueAt). El offset sugerido lo extrae el parser de la
        // conversación y puede quedar >= dueAt por una fecha acortada o un offset mal
        // inferido; aquí se sanea igual que la rama "translated pasado" del editor cae al
        // default nunca-pasado. Conserva la paridad past-safe de c.286-c.303.
        if (suggested != null && suggested > now && suggested < dueAt) return suggested
        // Sin offset útil (ausente, pasado, o posterior al vencimiento): default
        // adaptativo nunca-pasado y siempre anterior al vencimiento (fuente única).
        return ReminderRules.defaultReminderAt(dueAt, now)
    }
}
