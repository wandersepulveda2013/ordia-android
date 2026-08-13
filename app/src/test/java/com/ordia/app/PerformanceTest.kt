package com.ordia.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.local.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PerformanceTest {
    private lateinit var database: OrdiaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrdiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun benchmarkRoutineInsertion() {
        runBlocking {
        val count = 100
        val now = System.currentTimeMillis()
        val dao = database.taskDao()

        // 1. One by one insertion
        val timeOneByOne = measureTimeMillis {
            for (i in 0 until count) {
                dao.insert(
                    TaskEntity(
                        title = "Step $i",
                        details = "Rutina: Benchmark",
                        durationMinutes = 5,
                        status = com.ordia.app.data.local.TaskStatus.INBOX,
                        sortOrder = i,
                        createdAt = now + i,
                        updatedAt = now + i
                    )
                )
            }
        }

        database.clearAllTables()

        // 2. Batch insertion
        val timeBatch = measureTimeMillis {
            val tasks = (0 until count).map { i ->
                TaskEntity(
                    title = "Step $i",
                    details = "Rutina: Benchmark",
                    durationMinutes = 5,
                    status = com.ordia.app.data.local.TaskStatus.INBOX,
                    sortOrder = i,
                    createdAt = now + i,
                    updatedAt = now + i
                )
            }
            dao.insertAll(tasks)
        }

        println("BENCHMARK RESULT: One by one took $timeOneByOne ms, batch took $timeBatch ms")
        }
    }
}
