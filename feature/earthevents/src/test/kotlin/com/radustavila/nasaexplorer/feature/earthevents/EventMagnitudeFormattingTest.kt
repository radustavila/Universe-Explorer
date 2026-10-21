package com.radustavila.nasaexplorer.feature.earthevents

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.EventMagnitude
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventMagnitudeFormattingTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val metric = UnitFormatter(resources, Locale.US, MeasurementSystem.METRIC, is24Hour = true)
    private val imperial = UnitFormatter(resources, Locale.US, MeasurementSystem.IMPERIAL, is24Hour = true)

    @Test
    fun `wind, burned land and ice are shown in the user's units`() {
        assertEquals("102 km/h", EventMagnitude(55.0, "kts").formatted(metric))
        assertEquals("63 mph", EventMagnitude(55.0, "kts").formatted(imperial))
        assertEquals("1,214 ha", EventMagnitude(3000.0, "acres").formatted(metric))
        assertEquals("3,000 acres", EventMagnitude(3000.0, "acres").formatted(imperial))
        assertEquals("43 km²", EventMagnitude(12.5, "NM^2").formatted(metric))
        assertEquals("17 mi²", EventMagnitude(12.5, "NM^2").formatted(imperial))
    }

    @Test
    fun `an unknown unit is shown as reported`() {
        assertEquals("4.5 Mw", EventMagnitude(4.5, "Mw").formatted(metric))
    }
}
