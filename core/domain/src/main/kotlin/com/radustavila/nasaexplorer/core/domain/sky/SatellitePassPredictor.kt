package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import java.time.Duration
import java.time.Instant

/** Satellite predictions from cached orbital elements, propagated on the device with SGP4. */
interface SatellitePassPredictor {

    /**
     * Passes that peak between [from] and [to] and climb at least [minElevationDegrees] above the
     * horizon, soonest first. A pass already under way at [from] is included.
     */
    fun passes(
        elements: OrbitalElements,
        observer: GeoCoordinate,
        from: Instant,
        to: Instant,
        minElevationDegrees: Double = DEFAULT_MIN_ELEVATION,
    ): List<SatellitePass>

    /**
     * Visible passes (see [SatellitePass.isVisible]) of each of [elements], keyed by catalogue
     * number, for many satellites at once: an implementation can skip the daylight hours, when
     * nothing is visible, for all of them.
     */
    fun visiblePasses(
        elements: List<OrbitalElements>,
        observer: GeoCoordinate,
        from: Instant,
        to: Instant,
        minElevationDegrees: Double = DEFAULT_MIN_ELEVATION,
    ): Map<Int, List<SatellitePass>> = elements.associate { satellite ->
        satellite.noradId to passes(satellite, observer, from, to, minElevationDegrees).filter(SatellitePass::isVisible)
    }

    /** Sub-satellite point at [at], or null when the elements cannot be propagated there. */
    fun position(elements: OrbitalElements, at: Instant): SatellitePosition?

    /** Sub-satellite points from [from] to [to], one every [step]. */
    fun groundTrack(
        elements: OrbitalElements,
        from: Instant,
        to: Instant,
        step: Duration = Duration.ofMinutes(1),
    ): List<SatellitePosition>

    companion object {
        /** Lower than this, a pass is mostly hidden by the landscape. */
        const val DEFAULT_MIN_ELEVATION = 10.0
    }
}
