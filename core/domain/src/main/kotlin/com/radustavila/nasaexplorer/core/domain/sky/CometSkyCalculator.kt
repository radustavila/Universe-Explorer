package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import java.time.Instant

/**
 * Where a comet is and how bright it is predicted to be, from its cached geocentric ephemeris: the
 * observer's place never leaves the device. Pure computation, a few milliseconds per comet.
 */
interface CometSkyCalculator {

    /**
     * The comet at [now], with its rise, set and best time for [observer] when there is one; null
     * when its ephemeris does not cover [now] (not downloaded yet, or months out of date).
     */
    fun tonight(comet: Comet, observer: GeoCoordinate?, now: Instant): CometTonight?
}
