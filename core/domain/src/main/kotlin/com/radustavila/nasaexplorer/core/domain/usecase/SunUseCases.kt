package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.SunImageRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.model.SunImage
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The chosen channel with its newest picture on the device and its 48-hour video. */
data class SunNow(
    val channel: SunChannel,
    val image: SunImage?,
    val videoUrl: String,
)

class ObserveSunUseCase @Inject constructor(
    private val repository: SunImageRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SunNow> = repository.selectedChannel.flatMapLatest { channel ->
        repository.observeLatestImage(channel).map { image ->
            SunNow(channel = channel, image = image, videoUrl = repository.videoUrl(channel))
        }
    }

    /** The channel picked now, for one-off reads. */
    suspend fun selectedChannel(): SunChannel = repository.selectedChannel.first()
}

class SelectSunChannelUseCase @Inject constructor(
    private val repository: SunImageRepository,
) {
    suspend operator fun invoke(channel: SunChannel) = repository.selectChannel(channel)
}

/**
 * Downloads a channel's newest picture through [RefreshSourceUseCase] as [SyncSource.SDO].
 *
 * SDO publishes a picture per channel about every [SDO_CADENCE], so while the one on the device is
 * younger than that nothing is asked. Otherwise the repository asks whether the server has a newer
 * one (a few hundred bytes when it does not). The source has no interval of its own: channels are
 * fetched one at a time as the user picks them, and only a rate-limit cooldown holds them all back.
 */
class RefreshSunImageUseCase @Inject constructor(
    private val repository: SunImageRepository,
    private val refreshSource: RefreshSourceUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(channel: SunChannel, force: Boolean = false): SourceRefreshResult<Unit> {
        if (!force) {
            val nextExpected = repository.observeLatestImage(channel).first()?.updatedAt?.plus(SDO_CADENCE)
            if (nextExpected != null && timeProvider.now().isBefore(nextExpected)) {
                return SourceRefreshResult.Skipped(nextExpected)
            }
        }
        return refreshSource(SyncSource.SDO, force = force) { repository.refreshLatestImage(channel) }
    }

    companion object {
        val SDO_CADENCE: Duration = Duration.ofMinutes(15)
    }
}

/** Helioviewer's picture of the Sun nearest [time], e.g. a flare's peak. */
class GetSunImageAtUseCase @Inject constructor(
    private val repository: SunImageRepository,
) {
    operator fun invoke(time: Instant): String = repository.imageUrlAt(time)
}

/**
 * Whether a 48-hour video (3–10 MB) may start without asking: only on an unmetered network, so it
 * never downloads on mobile data without a tap.
 */
class CanAutoPlaySunVideoUseCase @Inject constructor(
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(): Boolean = networkMonitor.isUnmetered()
}
