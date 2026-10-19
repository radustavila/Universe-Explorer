package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [EpicRepository]: reads are flows over what the test stored, and a successful
 * [fetchDay] stores [fetchDayImages] the way a download would fill the cache.
 */
class FakeEpicRepository : EpicRepository {

    private val images = MutableStateFlow<List<EpicImage>>(emptyList())
    private val availableDates = MutableStateFlow<Map<EpicCollection, List<LocalDate>>>(emptyMap())

    val refreshResults: MutableMap<EpicCollection, DataResult<Int>> = mutableMapOf()
    var fetchDayResult: DataResult<Int> = DataResult.Success(0)

    /** Stored by a successful [fetchDay], standing in for the downloaded day. */
    var fetchDayImages: List<EpicImage> = emptyList()

    val refreshedCollections: MutableList<EpicCollection> = mutableListOf()
    val fetchedDays: MutableList<Pair<EpicCollection, LocalDate>> = mutableListOf()
    val prefetchedCollections: MutableList<EpicCollection> = mutableListOf()

    fun setImages(list: List<EpicImage>) {
        images.value = list
    }

    fun setAvailableDates(collection: EpicCollection, dates: List<LocalDate>) {
        availableDates.update { it + (collection to dates.sortedDescending()) }
    }

    override fun observeLatestDay(collection: EpicCollection): Flow<List<EpicImage>> =
        images.map { list ->
            val ofCollection = list.filter { it.collection == collection }
            val newest = ofCollection.maxOfOrNull(EpicImage::date)
            ofCollection.filter { it.date == newest }.sortedBy(EpicImage::capturedAt)
        }

    override fun observeDay(collection: EpicCollection, date: LocalDate): Flow<List<EpicImage>> =
        images.map { list ->
            list.filter { it.collection == collection && it.date == date }.sortedBy(EpicImage::capturedAt)
        }

    override fun observeAvailableDates(collection: EpicCollection): Flow<List<LocalDate>> =
        availableDates.map { it[collection].orEmpty() }

    override suspend fun refreshLatest(collection: EpicCollection): DataResult<Int> {
        refreshedCollections += collection
        return refreshResults[collection] ?: DataResult.Success(0)
    }

    override suspend fun fetchDay(collection: EpicCollection, date: LocalDate): DataResult<Int> {
        fetchedDays += collection to date
        val result = fetchDayResult
        if (result is DataResult.Success) images.update { it + fetchDayImages }
        return result
    }

    override suspend fun prefetchLatestThumbnails(collection: EpicCollection) {
        prefetchedCollections += collection
    }
}
