package com.radustavila.nasaexplorer.feature.dsn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import java.time.Duration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DsnFormatterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun formatter(system: MeasurementSystem = MeasurementSystem.METRIC, locale: Locale = Locale.US) =
        DsnFormatter(context.resources, UnitFormatter(context.resources, locale, system, is24Hour = true))

    @Test
    fun `light time reads in seconds, minutes or hours`() {
        val format = formatter()

        assertEquals("5.0 s", format.lightTime(Duration.ofMillis(5_037)))
        assertEquals("8 min 20 s", format.lightTime(Duration.ofSeconds(500)))
        assertEquals("19 h 58 min", format.lightTime(Duration.ofMinutes(19 * 60 + 58).plusSeconds(40)))
    }

    @Test
    fun `data rates pick their unit`() {
        val format = formatter()

        assertEquals("160 bit/s", format.dataRate(160.0))
        assertEquals("88 kbit/s", format.dataRate(87_650.0))
        assertEquals("5.2 kbit/s", format.dataRate(5_200.0))
        assertEquals("2.5 Mbit/s", format.dataRate(2_500_000.0))
        assertEquals("28 Mbit/s", format.dataRate(28_000_000.0))
    }

    @Test
    fun `AU are never converted, kilometres are`() {
        assertEquals("172.65 AU", formatter(MeasurementSystem.IMPERIAL).astronomicalUnits(172.652953))
        assertEquals("13.4 billion mi", formatter(MeasurementSystem.IMPERIAL).distance(21_600_000_000.0))
        assertEquals("172,65 AU", formatter(locale = Locale.GERMANY).astronomicalUnits(172.652953))
    }

    @Test
    fun `mission notes are keyed by the feed's code`() {
        assertEquals(R.string.dsn_note_vgr2, missionNoteRes("VGR2"))
        assertEquals(null, missionNoteRes("XYZ"))
    }
}
