package com.radustavila.nasaexplorer.core.domain.earthquake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Recent earthquakes from the USGS feeds. Reads come from the local cache; the network only ever
 * writes into it.
 */
interface EarthquakeRepository {

    /** Cached quakes of every feed, each once, newest first. Emits whenever the cache changes. */
    fun observeEarthquakes(): Flow<List<Earthquake>>

    /**
     * Brings each feed up to date in one request per feed, with `If-Modified-Since` so an unchanged
     * feed costs a bodyless 304. On failure a feed keeps its cached quakes.
     *
     * @return the number of quakes now cached.
     */
    suspend fun refreshEarthquakes(): DataResult<Int>
}

/** The user's strong-quake alert choice, and the newest quake already announced. */
interface EarthquakeAlertRepository {

    /** Off until the user turns it on. */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)

    /** Time of the newest quake announced: older ones never alert again. */
    suspend fun alertedUntil(): Instant?

    suspend fun setAlertedUntil(until: Instant)
}

/** Posts the strong-quake notification; stays silent when the platform does not allow it. */
interface EarthquakeAlertNotifier {
    suspend fun notifyEarthquake(alert: EarthquakeAlert)
}
