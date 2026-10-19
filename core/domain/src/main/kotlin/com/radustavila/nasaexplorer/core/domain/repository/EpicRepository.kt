package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Contract for DSCOVR/EPIC whole-Earth photos.
 *
 * Same shape as [ApodRepository]: reads are database-backed [Flow]s, network calls only write into
 * the cache and report how it went.
 */
interface EpicRepository {

    /** Photos of the newest cached day, in capture order. Empty while nothing is cached. */
    fun observeLatestDay(collection: EpicCollection): Flow<List<EpicImage>>

    /** Photos of one day, in capture order. Empty while that day is not cached. */
    fun observeDay(collection: EpicCollection, date: LocalDate): Flow<List<EpicImage>>

    /** Every day EPIC has published photos for, newest first, as last fetched from the archive. */
    fun observeAvailableDates(collection: EpicCollection): Flow<List<LocalDate>>

    /**
     * Fetches the newest published day and caches it, dropping cached days that fell out of the
     * retention window. Also refreshes the list of available dates when the newest day is missing
     * from it, which is the only time that list can have changed.
     *
     * @return the number of photos cached for the newest day.
     */
    suspend fun refreshLatest(collection: EpicCollection): DataResult<Int>

    /**
     * Fetches one specific day, for a date picked outside the cached window.
     *
     * @return the number of photos cached for that day.
     */
    suspend fun fetchDay(collection: EpicCollection, date: LocalDate): DataResult<Int>

    /**
     * Downloads the small rotation images of the newest cached day ahead of time, so the rotation
     * plays offline even if it was never opened online. Best effort: failures are not reported.
     */
    suspend fun prefetchLatestThumbnails(collection: EpicCollection)
}
