package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The galaxies, clusters and nebulae bundled with the app (F23): no network, read once. */
interface DeepSkyCatalogRepository {

    /** Brightest first; later calls return the same list. */
    suspend fun objects(): List<DeepSkyObject>
}

/**
 * The user's observing log: one "seen" mark per object, in the favourites database so backups keep
 * it. No cache cleaner or evictor may touch it.
 */
interface ObservingLogRepository {

    /** Every mark, newest observation first. */
    fun observeEntries(): Flow<List<ObservingLogEntry>>

    /** Adds the mark, or replaces the object's earlier one. */
    suspend fun save(entry: ObservingLogEntry)

    suspend fun remove(objectId: String)
}

/** Where a fixed J2000 direction is in an observer's sky, computed on the device. */
interface SkyPositionCalculator {

    /** Geometric altitude and azimuth, without refraction. */
    fun horizontal(position: EquatorialCoordinate, observer: GeoCoordinate, at: Instant): HorizontalCoordinate
}
