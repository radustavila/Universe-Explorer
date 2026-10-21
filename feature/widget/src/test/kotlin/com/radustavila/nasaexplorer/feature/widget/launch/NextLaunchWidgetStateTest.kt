package com.radustavila.nasaexplorer.feature.widget.launch

import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches.NOW
import com.radustavila.nasaexplorer.feature.widget.testResources
import com.radustavila.nasaexplorer.feature.widget.testWidgetFormat
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NextLaunchWidgetStateTest {

    private fun state(vararg launches: Launch, now: Instant = NOW) =
        nextLaunchWidgetState(launches.toList(), now, testWidgetFormat(), testResources)

    @Test
    fun `a launch later today counts down live and by the minute`() {
        val launch = TestLaunches.launch(net = NOW.plus(Duration.ofMinutes(200)))

        val state = checkNotNull(state(launch))

        assertEquals("Bandwagon 5", state.mission)
        assertEquals("SpaceX · Falcon 9 Block 5", state.vehicle)
        assertEquals("13:20", state.net)
        assertEquals("in 3 h 20 m", state.countdown)
        assertEquals(launch.net, state.liveCountdownTo)
        assertEquals("Go", state.status)
        assertEquals("Bandwagon 5. SpaceX · Falcon 9 Block 5. Target 13:20, in 3 h 20 m. Go", state.contentDescription)
    }

    @Test
    fun `a launch days away shows its date and hours to go, without the live countdown`() {
        val state = checkNotNull(state(TestLaunches.launch(net = NOW.plus(Duration.ofDays(3)), status = LaunchStatus.TBC)))

        assertEquals("Thu 22 Oct 10:00", state.net)
        assertEquals("in 3 d 0 h", state.countdown)
        assertNull(state.liveCountdownTo)
        assertEquals("TBC", state.status)
    }

    @Test
    fun `the soonest launch wins, and one just past stays for a while`() {
        val justPast = TestLaunches.launch(id = "past", name = "Electron | Just past", net = NOW.minus(Duration.ofMinutes(30)))
        val longPast = TestLaunches.launch(id = "old", name = "Electron | Long past", net = NOW.minus(Duration.ofHours(2)))
        val later = TestLaunches.launch(id = "later", net = NOW.plus(Duration.ofDays(1)))

        val state = checkNotNull(state(later, longPast, justPast))

        assertEquals("Just past", state.mission)
        assertEquals("Lift-off time", state.countdown)
        assertNull(state.liveCountdownTo)
    }

    @Test
    fun `without a provider the rocket stands alone, and some statuses go unsaid`() {
        val state = checkNotNull(state(TestLaunches.launch(provider = null, status = LaunchStatus.UNKNOWN)))

        assertEquals("Falcon 9 Block 5", state.vehicle)
        assertNull(state.status)
    }

    @Test
    fun `nothing upcoming means no state`() {
        assertNull(state())
        assertNull(state(TestLaunches.launch(net = NOW.minus(Duration.ofHours(2)))))
    }
}
