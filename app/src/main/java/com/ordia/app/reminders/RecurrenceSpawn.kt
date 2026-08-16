package com.ordia.app.reminders

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.repository.TagRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.domain.RecurrenceEngine
import com.ordia.app.domain.SubtaskRules
import kotlinx.coroutines.flow.first

/**
 * Genera la próxima ocurrencia de una tarea recurrente recién completada y
 * reconstruye su desglose (subtareas + etiquetas). Fuente única de verdad para
 * TODOS los caminos de completado: `toggleTask` y `completeParentAutomatically`
 * en la app (`OrdiaViewModel`) y `ACTION_COMPLETE` / `completeParentIfDone` en
 * la notificación (`ReminderActionReceiver`).
 *
 * Sin esta centralización, el camino de la notificación olvidaba clonar el
 * checklist: completar una recurrente CON subtareas desde el botón de acción
 * del recordatorio generaba la próxima ocurrencia SIN su desglose (y sin sus
 * etiquetas), perdiéndolo ciclo a ciclo —asimetría flagrante con completar en
 * la app, que sí lo clonaba (c.223/c.236). Una sola implementación cierra la
 * rendija para siempre: lo que antes eran dos copias que podían diverger (y de
 * hecho divergieron) es ahora un único orquestador. "Menos copias, más
 * coherencia", sin nueva pantalla ni botón.
 *
 * Compone las reglas PURAS y JVM-verificadas [RecurrenceEngine.nextOccurrence],
 * [SubtaskRules.cloneForNextOccurrence] y [SubtaskRules.relinkedSubtaskTags];
 * este orquestador (repositorios + scheduler) es cableado Android y queda
 * **NO VERIFICADO** en JVM (sin Room/Android SDK), pero su lógica es la suma de
 * reglas ya verificadas, idéntica a la que la app usaba antes.
 *
 * @param original tarea recurrente tal como estaba ANTES de marcarse completada
 *  (con su `recurrence`/`dueAt`/`recurrenceDays` vigentes y `completed=false`).
 * @param now instante de completado; fija el `createdAt` de la nueva ocurrencia
 *  y el punto desde el que [RecurrenceEngine.nextOccurrence] avanza al futuro.
 */
suspend fun spawnNextOccurrence(
    original: TaskEntity,
    now: Long,
    taskRepository: TaskRepository,
    tagRepository: TagRepository,
    reminderScheduler: ReminderScheduler,
) {
    val next = RecurrenceEngine.nextOccurrence(original, now) ?: return
    val nextId = taskRepository.add(next)
    reminderScheduler.schedule(next.copy(id = nextId))
    // Se lee UNA vez y se reusa para el padre y los pasos: enlaces vigentes
    // (snapshot de la DB) de la ocurrencia recién completada.
    val taskTags = tagRepository.links.first()
    // Las etiquetas del PADRE recurrente renacen en la próxima ocurrencia
    // (simétrico a `duplicateTask`, que sí las copiaba vía `tagsForTask`):
    // sin esto, una recurrente con etiquetas ("#trabajo Reunión semanal") las
    // perdía ciclo a ciclo al completarla — su categorización desaparecía de
    // la nueva ocurrencia aunque el checklist renaciera. Regla pura
    // [SubtaskRules.tagIdsForTask] (verificada en JVM); el re-enlace es
    // cableado Android (NO VERIFICADO en JVM).
    SubtaskRules.tagIdsForTask(original.id, taskTags)
        .forEach { tagRepository.link(nextId, it) }
    // El desglose del padre recurrente renace abierto en la próxima ocurrencia
    // (c.223), y con él sus etiquetas categoriales (c.236): sin esto, el
    // checklist se perdía ciclo a ciclo al completar desde la notificación.
    val subs = taskRepository.subtasks(original.id)
    if (subs.isEmpty()) return
    val ids = taskRepository.addAll(SubtaskRules.cloneForNextOccurrence(subs, nextId, now))
    SubtaskRules.relinkedSubtaskTags(subs, ids, taskTags)
        .forEach { tagRepository.link(it.taskId, it.tagId) }
}
