package com.radustavila.nasaexplorer.data.dsn.remote

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The pull parser against `dsn.xml` and `config.xml` as recorded on 2026-10-22. */
@RunWith(RobolectricTestRunner::class)
class DsnXmlParserTest {

    private fun live(xml: String = Fixtures.read(LIVE_FIXTURE)) = DsnXmlParser.parseLive(xml.byteInputStream())

    @Test
    fun `recorded feed has three sites and their dishes in order`() {
        val feed = live()

        assertEquals(1_792_685_598_000L, feed.timestampMillis)
        assertEquals(listOf("gdscc", "mdscc", "cdscc"), feed.stations.map { it.name })
        assertEquals(listOf("Goldstone", "Madrid", "Canberra"), feed.stations.map { it.friendlyName })
        assertEquals(listOf("DSS14", "DSS24", "DSS25", "DSS26", "DSS23"), feed.stations[0].dishes.map { it.name })
        assertEquals(13, feed.stations.sumOf { it.dishes.size })
    }

    @Test
    fun `an active dish carries pointing, signals and the target's range`() {
        val voyager = live().stations[2].dishes.single { it.name == "DSS43" }

        assertEquals(207.0, voyager.azimuth!!, 0.0)
        assertEquals(17.0, voyager.elevation!!, 0.0)
        assertEquals("Spacecraft Telemetry, Tracking, and Command", voyager.activity)
        assertTrue(voyager.upSignals.isEmpty())
        val down = voyager.downSignals.single()
        assertEquals("VGR2", down.spacecraft)
        assertEquals(-32, down.spacecraftId)
        assertTrue(down.active)
        assertEquals("X", down.band)
        assertEquals(160.0, down.dataRate!!, 0.0)
        val target = voyager.targets.single()
        assertEquals(32, target.id)
        assertEquals(21_600_000_000.0, target.downlegRange!!, 0.0)
    }

    @Test
    fun `idle dishes and unknown ranges come through as the feed says them`() {
        val goldstone = live().stations[0]

        val idle = goldstone.dishes.single { it.name == "DSS14" }
        assertEquals("Engineering Upgrades", idle.activity)
        assertEquals(99, idle.targets.single().id)
        assertTrue(idle.upSignals.isEmpty() && idle.downSignals.isEmpty())
        // Wind speed is empty on idle dishes; the parser does not care.
        val mro = goldstone.dishes.single { it.name == "DSS25" }
        assertEquals(-1.0, mro.targets.single().downlegRange!!, 0.0)
        assertFalse(mro.downSignals.single().active)
    }

    @Test
    fun `a dish talking on two bands keeps both downlinks`() {
        val webb = live().stations[2].dishes.single { it.name == "DSS34" }

        assertEquals(listOf("S", "K"), webb.downSignals.map { it.band })
        assertEquals(28_000_000.0, webb.downSignals[1].dataRate!!, 0.0)
    }

    @Test
    fun `unknown elements, missing attributes and stray dishes are tolerated`() {
        val feed = live(
            """
            <dsn>
              <dish name="DSS99" azimuthAngle="1"/>
              <station name="gdscc"/>
              <dish name="DSS14" azimuthAngle="" elevationAngle="abc">
                <weather temperature="20"/>
                <downSignal active="maybe" dataRate=""/>
                <target uplegRange="5"/>
              </dish>
              <dish azimuthAngle="5"/>
            </dsn>
            """.trimIndent(),
        )

        assertNull(feed.timestampMillis)
        val dish = feed.stations.single().dishes.single()
        assertEquals("DSS14", dish.name)
        assertNull(dish.azimuth)
        assertNull(dish.elevation)
        assertFalse(dish.downSignals.single().active)
        assertNull(dish.downSignals.single().dataRate)
        assertTrue(dish.targets.isEmpty())
    }

    @Test(expected = SerializationException::class)
    fun `unreadable xml is a serialization failure`() {
        live("<dsn><station name=\"gdscc\"></dsn")
    }

    @Test
    fun `config maps sites to dishes and codes to names`() {
        val config = DsnXmlParser.parseConfig(Fixtures.read(CONFIG_FIXTURE).byteInputStream())

        assertEquals(listOf("mdscc", "gdscc", "cdscc"), config.sites.map { it.name })
        val canberra = config.sites.single { it.name == "cdscc" }
        assertEquals(listOf("DSS43", "DSS34", "DSS35", "DSS36"), canberra.dishes.map { it.name })
        assertEquals("70M", canberra.dishes.first().type)
        assertEquals(170, config.spacecraft.size)
        assertEquals("Voyager 2", config.spacecraft.single { it.name == "vgr2" }.friendlyName)
    }

    private companion object {
        const val LIVE_FIXTURE = "dsn/dsn.xml"
        const val CONFIG_FIXTURE = "dsn/config.xml"
    }
}
