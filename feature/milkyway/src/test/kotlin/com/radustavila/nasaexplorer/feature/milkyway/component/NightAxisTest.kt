package com.radustavila.nasaexplorer.feature.milkyway.component

import com.radustavila.nasaexplorer.core.testing.data.TestMilkyWay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightAxisTest {

    @Test
    fun `nights at home span the evening to the morning, an hour either side at most`() {
        val nights = TestMilkyWay.nights()
        val axis = NightAxis.of(nights, ZoneOffset.UTC)

        nights.forEach { night ->
            assertTrue(axis.fraction(night, night.start) in 0f..0.1f)
            assertTrue(axis.fraction(night, night.end) in 0.9f..1f)
        }
        assertEquals(listOf("21:00", "00:00", "03:00"), axis.ticks(nights.first()).map { (_, at) -> clock(at) })
    }

    @Test
    fun `a place far from the device's zone, whose night starts around noon there, is not cut off`() {
        // Sydney's evening seen from Moscow (UTC+3 all year): sunset about 11:23 on the device.
        val nights = TestMilkyWay.nights(first = Instant.parse("2026-10-23T08:23:00Z"))
        val axis = NightAxis.of(nights, ZoneId.of("Europe/Moscow"))

        nights.forEach { night ->
            val sunset = axis.fraction(night, night.start)
            val civilDusk = axis.fraction(night, checkNotNull(night.twilight.civilDusk))
            // Within the hour the sunsets drift over the month, and the rounding to whole hours.
            assertTrue("sunset at $sunset", sunset in 0f..0.2f)
            assertTrue(civilDusk > sunset)
            assertTrue(axis.fraction(night, night.end) in 0.9f..1f)
        }
    }

    private fun clock(at: Instant) = at.atZone(ZoneOffset.UTC).toLocalTime().toString()
}
