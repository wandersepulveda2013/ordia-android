package com.ordia.app.data.local

import com.ordia.app.data.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis

class MockTaskTagDao : TaskTagDao {
    override fun observeAll(): Flow<List<TaskTagCrossRef>> = emptyFlow()
    override suspend fun getAllNow(): List<TaskTagCrossRef> = emptyList()
    override suspend fun add(ref: TaskTagCrossRef) {
        // Simulate DB insert overhead
        Thread.sleep(1)
    }
    override suspend fun insertAll(refs: List<TaskTagCrossRef>) {
        // Simulate DB batch insert overhead
        Thread.sleep(1 + (refs.size * 0.1).toLong())
    }
    override suspend fun remove(taskId: Long, tagId: Long) {}
    override suspend fun deleteAll() {}
}

class MockTagDao : TagDao {
    override fun observeAll(): Flow<List<TagEntity>> = emptyFlow()
    override suspend fun getAllNow(): List<TagEntity> = emptyList()
    override suspend fun insert(tag: TagEntity): Long = 0L
    override suspend fun insertAll(tags: List<TagEntity>): List<Long> = emptyList()
    override suspend fun delete(tag: TagEntity) {}
    override suspend fun deleteAll() {}
}

class TagRepositoryPerfTest {
    @Test
    fun testNPlus1QueryPerformance() = runBlocking {
        val repo = TagRepository(MockTagDao(), MockTaskTagDao())

        // Generate test data
        val tagIds = (1L..100L).toList()
        val taskId = 1L

        // Measure baseline (N+1 query)
        val timeBaseline = measureTimeMillis {
            tagIds.forEach { tagId -> repo.link(taskId, tagId) }
        }

        println("Baseline (N+1 queries for 100 tags): $timeBaseline ms")

        // Measure optimized (batch insert)
        val timeOptimized = measureTimeMillis {
            if (tagIds.isNotEmpty()) repo.linkAll(taskId, tagIds)
        }

        println("Optimized (batch insert for 100 tags): $timeOptimized ms")
    }
}
