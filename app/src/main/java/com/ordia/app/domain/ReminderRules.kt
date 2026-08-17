package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity

/**
 * Reglas puras de recordatorios.
 *
 * Posponer (snooze) un recordatorio es un aplazamiento **transitorio** de la
 * notificación, NO un cambio en la preferencia de cuándo debe avisarse la
 * tarea. Por eso [snooze] NO sobrescribe [TaskEntity.reminderAt]:
 *
 * - [reminderAt] codifica la preferencia del usuario (p. ej. "15 min antes del
 *   vencimiento") y, para tareas recurrentes, el offset que [RecurrenceEngine]
 *   reutiliza en TODAS las ocurrencias futuras (`reminderOffset = dueAt - reminderAt`).
 *   Si snooze lo reescribiera con `now + N min`, el offset quedaría corrupto y
 *   cada próxima ocurrencia recordaría a una hora equivocada para siempre.
 * - El aplazamiento se materializa solo en el disparador del worker
 *   ([SnoozeResult.triggerAt]), que el llamador agenda con
 *   `ReminderScheduler.scheduleAt`. WorkManager persiste ese trabajo, así que
 *   el snooze sobrevive a reinicios sin tocar la preferencia original.
 */
object ReminderRules {

    /** Minutos por defecto de un aplazamiento desde una acción de recordatorio. */
    const val DEFAULT_SNOOZE_MINUTES = 10

    /**
     * Offset por defecto del recordatorio respecto al vencimiento (en ms):
     * "30 min antes" cuando el usuario no especificó uno explícito.
     */
    const val DEFAULT_REMINDER_OFFSET_MS = 30L * 60_000L

    /**
     * Antelación mínima (en ms) de un recordatorio por defecto: nunca menos de
     * 1 min antes del vencimiento. Evita avisos sin margen de reacción y, sobre
     * todo, recordatorios en el pasado (ver [defaultReminderAt]).
     */
    const val MIN_REMINDER_LEAD_MS = 60_000L

    /** Resultado de posponer: el disparador a agendar y la tarea a persistir. */
    data class SnoozeResult(val triggerAt: Long, val task: TaskEntity)

    /**
     * Calcula el aplazamiento de un recordatorio sin destruir el offset original.
     *
     * @return [SnoozeResult] con [triggerAt] = `now + minutes` (cuándo volver a
     *   avisar) y [task] = la tarea solo con `updatedAt = now` (reminderAt,
     *   dueAt y startAt se preservan intactos).
     */
    fun snooze(task: TaskEntity, now: Long, minutes: Int = DEFAULT_SNOOZE_MINUTES): SnoozeResult {
        require(minutes >= 0) { "minutes must be non-negative" }
        val triggerAt = now + minutes.toLong() * 60_000L
        return SnoozeResult(triggerAt, task.copy(updatedAt = now))
    }

    /**
     * Resuelve el [TaskEntity.reminderAt] al guardar una tarea desde el editor,
     * preservando el offset personalizado del usuario en lugar de siempre
     * forzar "30 min antes".
     *
     * Antes, el editor recomputaba `reminderAt = dueAt - 30min` en cada guardado.
     * Esto destruía offsets explícitos ("recuérdame 2 horas antes") cuando el
     * usuario editaba un campo NO relacionado (prioridad, proyecto, etiquetas,
     * flagged). Para tareas recurrentes, [RecurrenceEngine] reutiliza el offset
     * (`dueAt - reminderAt`) en TODAS las ocurrencias futuras, así que una
     * edición inocua corrompía el recordatorio de la tarea para siempre.
     *
     * Reglas (con [dueAt] != null y reminder habilitado):
     * - Si [existing] ya tiene recordatorio y el vencimiento NO cambió, se
     *   conserva exactamente el [TaskEntity.reminderAt] previo (offset intacto).
     * - Si [existing] tenía recordatorio y vencimiento y el vencimiento SÍ
     *   cambió, se traslada el offset: `dueAt - (oldDueAt - oldReminderAt)`.
     *   Así "15 min antes" sigue siendo 15 min antes en la nueva hora. Si el
     *   instante trasladado cae en el pasado (vencimiento acercado con un offset
     *   grande), cae a [defaultReminderAt] para no dejar un aviso inútil/pasado
     *   (consistencia con la rama por defecto, que es past-safe).
     * - En cualquier otro caso (nueva tarea, o recordatorio recién activado sin
     *   offset previo) se usa [defaultReminderAt]: "30 min antes" cuando hay
     *   margen, o un aviso intermedio recortado cuando el vencimiento está cerca
     *   (nunca en el pasado; ver [defaultReminderAt]).
     *
     * @param existing tarea previa (null si es nueva).
     * @param reminderEnabled preferencia actual del toggle de recordatorio.
     * @param dueAt vencimiento del guardado (null desactiva el recordatorio).
     * @param now instante de guardado (para evitar recordatorios en el pasado en
     *   el camino por defecto; por defecto [System.currentTimeMillis]).
     * @return timestamp del recordatorio, o null si no procede.
     */
    fun resolveReminderAt(
        existing: TaskEntity?,
        reminderEnabled: Boolean,
        dueAt: Long?,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        if (!reminderEnabled || dueAt == null) return null
        val prevReminder = existing?.reminderAt
        val prevDue = existing?.dueAt
        if (prevReminder != null && prevDue != null) {
            if (prevDue == dueAt) return prevReminder
            // Trasladar el offset ("15 min antes" sigue siendo 15 min antes en la nueva
            // hora). Pero si el vencimiento se acercó con un offset grande, el instante
            // trasladado puede caer en el PASADO: un recordatorio pasado es inútil (se
            // dispara con delay 0 = ruido al guardar, o ReminderSync lo descarta) y la
            // tarea movida perdía silenciosamente su aviso previo. Cae al default
            // adaptativo (nunca pasado) para preservar un aviso útil. Asimétrico con
            // [defaultReminderAt] sólo en que aquí el usuario dejó un offset explícito,
            // así que se conserva cuando aún cabe en el futuro.
            val translated = dueAt - (prevDue - prevReminder)
            return if (translated > now) translated else defaultReminderAt(dueAt, now)
        }
        return defaultReminderAt(dueAt, now)
    }

    /**
     * Resuelve el [TaskEntity.reminderAt] para una tarea capturada con un offset de
     * recordatorio explícito extraído por el parser (p. ej. "recuérdame llamar en
     * 10 min" → offset 30 min por defecto; "avísame 2 horas antes" → 120 min).
     *
     * Antes, la capa de captura calculaba el recordatorio como
     * `dueAt - offsetMin * 60_000` de forma directa. Eso es past-safe sólo cuando
     * el vencimiento está lejos: con un plazo corto (p. ej. vence en 10 min y
     * offset 30 min) el instante cae 20 min EN EL PASADO. [ReminderSync.triggers]
     * descarta los disparos pasados (trigger <= now → null), así que el aviso
     * NUNCA se agendaba y la cita de plazo corto se olvidaba silenciosamente —
     * justo cuando más falta hace para no olvidar.
     *
     * Esta función reúne la misma garantía que [resolveReminderAt]/
     * [defaultReminderAt] (nunca un recordatorio en el pasado): si el offset
     * literal queda estrictamente después de `now`, se conserva íntegro (respeta la
     * preferencia explícita del usuario y el offset que [RecurrenceEngine]
     * reutiliza en ocurrencias futuras); si cae en el pasado o en `now`, cae al
     * default adaptativo [defaultReminderAt] (antelación recortada que sigue
     * precediendo al vencimiento), o `null` cuando ni el mínimo cabe. Fuente
     * única de la regla para las 3 rutas de captura (captura ultrarrápida,
     * captura desde overlay y creación desde interpretación).
     *
     * Determinista, sin random, sin IA fingida.
     *
     * @param dueAt vencimiento de la tarea (no null).
     * @param offsetMinutes minutos de antelación pedidos (>= 1).
     * @param now instante de captura (por defecto [System.currentTimeMillis]).
     * @return timestamp del recordatorio, o null si no cabe antelación útil.
     */
    fun reminderAtFromOffset(dueAt: Long, offsetMinutes: Int, now: Long = System.currentTimeMillis()): Long? {
        require(offsetMinutes >= 0) { "offsetMinutes must be non-negative" }
        val literal = dueAt - offsetMinutes.toLong() * 60_000L
        return if (literal > now) literal else defaultReminderAt(dueAt, now)
    }

    /**
     * Recordatorio por defecto cuando el usuario no dejó un offset explícito (tarea
     * nueva o recordatorio recién activado). Idealmente [DEFAULT_REMINDER_OFFSET_MS]
     * ("30 min antes"). Pero si ese instante YA pasó —vencimiento a menos de 30 min
     * de ahora— un recordatorio en el pasado es inútil: [ReminderScheduler] lo
     * dispara con delay 0, es decir, AVISA AL GUARDAR, sin dar margen real de
     * reacción. Y peor, un plazo corto (p. ej. "llamar al médico en 10 min") se
     * queda SIN aviso previo útil, justo cuando más se necesita para no olvidar.
     *
     * Por eso, cuando "30 min antes" cae en el pasado o no deja margen, se recorta
     * la antelación a la MITAD del tiempo restante (clamped a
     * [MIN_REMINDER_LEAD_MS]), de modo que el usuario reciba un aviso ANTES del
     * vencimiento pero DESPUÉS de ahora. Ejemplos (ahora = 0, due en min):
     * - due en 60 min → 30 min antes (ideal, no se recorta).
     * - due en 10 min → 5 min antes (recortado: avisa a los 5 min, 5 min antes).
     * - due en 2 min  → 1 min antes (mínimo; avisa en 1 min).
     * - due en 30 s   → null (no cabe ni el mínimo: no hay "antes" útil; coherente
     *   con [com.ordia.app.conversations.CommitmentEngine], que también descarta
     *   recordatorios por defecto ya vencidos).
     *
     * Heurística determinista y local, no aleatoria: misma tarea+ahora → mismo
     * recordatorio. No simula IA; es una regla honesta de antelación adaptativa.
     */
    fun defaultReminderAt(dueAt: Long, now: Long): Long? {
        val ideal = dueAt - DEFAULT_REMINDER_OFFSET_MS
        if (ideal > now) return ideal
        // "30 min antes" ya pasó: buscar una antelación útil que aún preceda al
        // vencimiento. lead = mitad del tiempo restante, con piso de 1 min.
        val remaining = dueAt - now
        if (remaining <= MIN_REMINDER_LEAD_MS) return null
        val lead = minOf(DEFAULT_REMINDER_OFFSET_MS, maxOf(MIN_REMINDER_LEAD_MS, remaining / 2))
        val clamped = dueAt - lead
        return if (clamped > now) clamped else null
    }
}
