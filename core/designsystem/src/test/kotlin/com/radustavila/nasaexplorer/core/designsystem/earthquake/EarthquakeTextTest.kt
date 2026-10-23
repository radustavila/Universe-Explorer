package com.radustavila.nasaexplorer.core.designsystem.earthquake

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EarthquakeTextTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val metric = UnitFormatter(resources, Locale.UK, MeasurementSystem.METRIC, true, zone = ZoneId.of("UTC"))
    private val imperial = UnitFormatter(resources, Locale.US, MeasurementSystem.IMPERIAL, true, zone = ZoneId.of("UTC"))
    private val sicily = TestEarthquakes.earthquake()

    @Test
    fun `the place distance follows the user's units`() {
        assertEquals("61 km WNW of Leni, Italy", EarthquakeText.place(resources, metric, sicily))
        assertEquals("38 mi WNW of Leni, Italy", EarthquakeText.place(resources, imperial, sicily))
        assertEquals("M 5.7, 61 km WNW of Leni, Italy", EarthquakeText.title(resources, metric, sicily))
    }

    @Test
    fun `places without a distance and quakes without a place read sensibly`() {
        val ridge = sicily.copy(place = "central Mid-Atlantic Ridge")
        assertEquals("central Mid-Atlantic Ridge", EarthquakeText.place(resources, metric, ridge))
        assertEquals("Unknown location", EarthquakeText.place(resources, metric, sicily.copy(place = null)))
    }

    @Test
    fun `depth is converted and classed`() {
        assertEquals("10 km deep, shallow", EarthquakeText.depth(resources, metric, sicily))
        assertEquals("336 mi deep, deep focus", EarthquakeText.depth(resources, imperial, TestEarthquakes.small()))
    }

    @Test
    fun `bigger quakes get bigger markers, within bounds`() {
        val radii = listOf(2.0, 2.5, 4.5, 7.0, 9.5).map { sicily.copy(magnitude = it).markerRadius.value }
        assertEquals(radii, radii.sorted())
        assertTrue(radii.first() >= 3f && radii.last() <= 16f)
    }
}
