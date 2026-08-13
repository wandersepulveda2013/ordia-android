package com.ordia.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TaskMutationGateTest {

    @Test
    fun withLock_serializesSameTaskMutations() = runTest {
        val active = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val taskId = 42L
        val jobs = (1..50).map {
            async(Dispatchers.Default) {
                TaskMutationGate.withLock(taskId) {
                    val current = active.incrementAndGet()
                    maxConcurrent.accumulateAndGet(current) { a, b -> maxOf(a, b) }
                    delay(5)
                    active.decrementAndGet()
                    completed.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()
        assertEquals(50, completed.get())
        assertTrue("Se ejecutaron mutaciones concurrentes: max=$maxConcurrent", maxConcurrent.get() <= 1)
    }

    @Test
    fun withLock_allowsConcurrentDifferentTasks() = runTest {
        val active = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val completed = AtomicInteger(0)
        // Tareas distintas: deben poder ejecutarse concurrentemente.
        val jobs = (1L..20L).map { id ->
            async(Dispatchers.Default) {
                TaskMutationGate.withLock(id) {
                    val current = active.incrementAndGet()
                    maxConcurrent.accumulateAndGet(current) { a, b -> maxOf(a, b) }
                    delay(20)
                    active.decrementAndGet()
                    completed.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()
        assertEquals(20, completed.get())
        assertTrue("Tareas distintas deberían ejecutarse concurrentemente: max=$maxConcurrent", maxConcurrent.get() > 1)
    }
}
