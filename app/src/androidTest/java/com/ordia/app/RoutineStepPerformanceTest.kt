package com.ordia.app

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class RoutineStepPerformanceTest {
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
    fun benchmarkDeleteSteps() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(name = "Perf Routine", createdAt = 0L, updatedAt = 0L))
        val stepDao = database.routineStepDao()

        val steps = (1..100).map { RoutineStepEntity(routineId = routineId, title = "Step $it", position = it) }
        stepDao.insertAll(steps)
        val insertedSteps = stepDao.getByRoutine(routineId)

        val timeOneByOne = measureTimeMillis {
            insertedSteps.forEach { stepDao.delete(it) }
        }
        println("BENCHMARK_RESULT_BASELINE: $timeOneByOne ms")
    }
}
