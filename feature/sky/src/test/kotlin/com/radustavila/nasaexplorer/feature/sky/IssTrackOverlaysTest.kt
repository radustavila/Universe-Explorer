package com.radustavila.nasaexplorer.feature.sky

import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.sky.component.splitAtAntimeridian
import org.junit.Assert.assertEquals
import org.junit.Test

class IssTrackOverlaysTest {

    @Test
    fun `the track is cut where it crosses the antimeridian`() {
        val track = listOf(170.0, 175.0, 179.5, -178.0, -173.0, -168.0).map { TestSky.position(longitude = it) }

        val segments = track.splitAtAntimeridian()

        assertEquals(listOf(3, 3), segments.map { it.size })
        assertEquals(-178.0, segments[1].first().longitude, 0.0)
    }

    @Test
    fun `a lone point left over by a cut is dropped`() {
        val track = listOf(178.0, 179.0, -179.5).map { TestSky.position(longitude = it) }

        assertEquals(listOf(2), track.splitAtAntimeridian().map { it.size })
    }
}
