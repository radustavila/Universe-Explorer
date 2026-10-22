package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.SunImageRepository
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.model.SunImage
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSunImageRepository(initialChannel: SunChannel = SunChannel.VISIBLE_LIGHT) : SunImageRepository {

    val channel = MutableStateFlow(initialChannel)
    val images = MutableStateFlow<Map<SunChannel, SunImage>>(emptyMap())

    /** Channels asked for, in order. */
    val refreshes = mutableListOf<SunChannel>()

    /** What [refreshLatestImage] answers; on success it stores [nextImage] when set. */
    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var nextImage: SunImage? = null

    override val selectedChannel: Flow<SunChannel> = channel

    override suspend fun selectChannel(channel: SunChannel) {
        this.channel.value = channel
    }

    override fun observeLatestImage(channel: SunChannel): Flow<SunImage?> = images.map { it[channel] }

    override suspend fun refreshLatestImage(channel: SunChannel): DataResult<Unit> {
        refreshes += channel
        val image = nextImage
        if (refreshResult is DataResult.Success && image != null) images.update { it + (image.channel to image) }
        return refreshResult
    }

    override fun videoUrl(channel: SunChannel): String = "https://sdo.test/video/${channel.name}.mp4"

    override fun imageUrlAt(time: Instant): String = "https://helioviewer.test/at/${time.epochSecond}.png"

    companion object {
        fun image(channel: SunChannel, updatedAt: Instant): SunImage = SunImage(
            channel = channel,
            localUrl = "file:///sun/${channel.name}_${updatedAt.epochSecond}.jpg",
            fullSizeUrl = "https://sdo.test/full/${channel.name}.jpg?v=${updatedAt.epochSecond}",
            updatedAt = updatedAt,
        )
    }
}
