package com.radustavila.nasaexplorer.feature.mars.photos

import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import java.time.Instant

data class MarsPhotosUiState(
    val rover: MarsRover = MarsRover.CURIOSITY,
    /** Newest sol first. */
    val sols: List<SolGroup> = emptyList(),
    val isSyncing: Boolean = false,
    val lastAttemptFailed: Boolean = false,
    val isOffline: Boolean = false,
    /** False until the cache has been read once. */
    val isLoaded: Boolean = false,
    /** When the rover photos last synced. */
    val lastUpdatedAt: Instant? = null,
) {
    val freshness: DataFreshness
        get() = DataFreshness(lastUpdatedAt = lastUpdatedAt, isOffline = isOffline, lastRefreshFailed = lastAttemptFailed)
}

/** One sol's photos, by camera. */
data class SolGroup(
    val sol: Int,
    /** When the sol's last photo was taken, for its date. */
    val takenAt: Instant?,
    val cameras: List<CameraGroup>,
)

data class CameraGroup(
    val cameraName: String,
    val photos: List<MarsPhoto>,
)

/** Groups photos sorted newest sol first by sol, then by camera in order of first appearance. */
internal fun List<MarsPhoto>.groupBySolAndCamera(): List<SolGroup> =
    groupBy(MarsPhoto::sol).map { (sol, photos) ->
        SolGroup(
            sol = sol,
            takenAt = photos.mapNotNull(MarsPhoto::takenAt).maxOrNull(),
            cameras = photos.groupBy(MarsPhoto::cameraName).map { (name, shots) -> CameraGroup(name, shots) },
        )
    }
