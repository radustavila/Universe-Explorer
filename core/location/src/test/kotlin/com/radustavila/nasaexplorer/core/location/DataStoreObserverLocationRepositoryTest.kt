package com.radustavila.nasaexplorer.core.location

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreObserverLocationRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val repository by lazy {
        DataStoreObserverLocationRepository(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                File(folder.root, "observer_location.preferences_pb")
            },
        )
    }

    @Test
    fun `nothing is saved until the user picks a place`() = scope.runTest {
        assertNull(repository.location.first())
    }

    @Test
    fun `a city and then a device fix round-trip, the fix dropping the name`() = scope.runTest {
        val city = ObserverLocation(GeoCoordinate(44.43, 26.10), name = "Bucharest", source = LocationSource.MANUAL)
        repository.setLocation(city)
        assertEquals(city, repository.location.first())

        val device = ObserverLocation(GeoCoordinate(-33.87, 151.21), source = LocationSource.DEVICE)
        repository.setLocation(device)
        assertEquals(device, repository.location.first())
    }

    @Test
    fun `clearing forgets the location`() = scope.runTest {
        repository.setLocation(ObserverLocation(GeoCoordinate(1.0, 2.0), source = LocationSource.MANUAL))

        repository.clearLocation()

        assertNull(repository.location.first())
    }
}
