package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.location.CityDirectory
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationProvider
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The saved observer location, shared by every feature that depends on where the user is. */
class ObserveObserverLocationUseCase @Inject constructor(
    private val repository: ObserverLocationRepository,
) {
    operator fun invoke(): Flow<ObserverLocation?> = repository.location
}

/**
 * Takes one coarse fix from the device and saves it. Rounded to two decimals (about a kilometre):
 * more precision changes no rise or set time and is not worth keeping.
 */
class LocateDeviceUseCase @Inject constructor(
    private val provider: DeviceLocationProvider,
    private val repository: ObserverLocationRepository,
) {
    suspend operator fun invoke(): DeviceLocationResult {
        val result = provider.currentLocation()
        if (result is DeviceLocationResult.Located) {
            repository.setLocation(
                ObserverLocation(coordinate = result.coordinate.coarse(), source = LocationSource.DEVICE),
            )
        }
        return result
    }
}

/** Saves a city, or raw coordinates when [name] is null, the user picked instead. */
class SetManualLocationUseCase @Inject constructor(
    private val repository: ObserverLocationRepository,
) {
    suspend operator fun invoke(coordinate: GeoCoordinate, name: String? = null) {
        repository.setLocation(
            ObserverLocation(coordinate = coordinate, name = name, source = LocationSource.MANUAL),
        )
    }

    suspend operator fun invoke(city: City) = invoke(city.coordinate, city.name)
}

/**
 * Forgets the saved place (Settings). Screens that need one ask again; the ISS reminder, computed
 * for a place, is re-armed, which leaves it waiting for a new one.
 */
class ClearObserverLocationUseCase @Inject constructor(
    private val repository: ObserverLocationRepository,
    private val rescheduleIssPassReminder: RescheduleIssPassReminderUseCase,
) {
    suspend operator fun invoke() {
        repository.clearLocation()
        rescheduleIssPassReminder()
    }
}

class SearchCitiesUseCase @Inject constructor(
    private val directory: CityDirectory,
) {
    suspend operator fun invoke(query: String): List<City> =
        if (query.isBlank()) emptyList() else directory.search(query.trim())
}

private fun GeoCoordinate.coarse(): GeoCoordinate =
    GeoCoordinate(latitude = latitude.roundTo2(), longitude = longitude.roundTo2())

@Suppress("MagicNumber")
private fun Double.roundTo2(): Double = kotlin.math.round(this * 100.0) / 100.0
