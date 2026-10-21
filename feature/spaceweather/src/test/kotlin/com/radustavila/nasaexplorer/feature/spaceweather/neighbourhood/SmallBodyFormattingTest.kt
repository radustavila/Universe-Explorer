package com.radustavila.nasaexplorer.feature.spaceweather.neighbourhood

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.SizeEstimate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmallBodyFormattingTest {

    private val locale = Locale.US
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val metric = UnitFormatter(resources, locale, MeasurementSystem.METRIC, is24Hour = false)
    private val imperial = UnitFormatter(resources, locale, MeasurementSystem.IMPERIAL, is24Hour = false)

    @Test
    fun `distances, sizes and odds read like a person wrote them`() {
        assertEquals("7.6", formatLunarDistance(7.58, locale))
        assertEquals("16", formatLunarDistance(15.65, locale))
        assertEquals("7,6", formatLunarDistance(7.58, Locale.GERMANY))
        assertEquals("2.9 million km", metric.largeDistance(2_913_000.0))
        assertEquals("748,000 km", metric.largeDistance(747_812.0))
        assertEquals(6_300L, oddsAgainst(1.593373699e-4))
        assertEquals(340_000L, oddsAgainst(2.937e-6))
        assertNull(oddsAgainst(0.0))
    }

    @Test
    fun `sizes follow the unit system`() {
        assertEquals("34–75 m", SizeEstimate(33.6, 75.2).format(metric))
        assertEquals("340 m", SizeEstimate(340.0, 340.0).format(metric))
        assertEquals("1.2–2.7 km", SizeEstimate(1_210.0, 2_700.0).format(metric))
        assertEquals("110–250 ft", SizeEstimate(33.6, 75.2).format(imperial))
        assertEquals("1,100 ft", SizeEstimate(340.0, 340.0).format(imperial))
        assertEquals("0.8–1.7 mi", SizeEstimate(1_210.0, 2_700.0).format(imperial))
    }
}
