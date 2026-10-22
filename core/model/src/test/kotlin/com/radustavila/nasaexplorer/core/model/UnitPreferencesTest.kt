package com.radustavila.nasaexplorer.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitPreferencesTest {

    @Test
    fun `automatic follows the region, not the language`() {
        assertEquals(MeasurementSystem.IMPERIAL, MeasurementSystem.AUTOMATIC.resolve(Locale.US))
        assertEquals(MeasurementSystem.IMPERIAL, MeasurementSystem.AUTOMATIC.resolve(Locale.UK))
        assertEquals(MeasurementSystem.IMPERIAL, MeasurementSystem.AUTOMATIC.resolve(Locale.forLanguageTag("es-US")))
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.AUTOMATIC.resolve(Locale.forLanguageTag("en-AU")))
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.AUTOMATIC.resolve(Locale.GERMANY))
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.AUTOMATIC.resolve(Locale.forLanguageTag("ro-RO")))
        // No region at all: metric, as most of the world.
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.AUTOMATIC.resolve(Locale.ENGLISH))
    }

    @Test
    fun `an explicit choice wins over the region`() {
        assertEquals(MeasurementSystem.METRIC, MeasurementSystem.METRIC.resolve(Locale.US))
        assertEquals(MeasurementSystem.IMPERIAL, MeasurementSystem.IMPERIAL.resolve(Locale.GERMANY))
    }

    @Test
    fun `the time format overrides the system clock only when chosen`() {
        assertTrue(TimeFormat.SYSTEM.is24Hour(systemIs24Hour = true))
        assertFalse(TimeFormat.SYSTEM.is24Hour(systemIs24Hour = false))
        assertTrue(TimeFormat.TWENTY_FOUR_HOUR.is24Hour(systemIs24Hour = false))
        assertFalse(TimeFormat.TWELVE_HOUR.is24Hour(systemIs24Hour = true))
    }
}
