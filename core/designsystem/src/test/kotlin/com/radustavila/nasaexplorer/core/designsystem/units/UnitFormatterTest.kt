package com.radustavila.nasaexplorer.core.designsystem.units

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnitFormatterTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun formatter(
        system: MeasurementSystem = MeasurementSystem.METRIC,
        locale: Locale = Locale.UK,
        is24Hour: Boolean = true,
    ) = UnitFormatter(resources, locale, system, is24Hour, zone = ZoneId.of("UTC"))

    private val metric = formatter(MeasurementSystem.METRIC)
    private val imperial = formatter(MeasurementSystem.IMPERIAL)

    @Test
    fun `distances and altitudes convert to miles`() {
        assertEquals("408 km", metric.distance(408.4))
        assertEquals("254 mi", imperial.distance(408.4))
        assertEquals("1.5 km", metric.distance(1.5, fractionDigits = 1))
    }

    @Test
    fun `large distances read in millions or round to the thousand`() {
        assertEquals("2.9 million km", metric.largeDistance(2_900_000.0))
        assertEquals("1.8 million mi", imperial.largeDistance(2_900_000.0))
        assertEquals("748,000 km", metric.largeDistance(748_200.0))
        assertEquals("465,000 mi", imperial.largeDistance(748_200.0))
        assertEquals("21.6 billion km", metric.largeDistance(21_600_000_000.0))
        assertEquals("13.4 billion mi", imperial.largeDistance(21_600_000_000.0))
    }

    @Test
    fun `rough lengths keep two significant figures and switch to the larger unit`() {
        assertEquals("34–75 m", metric.length(34.0, 75.0))
        assertEquals("110–250 ft", imperial.length(34.0, 75.0))
        assertEquals("340 m", metric.length(340.0))
        assertEquals("1.2 km", metric.length(1_200.0))
        assertEquals("3,900 ft", imperial.length(1_200.0))
        assertEquals("1.2 mi", imperial.length(2_000.0))
    }

    @Test
    fun `speeds convert to mph and miles per second`() {
        assertEquals("27,600 km/h", metric.speedPerHour(27_600.0))
        assertEquals("17,150 mph", imperial.speedPerHour(27_600.0))
        assertEquals("12.3 km/s", metric.speedPerSecond(12.34))
        assertEquals("7.7 mi/s", imperial.speedPerSecond(12.34))
    }

    @Test
    fun `temperatures and areas follow the system`() {
        assertEquals("21 °C", metric.temperature(21.0))
        assertEquals("70 °F", imperial.temperature(21.0))
        assertEquals("1,000 km²", metric.area(1_000.0))
        assertEquals("386 mi²", imperial.area(1_000.0))
        assertEquals("304 ha", metric.landArea(751.0))
        assertEquals("751 acres", imperial.landArea(751.0))
        assertEquals("1 acre", imperial.landArea(1.0))
    }

    @Test
    fun `numbers use the locale's separators in both systems`() {
        val german = formatter(MeasurementSystem.METRIC, Locale.GERMANY)
        val germanImperial = formatter(MeasurementSystem.IMPERIAL, Locale.GERMANY)

        assertEquals("27.600 km/h", german.speedPerHour(27_600.0))
        assertEquals("17.150 mph", germanImperial.speedPerHour(27_600.0))
        assertEquals("2,9 million km", german.largeDistance(2_900_000.0))
        assertEquals("51,5° N, 0,1° W", german.coordinate(51.5, -0.12))
    }

    @Test
    fun `coordinates name their hemispheres`() {
        assertEquals("51.5° N, 0.1° W", metric.coordinate(51.5, -0.12))
        assertEquals("33.9° S, 151.2° E", metric.coordinate(-33.87, 151.21))
    }

    @Test
    fun `clock times follow the chosen 12 or 24 hour format in every locale`() {
        val evening = Instant.parse("2026-10-21T21:30:00Z")
        for (locale in listOf(Locale.US, Locale.UK, Locale.GERMANY, Locale.JAPAN, Locale.forLanguageTag("ro-RO"))) {
            val twentyFour = formatter(locale = locale, is24Hour = true)
            val twelve = formatter(locale = locale, is24Hour = false)

            assertTrue("$locale 24 h: ${twentyFour.time(evening)}", twentyFour.time(evening).contains("21:30"))
            assertTrue("$locale 12 h: ${twelve.time(evening)}", twelve.time(evening).contains("9:30"))
            assertFalse("$locale 12 h: ${twelve.time(evening)}", twelve.time(evening).contains("21"))

            assertTrue(twentyFour.dateTime(evening).contains("21:30"))
            assertTrue(twentyFour.dateTime(evening).contains("2026"))
            assertTrue(twelve.dateTime(evening).contains("9:30"))
            assertTrue(twelve.dayAndTime(evening).contains("9:30"))
        }
    }

    @Test
    fun `date and time keep the locale's order`() {
        val evening = Instant.parse("2026-10-21T21:30:00Z")

        assertEquals("Oct 21, 2026, 9:30 PM", formatter(locale = Locale.US, is24Hour = false).dateTime(evening).normalizeSpaces())
        assertEquals("21 Oct 2026, 21:30", formatter(locale = Locale.UK, is24Hour = true).dateTime(evening).normalizeSpaces())
    }

    /** ICU puts narrow no-break spaces before "PM" and in some dates. */
    private fun String.normalizeSpaces(): String = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `climate values convert as changes, small heights and millions of square units`() {
        assertEquals("+1.38 °C", metric.temperatureChange(1.38))
        assertEquals("+2.48 °F", imperial.temperatureChange(1.38))
        assertEquals("\u22120.19 °C", metric.temperatureChange(-0.19))
        assertEquals("0.0 °C", metric.temperatureChange(-0.01, fractionDigits = 1))
        assertEquals("84 mm", metric.smallLength(83.6))
        assertEquals("+84 mm", metric.smallLength(83.6, signed = true))
        assertEquals("3.3 in", imperial.smallLength(83.6))
        assertEquals("4.81 million km²", metric.millionArea(4.81))
        assertEquals("1.86 million mi²", imperial.millionArea(4.81))
        assertEquals("\u22122.86 million km²", metric.millionArea(-2.86, signed = true))
    }

    @Test
    fun `a month reads the same in every time zone`() {
        assertEquals("Sept 2026", metric.month(java.time.LocalDate.of(2026, 9, 1)))
        assertEquals("Sep 2026", formatter(locale = Locale.US).month(java.time.LocalDate.of(2026, 9, 1)))
    }
}
