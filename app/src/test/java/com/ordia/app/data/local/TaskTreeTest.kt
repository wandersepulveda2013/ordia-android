package com.ordia.app.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de `TaskTree.collectIds` (ORD-025):
 * - Incluye la raíz y todos los descendientes transitivos.
 * - Mantiene orden BFS (padre antes que hijos).
 * - No repite ids y tolera ciclos (datos corruptos) sin colgarse.
 */
class TaskTreeTest {

    private val tree: Map<Long, List<Long>> = mapOf(
        1L to listOf(2L, 3L),
        2L to listOf(4L),
        3L to listOf(5L, 6L),
        4L to emptyList(),
        5L to emptyList(),
        6L to emptyList()
    )

    private fun collect(root: Long, graph: Map<Long, List<Long>>): List<Long> =
        runBlocking { TaskTree.collectIds(root) { graph[it] ?: emptyList() } }

    @Test
    fun leaf_onlyContainsItself() {
        val ids = collect(4L, tree)
        assertEquals(listOf(4L), ids)
    }

    @Test
    fun root_collectsWholeSubtree() {
        val ids = collect(1L, tree)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), ids)
    }

    @Test
    fun innerNode_collectsItsOwnSubtree() {
        val ids = collect(2L, tree)
        assertEquals(listOf(2L, 4L), ids)
    }

    @Test
    fun missingParentData_returnsJustRoot() {
        val ids = collect(99L, tree)
        assertEquals(listOf(99L), ids)
    }

    @Test
    fun cycle_doesNotHangAndHasNoDuplicates() {
        // 7 -> 8 -> 7 (dato corrupto): no debe colgarse ni repetir.
        val cyclic = mapOf(7L to listOf(8L), 8L to listOf(7L))
        val ids = collect(7L, cyclic)
        assertEquals(listOf(7L, 8L), ids)
    }

    @Test
    fun sharedChild_isOnlyListedOnce() {
        // 10 y 11 apuntan al mismo hijo 12.
        val graph = mapOf(10L to listOf(11L, 12L), 11L to listOf(12L), 12L to emptyList())
        val ids = collect(10L, graph)
        assertEquals(listOf(10L, 11L, 12L), ids)
        assertFalse(ids.toSet().size != ids.size)
    }

    @Test
    fun deepChain_includesEveryLevel() {
        val chain = (20L..29L).associateWith { listOf(it + 1) } + (30L to emptyList())
        val ids = collect(20L, chain)
        assertEquals((20L..30L).toList(), ids)
        assertTrue(ids.size == 11)
    }
}
