package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpKind
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import java.time.Duration
import java.time.Instant

object TestSpaceWeather {

    val NOW: Instant = Instant.parse("2026-10-19T14:20:00Z")

    /** The 3-hour slot containing [NOW]. */
    val CURRENT_SLOT: Instant = Instant.parse("2026-10-19T12:00:00Z")

    fun reading(start: Instant, kp: Double, kind: KpKind = KpKind.PREDICTED) = KpReading(start, kp, kind)

    /**
     * Consecutive slots from [from], one per value: observed before [NOW]'s slot, estimated for it,
     * predicted after.
     */
    fun forecast(vararg kp: Double, from: Instant = CURRENT_SLOT.minus(Duration.ofHours(6))): List<KpReading> =
        kp.mapIndexed { index, value ->
            val start = from.plus(KpReading.SLOT.multipliedBy(index.toLong()))
            val kind = when {
                start.isBefore(CURRENT_SLOT) -> KpKind.OBSERVED
                start == CURRENT_SLOT -> KpKind.ESTIMATED
                else -> KpKind.PREDICTED
            }
            KpReading(start, value, kind)
        }

    fun currentKp(kp: Double = 2.33) = CurrentKp(time = NOW, kp = kp)

    fun flare(classType: String = "M1.6") = SolarFlare(
        id = "2026-10-18T09:47:00-FLR-001",
        classType = classType,
        beginTime = Instant.parse("2026-10-18T09:47:00Z"),
        peakTime = Instant.parse("2026-10-18T10:02:00Z"),
        endTime = Instant.parse("2026-10-18T10:15:00Z"),
        sourceLocation = "S12W30",
        activeRegion = 14560,
        link = "https://ccmc.gsfc.nasa.gov/DONKI/view/FLR/49150/-1",
    )

    fun cme(earthArrival: Instant? = null) = CoronalMassEjection(
        id = "2026-10-18T23:15:00-CME-001",
        startTime = Instant.parse("2026-10-18T23:15:00Z"),
        speedKmPerSecond = 590.0,
        halfAngleDegrees = 20.0,
        sourceLocation = null,
        note = "CME first seen to the West by GOES CCOR-1.",
        earthArrival = earthArrival,
        isEarthGlancingBlow = false,
        link = "https://ccmc.gsfc.nasa.gov/DONKI/view/CME/49162/-1",
    )

    /** A grid with [probability] % in a band of latitudes in both hemispheres, 0 elsewhere. */
    fun auroraForecast(
        probability: Int = 40,
        band: IntRange = 60..70,
        observedAt: Instant = NOW.minus(Duration.ofMinutes(10)),
    ): AuroraForecast {
        val grid = ByteArray(AuroraForecast.LONGITUDES * AuroraForecast.LATITUDES)
        for (lon in 0 until AuroraForecast.LONGITUDES) {
            for (lat in band) {
                grid[AuroraForecast.index(lon, lat)] = probability.toByte()
                grid[AuroraForecast.index(lon, -lat)] = probability.toByte()
            }
        }
        return AuroraForecast(observedAt = observedAt, forecastFor = observedAt.plus(Duration.ofMinutes(70)), probabilities = grid)
    }
}
