package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.location.CityDirectory
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationProvider
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeObserverLocationRepository(initial: ObserverLocation? = null) : ObserverLocationRepository {

    private val state = MutableStateFlow(initial)

    val current: ObserverLocation? get() = state.value

    override val location: Flow<ObserverLocation?> = state

    override suspend fun setLocation(location: ObserverLocation) {
        state.value = location
    }

    override suspend fun clearLocation() {
        state.value = null
    }
}

/** Answers [result]; [permissionGranted] is what [hasPermission] reports. */
class FakeDeviceLocationProvider(
    var permissionGranted: Boolean = false,
    var result: DeviceLocationResult = DeviceLocationResult.Unavailable,
) : DeviceLocationProvider {

    var requestCount: Int = 0
        private set

    override fun hasPermission(): Boolean = permissionGranted

    override suspend fun currentLocation(): DeviceLocationResult {
        requestCount++
        return if (permissionGranted) result else DeviceLocationResult.PermissionDenied
    }
}

/** Case-insensitive substring search over [cities]. */
class FakeCityDirectory(var cities: List<City> = emptyList()) : CityDirectory {
    override suspend fun search(query: String, limit: Int): List<City> =
        cities.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
}
