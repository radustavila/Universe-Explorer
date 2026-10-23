package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometEphemerisPoint
import com.radustavila.nasaexplorer.core.model.CometLocalSky
import com.radustavila.nasaexplorer.core.model.CometSkyPosition
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import java.time.Duration
import java.time.Instant

/** Comets and their nights for tests, modelled on JPL's answers of 2026-10-23. */
object TestComets {

    val NOW: Instant = Instant.parse("2026-10-23T18:00:00Z")

    /** A daily brightness curve over ±60 days, brightest [peakDay] days from [NOW]. */
    fun comet(
        designation: String = "2026 A2",
        name: String = "C/2026 A2 (Bok)",
        absoluteMagnitude: Double? = 9.2,
        peakMagnitude: Double = 13.7,
        peakDay: Int = 33,
    ) = Comet(
        designation = designation,
        name = name,
        absoluteMagnitude = absoluteMagnitude,
        slopeParameter = 12.0,
        perihelionDistanceAu = 1.938,
        eccentricity = 0.9976,
        perihelionTime = Instant.parse("2026-12-22T08:24:00Z"),
        ephemeris = (-60..60).map { day ->
            CometEphemerisPoint(
                time = NOW.plus(Duration.ofDays(day.toLong())),
                position = EquatorialCoordinate(rightAscensionDegrees = 292.5 + day * 0.4, declinationDegrees = 59.8),
                magnitude = peakMagnitude + 0.0005 * (day - peakDay) * (day - peakDay),
                sunDistanceAu = 2.07,
                earthDistanceAu = 1.70,
            )
        },
    )

    fun tonight(
        comet: Comet = comet(),
        magnitude: Double? = 14.2,
        constellation: String = "Draco",
        sky: CometLocalSky? = localSky(),
    ) = CometTonight(
        comet = comet,
        computedAt = NOW,
        magnitude = magnitude,
        visibility = CometVisibility.fromMagnitude(magnitude),
        position = EquatorialCoordinate(rightAscensionDegrees = 292.5, declinationDegrees = 59.8),
        constellation = constellation,
        sunDistanceAu = 2.07,
        earthDistanceAu = 1.70,
        peakMagnitude = comet.ephemeris.mapNotNull { it.magnitude }.minOrNull(),
        peakTime = comet.ephemeris.filter { it.magnitude != null }.minByOrNull { it.magnitude!! }?.time,
        sky = sky,
    )

    /** Where [comet] is on the sky map: a bright one by default, well up in the north-west. */
    fun skyPosition(
        comet: Comet = comet(),
        magnitude: Double? = 8.4,
        altitudeDegrees: Double = 52.0,
        azimuthDegrees: Double = 310.0,
    ) = CometSkyPosition(
        designation = comet.designation,
        name = comet.name,
        horizontal = HorizontalCoordinate(altitudeDegrees = altitudeDegrees, azimuthDegrees = azimuthDegrees),
        equatorial = EquatorialCoordinate(rightAscensionDegrees = 292.5, declinationDegrees = 59.8),
        magnitude = magnitude,
    )

    fun localSky(
        bestTime: Instant? = Instant.parse("2026-10-23T19:40:00Z"),
        isAlwaysUp: Boolean = false,
    ) = CometLocalSky(
        now = HorizontalCoordinate(altitudeDegrees = 52.0, azimuthDegrees = 310.0),
        rise = if (isAlwaysUp) null else Instant.parse("2026-10-24T08:10:00Z"),
        set = if (isAlwaysUp) null else Instant.parse("2026-10-24T03:20:00Z"),
        isAlwaysUp = isAlwaysUp,
        bestTime = bestTime,
        best = bestTime?.let { HorizontalCoordinate(altitudeDegrees = 61.0, azimuthDegrees = 300.0) },
    )
}
