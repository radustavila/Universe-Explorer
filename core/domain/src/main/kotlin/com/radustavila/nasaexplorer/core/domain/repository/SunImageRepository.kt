package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.model.SunImage
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The Sun now, from NASA's Solar Dynamics Observatory, and the Sun at a past moment, from
 * Helioviewer. The newest picture of each channel is kept on the device, so the last one shows
 * offline; the 48-hour videos and past pictures are streamed and never stored here.
 */
interface SunImageRepository {

    /** The channel the user picked last; visible light until they pick one. */
    val selectedChannel: Flow<SunChannel>

    suspend fun selectChannel(channel: SunChannel)

    /** The newest downloaded picture of [channel], or null before the first download. */
    fun observeLatestImage(channel: SunChannel): Flow<SunImage?>

    /** Downloads [channel]'s newest picture unless the one on the device is already it. */
    suspend fun refreshLatestImage(channel: SunChannel): DataResult<Unit>

    /** The last 48 hours of [channel] as a looping video (several megabytes). */
    fun videoUrl(channel: SunChannel): String

    /** The Sun in the flare channel at the picture nearest [time]. */
    fun imageUrlAt(time: Instant): String
}
