package com.ordia.app.data.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica la recolección del subárbol que usan TaskDao.delete y
 * deleteSubtreeAndSelf: sin esto, borrar una tarea padre dejaría
 * subtareas huérfanas.
 */
class TaskTreeTest {

    private val children: Map<Long, List<Long>> = mapOf(
        1L to listOf(2L, 3L),
        2L to listOf(4L),
        3L to emptyList(),
        4L to listOf(5L),
        5L to emptyList()
    )

    @Test
    fun collectIds_includesAllDescendantsTransitively() = runTest {
        val ids = TaskTree.collectIds(1L) { children[it].orEmpty() }

        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), ids.toSet())
    }

    @Test
    fun collectIds_returnsRootWhenNoChildren() = runTest {
        val ids = TaskTree.collectIds(5L) { children[it].orEmpty() }

        assertEquals(listOf(5L), ids)
    }

    @Test
    fun collectIds_doesNotDuplicateCycles() = runTest {
        val cyclic = mapOf(1L to listOf(2L), 2L to listOf(1L, 3L), 3L to emptyList())

        val ids = TaskTree.collectIds(1L) { cyclic[it].orEmpty() }

        assertEquals(setOf(1L, 2L, 3L), ids.toSet())
    }
}
