package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLoadMonitorTest {

    private val day1 = TileLoadMonitor.watch(
        listOf("https://gibs/SNPP/default/2026-10-20/", "https://gibs/NOAA20/default/2026-10-20/"),
    )
    private val day2 = TileLoadMonitor.watch(listOf("https://gibs/SNPP/default/2026-10-21/"))

    @After
    fun tearDown() {
        TileLoadMonitor.unwatch(day1)
        TileLoadMonitor.unwatch(day2)
    }

    @Test
    fun `outcomes reach only the tallies watching that URL`() {
        TileLoadMonitor.report("https://gibs/NOAA20/default/2026-10-20/set/1/0/0.jpg", TileLoadStatus.Loaded)
        TileLoadMonitor.report("https://gibs/SNPP/default/2026-10-21/set/1/0/0.jpg", TileLoadStatus.ImageryUnavailable)

        assertEquals(1, day1.loaded)
        assertEquals(0, day1.failed)
        assertEquals(TileLoadStatus.Loaded, day1.status.value)
        assertEquals(1, day2.failed)
        assertEquals(TileLoadStatus.ImageryUnavailable, day2.status.value)
    }

    @Test
    fun `imagery replaces what is on screen if anything loaded or nothing failed`() {
        assertTrue(day1.canReplaceShown)

        day1.record(TileLoadStatus.NetworkError)
        assertFalse(day1.canReplaceShown)

        day1.record(TileLoadStatus.Loaded)
        assertTrue(day1.canReplaceShown)
    }

    @Test
    fun `an unwatched tally keeps what it counted`() {
        TileLoadMonitor.report("https://gibs/SNPP/default/2026-10-21/set/1/0/0.jpg", TileLoadStatus.ImageryUnavailable)
        TileLoadMonitor.unwatch(day2)

        TileLoadMonitor.report("https://gibs/SNPP/default/2026-10-21/set/1/0/1.jpg", TileLoadStatus.Loaded)

        assertEquals(0, day2.loaded)
        assertEquals(TileLoadStatus.ImageryUnavailable, day2.status.value)
    }
}
