package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sky.MilkyWayCalculator
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import java.time.Instant
import java.time.Month

/** Returns the first `count` of [nights] and [season], whatever the place and time; counts the calls. */
class FakeMilkyWayCalculator(
    var nights: List<MilkyWayNight> = emptyList(),
    var season: Set<Month> = emptySet(),
) : MilkyWayCalculator {

    val requests = mutableListOf<Pair<GeoCoordinate, Instant>>()

    override fun nights(observer: GeoCoordinate, now: Instant, count: Int): List<MilkyWayNight> {
        requests += observer to now
        return nights.take(count)
    }

    override fun season(observer: GeoCoordinate, year: Int): Set<Month> = season
}
