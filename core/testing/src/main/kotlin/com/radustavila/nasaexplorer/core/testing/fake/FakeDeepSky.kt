package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.sky.DeepSkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.ObservingLogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyPositionCalculator
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeDeepSkyCatalogRepository(var objects: List<DeepSkyObject> = TestDeepSky.objects()) :
    DeepSkyCatalogRepository {
    override suspend fun objects(): List<DeepSkyObject> = objects
}

/** An in-memory observing log, newest observation first like Room's. */
class FakeObservingLogRepository(initial: List<ObservingLogEntry> = emptyList()) : ObservingLogRepository {

    val entries = MutableStateFlow(initial)

    override fun observeEntries(): Flow<List<ObservingLogEntry>> =
        entries.map { list -> list.sortedWith(NEWEST_FIRST) }

    private companion object {
        val NEWEST_FIRST = compareByDescending<ObservingLogEntry> { it.observedOn }.thenBy { it.objectId }
    }

    override suspend fun save(entry: ObservingLogEntry) {
        entries.update { list -> list.filterNot { it.objectId == entry.objectId } + entry }
    }

    override suspend fun remove(objectId: String) {
        entries.update { list -> list.filterNot { it.objectId == objectId } }
    }
}

/**
 * Altitude from [altitude] for each direction and instant; by default every object is 45 degrees up
 * in the south.
 */
class FakeSkyPositionCalculator(
    var altitude: (position: EquatorialCoordinate, at: Instant) -> Double = { _, _ -> DEFAULT_ALTITUDE },
) : SkyPositionCalculator {
    override fun horizontal(position: EquatorialCoordinate, observer: GeoCoordinate, at: Instant) =
        HorizontalCoordinate(altitude(position, at), SOUTH)

    private companion object {
        const val DEFAULT_ALTITUDE = 45.0
        const val SOUTH = 180.0
    }
}
