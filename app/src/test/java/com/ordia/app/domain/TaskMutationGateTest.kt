package com.ordia.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TaskMutationGate serializa las transiciones de estado de tareas compartidas
 * por la UI (`OrdiaViewModel.toggleTask`/`cancelTask`) y las acciones de la
 * notificación (`ReminderActionReceiver`): todas son read-modify-write +
 * spawn de la próxima ocurrencia recurrente. Sin un mutex compartido, dos
 * toggles concurrentes leerían el mismo estado y duplicarían la ocurrencia
 * (datos sagrados). Estos tests fijan el invariante en JVM pura.
 */
class TaskMutationGateTest {

    @Test fun gateSerializesConcurrentTogglesSoRecurrenceSpawnsExactlyOnce() = runBlocking {
        // Modelo mínimo de toggleTask: leer completado, invertirlo, y sólo en
        // la transición a completado generar la próxima ocurrencia.
        var completed = false
        var spawned = 0
        val workers = (1..2).map {
            launch(Dispatchers.Default) {
                TaskMutationGate.mutex.withLock {
                    val wasCompleted = completed
                    completed = !wasCompleted
                    if (!wasCompleted) spawned++
                }
            }
        }
        workers.forEach { it.join() }
        // Dos toggles serializados: el primero completa (spawn=1), el segundo
        // des-completa (sin spawn). Sin el gate ambos leerían completed=false
        // y spawn serían 2 (ocurrencia duplicada huérfana).
        assertEquals(false, completed)
        assertEquals(1, spawned)
    }

    @Test fun gateRecoversAfterHolderFailure() = runBlocking {
        // Un holder que falla dentro del lock NO debe dejar el mutex tomado
        // (Mutex.withLock libera en finally); el siguiente holder entra.
        val failing = launch(Dispatchers.Default) {
            runCatching {
                TaskMutationGate.mutex.withLock { throw IllegalStateException("boom") }
            }
        }
        failing.join()
        var entered = false
        TaskMutationGate.mutex.withLock { entered = true }
        assertEquals(true, entered)
    }
}
