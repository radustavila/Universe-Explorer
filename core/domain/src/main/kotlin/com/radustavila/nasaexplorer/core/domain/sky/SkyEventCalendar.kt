package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.core.model.SkyEventId
import java.time.Instant
import java.time.ZoneOffset

/**
 * Eclipses, Moon phases, planet events, meteor shower peaks and seasons for any dates, computed on
 * the device (no network, no quota). Pure computation: the same answer for the same arguments.
 */
interface SkyEventCalendar {

    /**
     * Every event of [categories] whose [SkyEvent.time] is in [from] (inclusive) to [until]
     * (exclusive), in time order. With an [observer] the local parts are filled in: whether an
     * eclipse can be seen, the local contact times, a meteor shower's best night.
     *
     * Takes up to a few hundred milliseconds for a month: call it off the main thread.
     */
    fun events(
        from: Instant,
        until: Instant,
        observer: GeoCoordinate?,
        categories: Set<SkyEventCategory> = SkyEventCategory.entries.toSet(),
    ): List<SkyEvent>
}

/**
 * The event [id] names (a [SkyEvent.id]) for [observer], or null when the id is not one or no such
 * event exists. Only the few days around the date in the id are computed.
 */
fun SkyEventCalendar.find(id: String, observer: GeoCoordinate?): SkyEvent? {
    val date = SkyEventId.dateOf(id) ?: return null
    return events(
        from = date.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
        until = date.plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC),
        observer = observer,
    ).firstOrNull { it.id == id }
}
