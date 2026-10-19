package com.radustavila.nasaexplorer.core.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheEvictionWorkerTest {

    private val clock = FakeTimeProvider()

    @Test
    fun `runs every evictor with the current time and reports what each removed`() = runTest {
        val apod = RecordingEvictor("apod", removes = 12)
        val epic = RecordingEvictor("epic", removes = 0)

        val result = buildWorker(setOf(apod, epic)).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(clock.currentInstant), apod.calls)
        assertEquals(listOf(clock.currentInstant), epic.calls)
        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(12, output.getInt("evicted_apod", -1))
        assertEquals(0, output.getInt("evicted_epic", -1))
    }

    @Test
    fun `a failing evictor does not stop the others and fails the run`() = runTest {
        val broken = RecordingEvictor("broken", failure = IllegalStateException("disk I/O"))
        val healthy = RecordingEvictor("healthy", removes = 3)

        val result = buildWorker(setOf(broken, healthy)).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(1, healthy.calls.size)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertArrayEquals(arrayOf("broken"), output.getStringArray(CacheEvictionWorker.KEY_FAILED))
        assertEquals(3, output.getInt("evicted_healthy", -1))
    }

    @Test
    fun `with nothing registered the run is a successful no-op`() = runTest {
        val result = buildWorker(emptySet()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    private fun buildWorker(evictors: Set<CacheEvictor>): CacheEvictionWorker =
        TestListenableWorkerBuilder<CacheEvictionWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = CacheEvictionWorker(appContext, workerParameters, evictors, clock)
                },
            )
            .build()

    private class RecordingEvictor(
        override val name: String,
        private val removes: Int = 0,
        private val failure: Exception? = null,
    ) : CacheEvictor {
        val calls = mutableListOf<Instant>()

        override suspend fun evict(now: Instant): Int {
            calls += now
            failure?.let { throw it }
            return removes
        }
    }
}
