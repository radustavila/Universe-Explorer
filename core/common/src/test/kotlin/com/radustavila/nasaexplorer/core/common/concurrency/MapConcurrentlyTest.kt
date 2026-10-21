package com.radustavila.nasaexplorer.core.common.concurrency

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MapConcurrentlyTest {

    @Test
    fun `runs at most the given number at once and keeps the order`() = runTest {
        var running = 0
        var mostAtOnce = 0

        val result = (1..10).mapConcurrently(maxConcurrent = 4) { n ->
            running++
            mostAtOnce = maxOf(mostAtOnce, running)
            delay(100L * (11 - n))
            running--
            n * 2
        }

        assertEquals((1..10).map { it * 2 }, result)
        assertEquals(4, mostAtOnce)
    }

    @Test
    fun `ten one-second calls four at a time take three seconds, not ten`() = runTest {
        (1..10).mapConcurrently(maxConcurrent = 4) { delay(1_000) }

        assertEquals(3_000, testScheduler.currentTime)
    }
}
