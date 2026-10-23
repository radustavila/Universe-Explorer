package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.domain.sky.SkyPositionCalculator
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import java.time.Instant
import javax.inject.Inject

/** [CelestialFrames]' J2000 to horizontal rotation, the one the sky map draws with. */
class CelestialSkyPositionCalculator @Inject constructor() : SkyPositionCalculator {

    override fun horizontal(
        position: EquatorialCoordinate,
        observer: GeoCoordinate,
        at: Instant,
    ): HorizontalCoordinate =
        (CelestialFrames.j2000ToHorizontal(observer, at) * SkyVector.of(position)).toHorizontal()
}
