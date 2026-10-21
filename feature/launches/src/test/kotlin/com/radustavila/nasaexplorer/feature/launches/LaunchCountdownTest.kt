package com.radustavila.nasaexplorer.feature.launches

import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchCountdownTest {

    private val now = Instant.parse("2026-10-19T10:00:00Z")

    @Test
    fun `counts down inside a day as hours minutes and seconds`() {
        assertEquals(Countdown(afterLiftoff = false, days = 0, clock = "02:03:04"), countdown(net = now + Duration.parse("PT2H3M4S"), now = now))
    }

    @Test
    fun `adds whole days for launches further away`() {
        assertEquals(Countdown(afterLiftoff = false, days = 3, clock = "00:00:05"), countdown(net = now + Duration.parse("P3DT5S"), now = now))
    }

    @Test
    fun `counts up after lift-off`() {
        val countdown = countdown(net = now - Duration.parse("PT12M30S"), now = now)
        assertEquals(Countdown(afterLiftoff = true, days = 0, clock = "00:12:30"), countdown)
        assertEquals("+", countdown.sign)
    }

    @Test
    fun `lists the three soonest launches that have not long flown`() {
        val launches = listOf(
            TestLaunches.launch(id = "flown", net = now - Duration.ofHours(2)),
            TestLaunches.launch(id = "lifting-off", net = now - Duration.ofMinutes(10)),
            TestLaunches.launch(id = "later", net = now + Duration.ofDays(2)),
            TestLaunches.launch(id = "soon", net = now + Duration.ofHours(1)),
            TestLaunches.launch(id = "much-later", net = now + Duration.ofDays(9)),
        )

        assertEquals(
            listOf("lifting-off", "soon", "later"),
            launches.nextLaunches(now).map { it.id },
        )
    }

    @Test
    fun `shows the mission part of the LL2 name`() {
        assertEquals("Bandwagon 5", TestLaunches.launch(name = "Falcon 9 Block 5 | Bandwagon 5").missionName)
        assertEquals("Unnamed", TestLaunches.launch(name = "Unnamed").missionName)
    }
}
