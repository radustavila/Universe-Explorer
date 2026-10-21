package com.radustavila.nasaexplorer.core.cache

import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvictingCacheCleanerTest {

    private val now = Instant.parse("2026-10-21T10:00:00Z")

    private class Recording(override val name: String, private val failure: Exception? = null) : CacheEvictor {
        val calls = mutableListOf<Instant>()
        override suspend fun evict(now: Instant): Int {
            calls += now
            failure?.let { throw it }
            return 0
        }
    }

    @Test
    fun `runs every evictor as if everything had expired`() = runTest {
        val events = Recording("earth_events")
        val mars = Recording("mars_photos")

        EvictingCacheCleaner(setOf(events, mars), FakeTimeProvider(currentInstant = now)).clear()

        val expected = now.plus(EvictingCacheCleaner.BEYOND_EVERY_RETENTION)
        assertEquals(listOf(expected), events.calls)
        assertEquals(listOf(expected), mars.calls)
        // Still representable in epoch milliseconds, which the DAOs compare against.
        expected.toEpochMilli()
    }

    @Test
    fun `a failing evictor does not stop the others, and the failure is reported`() = runTest {
        val broken = Recording("broken", failure = IllegalStateException("disk I/O"))
        val healthy = Recording("healthy")

        val result = runCatching { EvictingCacheCleaner(setOf(broken, healthy), FakeTimeProvider(currentInstant = now)).clear() }

        assertTrue(result.isFailure)
        assertEquals(1, healthy.calls.size)
    }
}
