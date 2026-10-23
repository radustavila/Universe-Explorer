package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeRepository
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlert
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [EarthquakeRepository]: the cache is whatever the test seeds, newest first. */
class FakeEarthquakeRepository(initial: List<Earthquake> = emptyList()) : EarthquakeRepository {

    private val quakes = MutableStateFlow(initial.sortedByDescending(Earthquake::time))

    var refreshResult: DataResult<Int> = DataResult.Success(0)

    var refreshCallCount: Int = 0
        private set

    fun setEarthquakes(entries: List<Earthquake>) {
        quakes.value = entries.sortedByDescending(Earthquake::time)
    }

    override fun observeEarthquakes(): Flow<List<Earthquake>> = quakes

    override suspend fun refreshEarthquakes(): DataResult<Int> {
        refreshCallCount++
        return refreshResult
    }
}

class FakeEarthquakeAlertRepository(enabled: Boolean = false, var alertedUntil: Instant? = null) :
    EarthquakeAlertRepository {

    private val state = MutableStateFlow(enabled)

    override val enabled: Flow<Boolean> = state

    val isEnabled: Boolean get() = state.value

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }

    override suspend fun alertedUntil(): Instant? = alertedUntil

    override suspend fun setAlertedUntil(until: Instant) {
        alertedUntil = until
    }
}

/** Records every alert instead of posting it. */
class FakeEarthquakeAlertNotifier : EarthquakeAlertNotifier {
    val posted = mutableListOf<EarthquakeAlert>()

    override suspend fun notifyEarthquake(alert: EarthquakeAlert) {
        posted += alert
    }
}
