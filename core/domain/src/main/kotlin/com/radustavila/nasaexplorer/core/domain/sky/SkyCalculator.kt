package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SkyTonight
import java.time.Instant

/** Positions of the Sun, Moon and planets, computed on the device (no network, no quota). */
interface SkyCalculator {

    /**
     * The night under way at [now], or the coming one when it is still day: from the evening's
     * sunset to the next sunrise.
     */
    fun skyTonight(observer: GeoCoordinate, now: Instant): SkyTonight
}
