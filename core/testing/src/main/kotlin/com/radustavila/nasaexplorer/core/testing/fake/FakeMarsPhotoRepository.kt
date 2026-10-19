package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.MarsPhotoRepository
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [MarsPhotoRepository]: the cache is a state the test seeds; [refresh] is scripted. */
class FakeMarsPhotoRepository(initialPhotos: List<MarsPhoto> = emptyList()) : MarsPhotoRepository {

    private val photos = MutableStateFlow(initialPhotos)

    /** Photos [refresh] adds on success. */
    var refreshedPhotos: List<MarsPhoto> = emptyList()
    var refreshResult: DataResult<Int>? = null
    var refreshCount: Int = 0
        private set

    fun setPhotos(value: List<MarsPhoto>) {
        photos.value = value
    }

    override fun observeRoverPhotos(rover: MarsRover): Flow<List<MarsPhoto>> =
        photos.map { all -> all.filter { it.rover == rover }.sortedWith(NEWEST_SOL_FIRST) }

    override fun observeLatest(perRover: Int): Flow<List<MarsPhoto>> = photos.map { all ->
        all.groupBy(MarsPhoto::rover).values
            .flatMap { it.sortedWith(NEWEST_SOL_FIRST).take(perRover) }
            .sortedByDescending { it.takenAt }
    }

    override fun observePhoto(id: String): Flow<MarsPhoto?> = photos.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun refresh(): DataResult<Int> {
        refreshCount++
        val result = refreshResult ?: DataResult.Success(refreshedPhotos.size)
        if (result is DataResult.Success) {
            photos.value = (photos.value + refreshedPhotos).distinctBy(MarsPhoto::id)
        }
        return result
    }

    private companion object {
        val NEWEST_SOL_FIRST: Comparator<MarsPhoto> =
            compareByDescending<MarsPhoto> { it.sol }.thenByDescending { it.takenAt }
    }
}
