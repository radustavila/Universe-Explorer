package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import com.radustavila.nasaexplorer.core.model.SizeEstimate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SmallBodyFormattingTest {

    private val locale = Locale.US

    @Test
    fun `distances, sizes and odds read like a person wrote them`() {
        assertEquals("7.6", formatLunarDistance(7.58, locale))
        assertEquals("16", formatLunarDistance(15.65, locale))
        assertEquals("2.9 million", formatKilometres(2_913_000.0, locale))
        assertEquals("748,000", formatKilometres(747_812.0, locale))
        assertEquals("34–75 m", formatSize(SizeEstimate(33.6, 75.2), locale))
        assertEquals("340 m", formatSize(SizeEstimate(340.0, 340.0), locale))
        assertEquals("1.2–2.7 km", formatSize(SizeEstimate(1_210.0, 2_700.0), locale))
        assertEquals("1 in 6,300", formatOdds(1.593373699e-4, locale))
        assertEquals("1 in 340,000", formatOdds(2.937e-6, locale))
    }
}
