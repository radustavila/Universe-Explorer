package com.radustavila.nasaexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryMethodTest {

    @Test
    fun `tap short codes map to methods`() {
        assertEquals(DiscoveryMethod.TRANSIT, DiscoveryMethod.fromArchive("tran"))
        assertEquals(DiscoveryMethod.RADIAL_VELOCITY, DiscoveryMethod.fromArchive("rv"))
        assertEquals(DiscoveryMethod.MICROLENSING, DiscoveryMethod.fromArchive("micro"))
        assertEquals(DiscoveryMethod.IMAGING, DiscoveryMethod.fromArchive("ima"))
        assertEquals(DiscoveryMethod.TRANSIT_TIMING, DiscoveryMethod.fromArchive("ttv"))
        assertEquals(DiscoveryMethod.ECLIPSE_TIMING, DiscoveryMethod.fromArchive("etv"))
        assertEquals(DiscoveryMethod.ORBITAL_BRIGHTNESS, DiscoveryMethod.fromArchive("obm"))
        assertEquals(DiscoveryMethod.PULSAR_TIMING, DiscoveryMethod.fromArchive("pul"))
        assertEquals(DiscoveryMethod.PULSATION_TIMING, DiscoveryMethod.fromArchive("ptv"))
        assertEquals(DiscoveryMethod.ASTROMETRY, DiscoveryMethod.fromArchive("ast"))
        assertEquals(DiscoveryMethod.DISK_KINEMATICS, DiscoveryMethod.fromArchive("dkin"))
    }

    @Test
    fun `long names are accepted regardless of case`() {
        assertEquals(DiscoveryMethod.TRANSIT, DiscoveryMethod.fromArchive("Transit"))
        assertEquals(DiscoveryMethod.RADIAL_VELOCITY, DiscoveryMethod.fromArchive(" Radial Velocity "))
    }

    @Test
    fun `unknown or missing methods fall back to other`() {
        assertEquals(DiscoveryMethod.OTHER, DiscoveryMethod.fromArchive("telepathy"))
        assertEquals(DiscoveryMethod.OTHER, DiscoveryMethod.fromArchive(null))
    }
}
