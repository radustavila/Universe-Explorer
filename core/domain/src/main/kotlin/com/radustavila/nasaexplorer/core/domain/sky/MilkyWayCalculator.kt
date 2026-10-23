package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import java.time.Instant
import java.time.Month

/**
 * When the galactic core can be photographed, computed on the device: the core above 10°, the Sun
 * 18° down and the Moon down or nearly new, plus the twilights and the golden and blue hours.
 * Pure computation, a few milliseconds a night: call it off the main thread.
 */
interface MilkyWayCalculator {

    /**
     * [count] nights in a row, starting with the one under way at [now] (or the coming one while
     * the Sun is up or once tonight's dark part is over).
     */
    fun nights(observer: GeoCoordinate, now: Instant, count: Int): List<MilkyWayNight>

    /** The months of [year] with dark hours while the core is up at [observer], ignoring the Moon. */
    fun season(observer: GeoCoordinate, year: Int): Set<Month>
}
